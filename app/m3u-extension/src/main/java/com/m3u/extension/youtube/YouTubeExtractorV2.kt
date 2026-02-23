package com.m3u.extension.youtube

import android.content.Context
import android.util.Log
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * YouTubeExtractorV2
 *
 * Versão integrada com YouTubeWebViewTokenManager.
 *
 * A cada extração:
 * 1. WebView oculto navega para youtube.com
 * 2. JavaScript extrai: cookies, visitor_data, PO Token, UA real
 * 3. Todos os tokens são passados para o Python (extractor_v4.py)
 * 4. yt-dlp usa os mesmos tokens que o YouTube espera → sem 403
 *
 * Os tokens ficam em cache por 45 min (duração típica de validade do PO Token).
 */
class YouTubeExtractorV2(private val context: Context) {

    companion object {
        private const val TAG = "YouTubeExtractorV2"
        private const val STREAM_CACHE_TTL_MS = 5 * 60 * 60 * 1000L  // 5h (streams expiram em 6h)
    }

    private val python: Python by lazy { Python.getInstance() }
    private val tokenManager by lazy { YouTubeWebViewTokenManager(context) }
    private val cacheDir: File by lazy { File(context.cacheDir, "yt_streams_v3").apply { mkdirs() } }

    data class ExtractionResult(
        val success: Boolean,
        val m3u8Url: String?,
        val headers: Map<String, String>,
        val method: String?,
        val error: String?
    )

    // ──────────────────────────────────────────────────────────────
    // MÉTODO PRINCIPAL
    // ──────────────────────────────────────────────────────────────

    /**
     * Extrai stream M3U8 de um canal YouTube.
     *
     * @param name Nome do canal
     * @param url URL do YouTube (canal, vídeo ao vivo, etc.)
     * @param forceRefresh Se true, ignora cache de tokens e de stream
     */
    suspend fun extractChannel(
        name: String,
        url: String,
        logo: String? = null,
        group: String? = null,
        forceRefresh: Boolean = false
    ): ExtractionResult = withContext(Dispatchers.IO) {

        Log.d(TAG, "\n════════════════════════════════")
        Log.d(TAG, "Extraindo: $name")
        Log.d(TAG, "URL: $url")

        // 1. Verificar cache de stream
        if (!forceRefresh) {
            getCachedStream(url)?.let {
                Log.d(TAG, "✓ Stream em cache válido")
                return@withContext it
            }
        }

        // 2. Capturar tokens via WebView (ou usar cache de tokens)
        Log.d(TAG, "Capturando tokens via WebView...")
        val tokens = withContext(Dispatchers.Main) {
            tokenManager.fetchTokens(forceRefresh)
        }

        Log.d(TAG, "Tokens obtidos:")
        Log.d(TAG, "  UA: ${tokens.userAgent.take(60)}...")
        Log.d(TAG, "  Cookies: ${if (tokens.hasCookies) "✓ ${tokens.cookies.length} chars" else "✗"}")
        Log.d(TAG, "  visitorData: ${if (tokens.hasVisitorData) "✓" else "✗"}")
        Log.d(TAG, "  poToken: ${if (tokens.hasPoToken) "✓" else "✗"}")

        // 3. Extrair via Python com todos os tokens
        val result = extractWithPython(
            name = name,
            url = url,
            logo = logo,
            group = group,
            tokens = tokens
        )

        // 4. Cachear resultado bem-sucedido
        if (result.success) {
            cacheStream(url, result)
        }

        result
    }

    // ──────────────────────────────────────────────────────────────
    // EXTRAÇÃO PYTHON
    // ──────────────────────────────────────────────────────────────

