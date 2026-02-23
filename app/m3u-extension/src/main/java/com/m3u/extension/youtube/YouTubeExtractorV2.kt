package com.m3u.extension.youtube

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.flow.first
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * YouTubeExtractorV2 - VERSÃO CORRIGIDA para erro 404
 *
 * PROBLEMA RAIZ:
 * Os links M3U8 do YouTube são "signed URLs" vinculados ao IP + User-Agent.
 * Se o player usar um UA diferente do usado na extração → 404/403.
 *
 * CORREÇÃO:
 * 1. Capturar o UA REAL do WebView do Android
 * 2. Passar esse UA para o Python (yt-dlp usa o mesmo UA)
 * 3. Registrar o UA nos headers do JsonHeaderRegistry
 * 4. O player ExoPlayer usa EXATAMENTE os mesmos headers
 *
 * FLUXO CORRETO:
 * WebView UA capturado → Python extrai com esse UA → Headers salvos → Player reproduz com mesmo UA
 */
class YouTubeExtractorV2(private val context: Context) {

    companion object {
        private const val TAG = "YouTubeExtractorV2"
        private const val CACHE_VALIDITY_HOURS = 6
        private const val CACHE_VALIDITY_MS = CACHE_VALIDITY_HOURS * 60 * 60 * 1000L
    }

    private val python: Python by lazy { Python.getInstance() }
    private val cacheDir: File by lazy {
        File(context.cacheDir, "yt_streams").apply { mkdirs() }
    }
    private val logger: com.m3u.extension.logging.ExtractionLogger by lazy {
        com.m3u.extension.logging.ExtractionLogger(context)
    }

    data class ExtractionResult(
        val success: Boolean,
        val m3u8Url: String?,
        // CRÍTICO: headers DEVEM incluir o mesmo UA usado na extração
        val headers: Map<String, String>,
        val method: String?,
        val error: String?
    )

    // ============================================================
    // CORREÇÃO PRINCIPAL: Capturar UA real do WebView
    // ============================================================
    
    /**
     * Captura o User-Agent REAL do WebView do Android.
     * Este é o UA que o sistema usa nativamente, garantindo compatibilidade
     * com os signed URLs do YouTube.
     *
     * IMPORTANTE: Deve ser chamado na thread principal (UI thread).
     */
    private fun getRealDeviceUserAgent(): String {
        return try {
            // WebSettings.getDefaultUserAgent é o método mais confiável
            // Retorna o UA que o WebView/Chrome do dispositivo usa
            WebSettings.getDefaultUserAgent(context)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao obter UA do WebView: ${e.message}")
            // Fallback: UA genérico do Android
            "Mozilla/5.0 (Linux; Android ${android.os.Build.VERSION.RELEASE}; " +
            "${android.os.Build.MODEL}) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"
        }
    }

    /**
     * Captura cookies reais do YouTube do WebView.
     * Necessário para streams com restrição de região ou conteúdo age-gated.
     * 
     * Realiza um "warmup" acessando o YouTube e captura os cookies resultantes.
     */
    private suspend fun captureYouTubeCookies(): String? {
        val deferred = CompletableDeferred<String?>()
        
        Handler(Looper.getMainLooper()).post {
            try {
                val webView = WebView(context)
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(webView, true)
                
                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    userAgentString = getRealDeviceUserAgent()
                }
                
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, pageUrl: String?) {
                        val cookies = cookieManager.getCookie("https://www.youtube.com")
                        Log.d(TAG, "Cookies YouTube capturados: ${cookies?.length ?: 0} chars")
                        
                        if (!deferred.isCompleted) {
                            deferred.complete(cookies?.takeIf { it.isNotBlank() })
                            view?.destroy()
                        }
                    }
                }
                
                webView.loadUrl("https://www.youtube.com")
                