    private fun extractWithPython(
        name: String,
        url: String,
        logo: String?,
        group: String?,
        tokens: YouTubeWebViewTokenManager.YouTubeTokens
    ): ExtractionResult {

        val inputFile = File(cacheDir, "input_${System.currentTimeMillis()}.json")
        val outputFile = File(cacheDir, "output_${System.currentTimeMillis()}.json")
        val cookiesFile = File(cacheDir, "cookies_${System.currentTimeMillis()}.txt")

        try {
            // Montar JSON de entrada para o Python
            val inputJson = JSONObject().apply {
                put("channels", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("name", name)
                        put("url", url)
                        put("logo", logo ?: "")
                        put("group", group ?: "YouTube")
                    })
                })
                // Passar todos os tokens ao Python
                put("tokens", JSONObject().apply {
                    put("userAgent", tokens.userAgent)
                    put("visitorData", tokens.visitorData)
                    put("visitorInfoLive", tokens.visitorInfoLive)
                    put("poToken", tokens.poToken)
                    put("clientVersion", tokens.clientVersion)
                    put("apiKey", tokens.apiKey)
                    put("hl", tokens.hl)
                    put("gl", tokens.gl)
                })
            }
            inputFile.writeText(inputJson.toString())

            // Salvar cookies em arquivo separado (formato Netscape para o yt-dlp)
            if (tokens.hasCookies) {
                cookiesFile.writeText(buildNetscapeCookies(tokens.cookies))
            }

            // Chamar extrator Python
            val module = python.getModule("extractor_v4")
            module.callAttr(
                "extract",
                inputFile.absolutePath,
                outputFile.absolutePath,
                if (tokens.hasCookies) cookiesFile.absolutePath else null,
                tokens.userAgent
            )

            // Ler resultado
            if (!outputFile.exists()) {
                return ExtractionResult(false, null, emptyMap(), null, "Python não gerou saída")
            }

            val outputJson = JSONObject(outputFile.readText())
            val channels = outputJson.getJSONArray("channels")

            if (channels.length() == 0) {
                return ExtractionResult(false, null, emptyMap(), null, "Sem resultados")
            }

            val ch = channels.getJSONObject(0)
            if (!ch.getBoolean("success")) {
                return ExtractionResult(
                    success = false,
                    m3u8Url = null,
                    headers = emptyMap(),
                    method = null,
                    error = ch.optString("error", "Falha desconhecida")
                )
            }

            // Montar headers para o player
            val headers = mutableMapOf<String, String>()
            ch.optJSONObject("headers")?.let { h ->
                h.keys().forEach { key -> headers[key] = h.getString(key) }
            }

            // Garantir headers críticos
            if (!headers.containsKey("User-Agent")) headers["User-Agent"] = tokens.userAgent
            if (!headers.containsKey("Referer"))    headers["Referer"] = "https://www.youtube.com/"
            if (!headers.containsKey("Origin"))     headers["Origin"] = "https://www.youtube.com"
            if (tokens.hasCookies && !headers.containsKey("Cookie")) {
                headers["Cookie"] = tokens.cookies
            }

            val m3u8 = ch.getString("m3u8")
            val method = ch.optString("extraction_method", "unknown")

            Log.d(TAG, "✅ Extração bem-sucedida!")
            Log.d(TAG, "   Método: $method")
            Log.d(TAG, "   M3U8: ${m3u8.take(60)}...")

            // Validar stream antes de retornar
            val valid = validateStream(m3u8, headers)
            if (!valid) {
                Log.w(TAG, "⚠ Stream retornou erro na validação — tokens podem estar desatualizados")
                // Forçar refresh dos tokens e tentar uma vez mais
                return retryWithFreshTokens(name, url, logo, group)
            }

            return ExtractionResult(
                success = true,
                m3u8Url = m3u8,
                headers = headers,
                method = method,
                error = null
            )

        } catch (e: Exception) {
            Log.e(TAG, "Erro Python: ${e.message}", e)
            return ExtractionResult(false, null, emptyMap(), null, e.message)
        } finally {
            inputFile.delete()
            outputFile.delete()
            cookiesFile.delete()
        }
    }

    /**
     * Segunda tentativa com tokens forçados (ignora cache).
     * Chamada automaticamente quando a validação falha.
     */
    private suspend fun retryWithFreshTokens(
        name: String, url: String, logo: String?, group: String?
    ): ExtractionResult = withContext(Dispatchers.Main) {
        Log.d(TAG, "↩ Recapturando tokens frescos e tentando novamente...")
        tokenManager.clearCache()
        val freshTokens = tokenManager.fetchTokens(forceRefresh = true)
        withContext(Dispatchers.IO) {
            extractWithPython(name, url, logo, group, freshTokens)
        }
    }

    // ──────────────────────────────────────────────────────────────
    // VALIDAÇÃO
    // ──────────────────────────────────────────────────────────────

    private fun validateStream(url: String, headers: Map<String, String>): Boolean {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()

            val req = Request.Builder().url(url).head()
            headers.forEach { (k, v) ->
                try { req.header(k, v) } catch (_: Exception) {}
            }

            val resp = client.newCall(req.build()).execute()
            val code = resp.code
            resp.close()

            Log.d(TAG, "Validação HTTP $code")
            code in 200..399 || code == 206
        } catch (e: Exception) {
            Log.w(TAG, "Erro na validação: ${e.message}")
            // Aceitar URLs do googlevideo mesmo sem validação
            url.contains("googlevideo.com") || url.contains(".m3u8")
        }
    }

    // ──────────────────────────────────────────────────────────────
    // CACHE DE STREAM
    // ──────────────────────────────────────────────────────────────

    private fun cacheStream(url: String, result: ExtractionResult) {
        val file = File(cacheDir, "stream_${url.hashCode()}.json")
        try {
            file.writeText(JSONObject().apply {
                put("success", result.success)
                put("m3u8Url", result.m3u8Url ?: "")
                put("headers", JSONObject(result.headers as Map<*, *>))
                put("method", result.method ?: "")
                put("error", result.error ?: "")
                put("timestamp", System.currentTimeMillis())
            }.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao salvar cache de stream: ${e.message}")
        }
    }

    private fun getCachedStream(url: String): ExtractionResult? {
        val file = File(cacheDir, "stream_${url.hashCode()}.json")
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            val age = System.currentTimeMillis() - json.getLong("timestamp")
            if (age > STREAM_CACHE_TTL_MS) return null

            val headers = mutableMapOf<String, String>()
            json.optJSONObject("headers")?.let { h ->
                h.keys().forEach { k -> headers[k] = h.getString(k) }
            }

            ExtractionResult(
                success = json.getBoolean("success"),
                m3u8Url = json.optString("m3u8Url").takeIf { it.isNotEmpty() },
                headers = headers,
                method = json.optString("method"),
                error = json.optString("error").takeIf { it.isNotEmpty() }
            )
        } catch (e: Exception) {
            null
        }
    }

    fun clearAllCache() {
        tokenManager.clearCache()
        cacheDir.listFiles()?.forEach { it.delete() }
        Log.d(TAG, "Cache completo limpo")
    }

    // ──────────────────────────────────────────────────────────────
    // HELPERS
    // ──────────────────────────────────────────────────────────────

    /**
     * Converte string de cookies do CookieManager para formato Netscape
     * que o yt-dlp consegue ler via --cookies.
     *
     * Formato: domain\tTRUE\tpath\tSECURE\texpiry\tname\tvalue
     */
    private fun buildNetscapeCookies(rawCookies: String): String {
        val sb = StringBuilder("# Netscape HTTP Cookie File\n")
        sb.appendLine("# Gerado automaticamente pelo YouTubeExtractorV2")
        sb.appendLine()

        rawCookies.split(";").forEach { pair ->
            val trimmed = pair.trim()
            val eqIdx = trimmed.indexOf('=')
            if (eqIdx < 0) return@forEach

            val name = trimmed.substring(0, eqIdx).trim()
            val value = trimmed.substring(eqIdx + 1).trim()

            // domain  include-subdomains  path  secure  expiry  name  value
            sb.appendLine(".youtube.com\tTRUE\t/\tTRUE\t2147483647\t$name\t$value")
        }

        return sb.toString()
    }
}