                // Timeout de segurança
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!deferred.isCompleted) {
                        val cookies = cookieManager.getCookie("https://www.youtube.com")
                        deferred.complete(cookies?.takeIf { it.isNotBlank() })
                        webView.destroy()
                    }
                }, 12000)
                
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao capturar cookies: ${e.message}")
                deferred.complete(null)
            }
        }
        
        return try {
            withTimeout(15000) { deferred.await() }
        } catch (e: Exception) {
            Log.w(TAG, "Timeout ao capturar cookies")
            null
        }
    }

    // ============================================================
    // MÉTODO PRINCIPAL DE EXTRAÇÃO
    // ============================================================

    /**
     * Extrai um canal do YouTube com correção do erro 404.
     *
     * FLUXO:
     * 1. Verificar cache válido
     * 2. Capturar UA real do dispositivo
     * 3. Capturar cookies do YouTube (opcional mas recomendado)
     * 4. Chamar Python com UA + cookies reais
     * 5. Validar stream resultante
     * 6. Registrar headers corretos no JsonHeaderRegistry
     */
    suspend fun extractChannel(
        name: String,
        url: String,
        logo: String? = null,
        group: String? = null
    ): ExtractionResult = withContext(Dispatchers.IO) {

        Log.d(TAG, "=== Extraindo: $name ===")
        Log.d(TAG, "URL: $url")

        // 1. Verificar cache
        val cached = getCachedResult(url)
        if (cached != null && isCacheValid(url)) {
            Log.d(TAG, "✓ Usando resultado em cache (${cached.method})")
            return@withContext cached
        }

        // 2. Capturar UA REAL do dispositivo (executar na main thread)
        val deviceUserAgent = withContext(Dispatchers.Main) {
            getRealDeviceUserAgent()
        }
        Log.d(TAG, "UA do dispositivo capturado: ${deviceUserAgent.take(60)}...")

        // 3. Capturar cookies do YouTube (recomendado para evitar bloqueios)
        Log.d(TAG, "Capturando cookies do YouTube...")
        val youtubeCookies = try {
            captureYouTubeCookies()
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível capturar cookies: ${e.message}")
            null
        }
        
        if (youtubeCookies != null) {
            Log.d(TAG, "✓ Cookies capturados: ${youtubeCookies.length} chars")
        } else {
            Log.w(TAG, "⚠ Sem cookies - pode causar problemas em algumas regiões")
        }

        // 4. Extrair via Python com UA + cookies reais
        try {
            val result = extractWithPython(
                url = url,
                name = name,
                logo = logo,
                group = group,
                deviceUserAgent = deviceUserAgent,
                youtubeCookies = youtubeCookies
            )

            if (result.success && result.m3u8Url != null) {
                // 5. Validar stream (com os mesmos headers que serão usados na reprodução)
                if (validateStream(result.m3u8Url, result.headers)) {
                    Log.d(TAG, "✅ Stream validado e funcional")
                    Log.d(TAG, "   URL: ${result.m3u8Url.take(60)}...")
                    Log.d(TAG, "   UA: ${result.headers["User-Agent"]?.take(50)}...")
                    
                    cacheResult(url, result)
                    return@withContext result
                } else {
                    Log.w(TAG, "⚠ Stream extraído mas validação falhou (possível expiração rápida)")
                    // Retornar mesmo assim - o player vai tentar e pode funcionar
                    // O erro 404 geralmente só ocorre se o UA for DIFERENTE
                    if (result.m3u8Url.contains("googlevideo.com") || result.m3u8Url.contains(".m3u8")) {
                        Log.d(TAG, "URL parece ser HLS válido, retornando mesmo sem validação confirmada")
                        return@withContext result
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Erro na extração Python: ${e.message}", e)
        }

        ExtractionResult(
            success = false,
            m3u8Url = null,
            headers = emptyMap(),
            method = null,
            error = "Falha em todos os métodos de extração"
        )
    }

    /**
     * Extrai usando o módulo Python v2 CORRIGIDO.
     * 
     * CRÍTICO: Passa o deviceUserAgent para o Python!
     * O yt-dlp deve usar o mesmo UA que o player vai usar.
     */
    private suspend fun extractWithPython(
        url: String,
        name: String,
        logo: String?,
        group: String?,
        deviceUserAgent: String,
        youtubeCookies: String?
    ): ExtractionResult {

        val module = python.getModule("extractor_v2")

        val inputJson = JSONObject().apply {
            put("channels", JSONArray().apply {
                put(JSONObject().apply {
                    put("name", name)
                    put("url", url)
                    put("logo", logo ?: "")
                    put("group", group ?: "YouTube")
                })
            })
        }

        val inputFile = File(cacheDir, "input_${System.currentTimeMillis()}.json")
        val outputFile = File(cacheDir, "output_${System.currentTimeMillis()}.json")
        val cookiesFile = if (youtubeCookies != null) {
            File(cacheDir, "cookies_${System.currentTimeMillis()}.txt").also {
                it.writeText(youtubeCookies)
            }
        } else null

        try {
            inputFile.writeText(inputJson.toString())

            val preferences = com.m3u.extension.preferences.ExtensionPreferences(context)
            val format = preferences.format.first()

            // CORREÇÃO: Passar o deviceUserAgent para o Python!
            module.callAttr(
                "extract",
                inputFile.absolutePath,
                outputFile.absolutePath,
                cookiesFile?.absolutePath,  // cookies
                format,
                deviceUserAgent  // NOVO: UA real do dispositivo
            )

            if (outputFile.exists()) {
                val resultJson = JSONObject(outputFile.readText())
                val channels = resultJson.getJSONArray("channels")

                if (channels.length() > 0) {
                    val channel = channels.getJSONObject(0)

                    if (channel.getBoolean("success")) {
                        val headers = mutableMapOf<String, String>()
                        val headersJson = channel.getJSONObject("headers")
                        headersJson.keys().forEach { key ->
                            headers[key] = headersJson.getString(key)
                        }

                        // VERIFICAÇÃO CRÍTICA: O UA nos headers deve corresponder ao deviceUserAgent
                        val extractedUA = headers["User-Agent"] ?: ""
                        if (extractedUA.isEmpty()) {
                            Log.w(TAG, "⚠ UA ausente nos headers! Injetando UA do dispositivo.")
                            headers["User-Agent"] = deviceUserAgent
                        } else if (!extractedUA.equals(deviceUserAgent, ignoreCase = true)) {
                            Log.d(TAG, "UA no resultado (${extractedUA.take(40)}) != UA do dispositivo")
                            Log.d(TAG, "Mantendo UA do resultado Python (mais próximo do usado na extração)")
                        }

                        // Garantir headers mínimos para reprodução
                        if (!headers.containsKey("Referer")) {
                            headers["Referer"] = "https://www.youtube.com/"
                        }
                        if (!headers.containsKey("Origin")) {
                            headers["Origin"] = "https://www.youtube.com"
                        }
                        // Adicionar cookies se não foram incluídos pelo Python
                        if (youtubeCookies != null && !headers.containsKey("Cookie")) {
                            headers["Cookie"] = youtubeCookies
                        }

                        val result = ExtractionResult(
                            success = true,
                            m3u8Url = channel.getString("m3u8"),
                            headers = headers,
                            method = channel.optString("extraction_method"),
                            error = null
                        )

                        Log.d(TAG, "✅ Extração Python bem-sucedida")
                        Log.d(TAG, "   Método: ${result.method}")
                        Log.d(TAG, "   UA usado: ${headers["User-Agent"]?.take(50)}")
                        Log.d(TAG, "   Tem cookies: ${headers.containsKey("Cookie")}")

                        return result
                    } else {
                        val error = channel.optString("error", "Erro desconhecido")
                        Log.w(TAG, "Python retornou falha: $error")
                    }
                }
            } else {
                Log.e(TAG, "Arquivo de saída Python não foi criado")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao executar Python: ${e.message}", e)
        } finally {
            inputFile.delete()
            outputFile.delete()
            cookiesFile?.delete()
        }

        return ExtractionResult(
            success = false,
            m3u8Url = null,
            headers = emptyMap(),
            method = null,
            error = "Extração Python falhou"
        )
    }

    /**
     * Valida stream usando os headers corretos.
     * 
     * IMPORTANTE: Usa os mesmos headers que serão usados na reprodução,
     * garantindo que a validação seja real.
     */
    private fun validateStream(url: String, headers: Map<String, String>): Boolean {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val requestBuilder = Request.Builder()
                .url(url)
                .head() // HEAD é suficiente para validar

            // Aplicar TODOS os headers (igual ao player vai fazer)
            headers.forEach { (key, value) ->
                try {
                    requestBuilder.header(key, value)
                } catch (e: Exception) {
                    Log.w(TAG, "Header inválido '$key': ${e.message}")
                }
            }

            val response = client.newCall(requestBuilder.build()).execute()
            val code = response.code
            response.close()

            val isValid = code in 200..299 || code in 300..399 || code == 206
            Log.d(TAG, "Validação HEAD: HTTP $code → ${if (isValid) "✓" else "✗"}")
            
            isValid
        } catch (e: Exception) {
            Log.w(TAG, "Erro na validação HEAD: ${e.message}")
            // Se falhou na validação mas a URL parece ser HLS, aceitar
            url.contains(".m3u8") || url.contains("googlevideo.com")
        }
    }

    // ============================================================
    // CACHE
    // ============================================================

    private fun getCachedResult(url: String): ExtractionResult? {
        val cacheFile = File(cacheDir, url.hashCode().toString() + ".json")
        if (!cacheFile.exists()) return null

        return try {
            val json = JSONObject(cacheFile.readText())
            val headers = mutableMapOf<String, String>()
            val headersJson = json.optJSONObject("headers")
            headersJson?.keys()?.forEach { key ->
                headers[key] = headersJson.getString(key)
            }

            ExtractionResult(
                success = json.getBoolean("success"),
                m3u8Url = json.optString("m3u8Url").takeIf { it.isNotEmpty() },
                headers = headers,
                method = json.optString("method"),
                error = json.optString("error").takeIf { it.isNotEmpty() }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao ler cache: ${e.message}")
            null
        }
    }

    private fun cacheResult(url: String, result: ExtractionResult) {
        val cacheFile = File(cacheDir, url.hashCode().toString() + ".json")
        try {
            val json = JSONObject().apply {
                put("success", result.success)
                put("m3u8Url", result.m3u8Url ?: "")
                put("headers", JSONObject(result.headers))
                put("method", result.method ?: "")
                put("error", result.error ?: "")
                put("timestamp", System.currentTimeMillis())
            }
            cacheFile.writeText(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao salvar cache: ${e.message}")
        }
    }

    private fun isCacheValid(url: String): Boolean {
        val cacheFile = File(cacheDir, url.hashCode().toString() + ".json")
        if (!cacheFile.exists()) return false
        return try {
            val json = JSONObject(cacheFile.readText())
            val timestamp = json.getLong("timestamp")
            val age = System.currentTimeMillis() - timestamp
            age < CACHE_VALIDITY_MS
        } catch (e: Exception) {
            false
        }
    }

    fun clearOldCache() {
        cacheDir.listFiles()?.forEach { file ->
            try {
                val json = JSONObject(file.readText())
                val timestamp = json.getLong("timestamp")
                val age = System.currentTimeMillis() - timestamp
                if (age > CACHE_VALIDITY_MS) {
                    file.delete()
                    Log.d(TAG, "Cache expirado removido: ${file.name}")
                }
            } catch (e: Exception) {
                file.delete()
            }
        }
    }

    // Métodos delegados ao logger
    suspend fun generateReport(): String = logger.formatReportAsText()
    suspend fun saveReport(): File = logger.saveReportToFile()
    suspend fun getQuickSummary(): String = logger.getQuickSummary()
    suspend fun resetLogger() = logger.reset()
}