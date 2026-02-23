package com.m3u.extension

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.google.gson.Gson
import com.m3u.extension.preferences.ExtensionPreferences
import com.m3u.extension.logic.PlaylistProcessor
import com.m3u.extension.logic.YouTubeInteractor
import com.m3u.extension.logic.BrowserUtils
import com.m3u.extension.util.LogManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import com.m3u.core.extension.IExtension
import com.m3u.core.extension.IExtensionCallback
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * ExtensionService - Serviço de Extração de Links M3U8 de Alta Performance
 * 
 * Este serviço é responsável por:
 * 1. Receber requisições do app Universal via AIDL
 * 2. Processar arquivos channels.json
 * 3. Extrair links M3U8 usando o Motor Pro-Max (WebView + Sniffing)
 * 4. Fallback automático para yt-dlp (Chaquopy)
 * 
 * ARQUITETURA:
 * - ExtensionService: Orquestração AIDL
 * - YouTubeInteractor: Lógica de negócio e seleção de motor
 * - BrowserUtils: Motor Pro-Max (Standard de Ouro)
 * - YtDlpResolver: Motor de Fallback (Suporte a múltiplos sites)
 */
class ExtensionService : Service() {
    
    private lateinit var interactor: YouTubeInteractor
    private lateinit var preferences: ExtensionPreferences
    private val extractorV2 by lazy { com.m3u.extension.youtube.YouTubeExtractorV2(this) }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mutex = Mutex()

    override fun onCreate() {
        super.onCreate()
        preferences = ExtensionPreferences(this)
        interactor = YouTubeInteractor(applicationContext)
        
        // Limpar cache antigo do extrator v2
        scope.launch(Dispatchers.IO) {
            try {
                extractorV2.clearOldCache()
            } catch (e: Exception) {
                Log.w(TAG, "Erro ao limpar cache: ${e.message}")
            }
        }
    }

    private data class ChannelData(
        val name: String,
        val url: String,
        val logo: String? = null,
        val group: String? = null
    )
    
    private data class ChannelsData(
        val channels: List<ChannelData>
    )

    private suspend fun processJsonAndNotify(jsonContent: String, callback: IExtensionCallback?) {
        try {
            preferences.updateStatus("Sincronizando via App Universal...")
            LogManager.info("Iniciando sincronização de canais via App Universal")
            
            // Captura cookies e User-Agent do navegador antes do processamento
            captureAndStoreUserAgent()
            captureAndStoreBrowserCookies()
            
            val gson = Gson()
            val channelsData = try {
                LogManager.debug("Parseando conteúdo JSON (${jsonContent.length} bytes)")
                gson.fromJson(jsonContent, ChannelsData::class.java)
            } catch (e: Exception) {
                val errorMsg = "Erro ao parsear JSON: ${e.message}"
                Log.e(TAG, errorMsg)
                LogManager.error(errorMsg)
                try { callback?.onError(errorMsg) } catch (e: Exception) { Log.w(TAG, "Callback failed", e) }
                preferences.updateStatus("Erro: JSON inválido")
                return
            }

            val channels = channelsData?.channels ?: emptyList()
            if (channels.isEmpty()) {
                preferences.updateStatus("Erro: Nenhum canal encontrado")
                try { callback?.onError("Nenhum canal encontrado no JSON") } catch (e: Exception) { Log.w(TAG, "Callback failed", e) }
                return
            }

            val total = channels.size
            var completed = 0
            var successCount = 0
            var failCount = 0
            val successNames = mutableListOf<String>()
            val failNames = mutableListOf<String>()
            
            // Controle de concorrência global (pode ser ajustado)
            val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
            val seen = ConcurrentHashMap.newKeySet<String>()
            
            val results: List<Map<String, Any?>> = coroutineScope {
                channels.map { channel ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            val result = processChannel(channel, seen)
                            
                            mutex.withLock {
                                completed++
                                if (result["success"] == true) {
                                    successCount++
                                    result["name"]?.let { successNames.add(it as String) }
                                    LogManager.info("Canal processado: ${result["name"]}")
                                } else {
                                    failCount++
                                    result["name"]?.let { failNames.add(it as String) }
                                    LogManager.warn("Falha no canal: ${result["name"]} -> ${result["error"]}")
                                }
                                
                                try {
                                    val pct = ((completed.toFloat() / total) * 100).toInt()
                                    val statusMsg = "Padronizando: $pct% ($completed/$total) | ✅ $successCount | ❌ $failCount"
                                    preferences.updateStatus(statusMsg)
                                    
                                    // Notifica progresso a cada item
                                    try { callback?.onProgress(completed, total, result["name"] as? String ?: "Desconhecido") } catch (e: Exception) { Log.w(TAG, "Callback failed", e) }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Erro ao notificar progresso: ${e.message}")
                                }
                            }
                            result
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            
            // Gera também uma versão M3U completa para debug ou consumo direto
            val m3uBuilder = StringBuilder("#EXTM3U\n")
            results.filter { it["success"] == true }.forEach { res ->
                val name = res["name"] as? String ?: "Unknown"
                val url = res["m3u8"] as? String ?: ""
                val logo = res["logo"] as? String ?: ""
                val group = res["group"] as? String ?: ""
                @Suppress("UNCHECKED_CAST")
                val headers = res["headers"] as? Map<String, String>
                
                m3uBuilder.append(PlaylistProcessor.generateM3UEntry(
                    name = name,
                    url = url,
                    logo = logo,
                    group = group,
                    headers = headers
                )).append("\n")
            }
            
            val finalJson = gson.toJson(mapOf(
                "channels" to results,
                "m3u_standard" to m3uBuilder.toString()
            ))
            
            // Log do JSON final para debug
            Log.d(TAG, "JSON final gerado com ${results.size} canais processados")
            
            preferences.recordRun(System.currentTimeMillis(), successCount, failCount, successNames, failNames)
            
            // Gerar e salvar relatório de extração
            try {
                val report = extractorV2.generateReport()
                val reportFile = extractorV2.saveReport()
                
                Log.i(TAG, "═══════════════════════════════════════════════════════")
                Log.i(TAG, "RELATÓRIO DE EXTRAÇÃO")
                Log.i(TAG, "═══════════════════════════════════════════════════════")
                Log.i(TAG, report)
                Log.i(TAG, "Relatório salvo em: ${reportFile.absolutePath}")
                
                // Atualizar status com resumo
                val summary = extractorV2.getQuickSummary()
                preferences.updateStatus("Concluído: $summary")
                
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao gerar relatório: ${e.message}", e)
                preferences.updateStatus("Lista Profissional Gerada com Sucesso")
            }
            
            delay(1000) // Pequeno delay para garantir que a UI capture o status final
            try { callback?.onResult(finalJson) } catch (e: Exception) { Log.w(TAG, "Callback failed", e) }
            
            saveToCache(jsonContent)
            // Agendamento do Worker para background updates
            try {
                // Tenta carregar a classe dinamicamente para evitar crash se o módulo worker não existir
                Class.forName("com.m3u.extension.worker.LinkExtractionWorker")
                    .getMethod("setupPeriodicWork", Context::class.java)
                    .invoke(null, this@ExtensionService)
                    
                // Youtube Extraction Worker (Chaquopy)
                com.m3u.extension.worker.YoutubeExtractorWorker.setupPeriodicWork(this@ExtensionService)
            } catch (e: Exception) {
                Log.w(TAG, "Worker não configurado ou indisponível: ${e.message}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro na extração assíncrona", e)
            preferences.updateStatus("Erro fatal: ${e.message}")
            try { callback?.onError(e.message) } catch (ex: Exception) { Log.w(TAG, "Callback failed", ex) }
        }
    }

    private data class RichResolution(
        val url: String,
        val headers: Map<String, String>,
        val kodiUrl: String
    )

    /**
     * Resolve uma URL utilizando o motor de extração e injeta headers necessários (format Kodi)
     * Realiza também o handshake para garantir que o link é reproduzível.
     */
    private suspend fun resolveAndEnrich(url: String): RichResolution? {
        LogManager.debug("Enriquecendo URL: ${url.take(50)}...")
        Log.d(TAG, "Iniciando resolveAndEnrich para: $url")
        
        // 1. Garante que temos cookies e UA atualizados
        captureAndStoreUserAgent()
        captureAndStoreBrowserCookies()
        
        // 2. Resolve via Interactor (múltiplos motores)
        val result = interactor.resolve(url)
        var resolvedUrl = result.getOrNull() ?: return null
        
        // 3. Coleta headers base (Cookies e User-Agent reais)
        val storedCookies = getStoredCookies()
        val storedUserAgent = getStoredUserAgent()
        val finalHeaders = mutableMapOf<String, String>()
        
        if (storedCookies.isNotEmpty()) {
            val cookieString = storedCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            finalHeaders["Cookie"] = cookieString
        }
        finalHeaders["User-Agent"] = storedUserAgent
        
        // Usa a URL base (sem headers Kodi) como Referer
        val baseReferer = url.substringBefore("|")
        finalHeaders["Referer"] = baseReferer
        
        // 4. Extrai headers pré-existentes na URL resolvida (Kodi format |)
        var cleanUrl = resolvedUrl
        if (resolvedUrl.contains("|")) {
            val parts = resolvedUrl.split("|")
            cleanUrl = parts[0]
            val options = parts[1].split("&")
            options.forEach { opt ->
                val kv = opt.split("=", limit = 2)
                if (kv.size == 2) {
                    finalHeaders[kv[0]] = kv[1]
                }
            }
        }
        
        // 5. Handshake de validação (Garante que o player vai conseguir abrir)
        val isValid = performHandshake(cleanUrl, finalHeaders)
        if (!isValid) {
            Log.w(TAG, "⚠ Handshake falhou para link resolvido, mas prosseguindo se parecer stream: $cleanUrl")
        }
        
        // 6. Gera URL final formatada com headers (Kodi Standard)
        val headerOptions = finalHeaders.entries.joinToString("&") { "${it.key}=${it.value}" }
        val kodiUrl = if (headerOptions.isNotEmpty()) "$cleanUrl|$headerOptions" else cleanUrl
        
        Log.d(TAG, "✓ URL enriquecida gerada: ${kodiUrl.take(100)}...")
        
        return RichResolution(
            url = cleanUrl,
            headers = finalHeaders,
            kodiUrl = kodiUrl
        )
    }

    private suspend fun processChannel(
        channel: ChannelData, 
        seen: MutableSet<String>
    ): Map<String, Any?> {
        val name = channel.name?.trim().orEmpty()
        val url = channel.url?.trim().orEmpty()
        val group = channel.group?.trim().orEmpty()
        val logo = channel.logo?.trim()
        
        // 1. Validação Inicial
        if (name.isEmpty() || url.isEmpty() || group.isEmpty()) {
            return mapOf(
                "name" to name.ifEmpty { "Inválido" },
                "success" to false,
                "error" to "Campos obrigatórios ausentes"
            )
        }
        
        if (!url.startsWith("http", ignoreCase = true)) {
            return mapOf(
                "name" to name,
                "success" to false,
                "error" to "URL inválida (não começa com http)"
            )
        }
        
        // 2. Deduplicação
        val key = "${name.lowercase()}|${url.lowercase()}"
        if (!seen.add(key)) {
            return mapOf(
                "name" to name,
                "success" to false,
                "error" to "Canal duplicado (ignorado)"
            )
        }
        
        // 3. Detecção de YouTube e uso do Extrator V2 (Metodologia Robusta)
        val isYouTube = url.contains("youtube.com", ignoreCase = true) || 
                       url.contains("youtu.be", ignoreCase = true)
        
        if (isYouTube) {
            Log.d(TAG, "🎯 Detectado YouTube, usando ExtractorV2 para: $name")
            
            try {
                val currentFormat = preferences.format.first()
                val v2Result = extractorV2.extractChannel(
                    name = name,
                    url = url,
                    logo = logo,
                    group = group,
                    format = currentFormat
                )
                
                if (v2Result.success && v2Result.m3u8Url != null) {
                    val normalizedName = PlaylistProcessor.normalizeName(name)
                    val finalCategory = PlaylistProcessor.inferCategory(name, group)
                    val finalLogo = PlaylistProcessor.validateLogo(logo, normalizedName)
                    
                    Log.d(TAG, "✅ ExtractorV2 sucesso: $normalizedName (${v2Result.method})")
                    
                    // Injetar headers na URL (Kodi format) para compatibilidade multiprocesso
                    val kodiUrl = buildString {
                        append(v2Result.m3u8Url)
                        if (v2Result.headers.isNotEmpty()) {
                            append("|")
                            append(v2Result.headers.entries.joinToString("&") { "${it.key}=${it.value}" })
                        }
                    }
                    
                    Log.d(TAG, "📦 URL Final (Kodi Format): $kodiUrl")
                    
                    return mapOf(
                        "name" to normalizedName,
                        "original_name" to name,
                        "group" to finalCategory,
                        "logo" to finalLogo,
                        "m3u8" to kodiUrl,
                        "success" to true,
                        "headers" to v2Result.headers,
                        "extraction_method" to v2Result.method,
                        "error" to null
                    )
                } else {
                    Log.w(TAG, "⚠ ExtractorV2 falhou: ${v2Result.error}, tentando método legacy")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro no ExtractorV2: ${e.message}, tentando método legacy", e)
            }
        }
        
        // 4. Fallback: Resolução e Enriquecimento com Motor Pro-Max (método antigo)
        val resolutionResult = resolveAndEnrich(url)
        val resolvedUrl = resolutionResult?.url
        val finalHeaders = resolutionResult?.headers ?: emptyMap()
        val kodiUrl = resolutionResult?.kodiUrl
        
        // 5. Normalização e Enriquecimento de Metadados
        val normalizedName = PlaylistProcessor.normalizeName(name)
        val finalCategory = PlaylistProcessor.inferCategory(name, group)
        val finalLogo = PlaylistProcessor.validateLogo(logo, normalizedName)
        
        return mapOf(
            "name" to normalizedName,
            "original_name" to name,
            "group" to finalCategory,
            "logo" to finalLogo,
            "m3u8" to kodiUrl,
            "success" to (kodiUrl != null),
            "headers" to finalHeaders,
            "error" to if (kodiUrl == null) "Falha na resolução ou handshake" else null
        )
    }

    private fun performHandshake(url: String, headers: Map<String, String>): Boolean {
        return try {
            val cleanUrl = url.split("|")[0]
            LogManager.debug("Validando link (Handshake): ${cleanUrl.take(40)}...")
            
            // Verifica padrão da URL antes de conectar
            val looksLikeStream = cleanUrl.contains(".m3u8", ignoreCase = true) ||
                                 cleanUrl.contains(".mpd", ignoreCase = true) ||
                                 cleanUrl.contains(".ts", ignoreCase = true) ||
                                 cleanUrl.contains("/manifest", ignoreCase = true) ||
                                 cleanUrl.contains("/live/", ignoreCase = true) ||
                                 cleanUrl.contains("/hls/", ignoreCase = true) ||
                                 cleanUrl.contains("/stream/", ignoreCase = true)

            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
            
            val requestBuilder = Request.Builder()
                .url(cleanUrl)
                .header("Range", "bytes=0-0") // Request leve
                
            headers.forEach { (k, v) -> requestBuilder.header(k, v) }
            
            val response = client.newCall(requestBuilder.build()).execute()
            val code = response.code
            response.close()
            
            when {
                code in 200..299 -> true
                code in 300..399 -> true
                code == 206 -> true // Partial Content (sucesso com Range)
                looksLikeStream && code in listOf(401, 403) -> {
                    // Se parece stream mas deu 403, pode ser auth dinâmica que o player resolve
                    Log.w(TAG, "Handshake 403/401 mas parece stream válido: $cleanUrl")
                    true
                }
                else -> {
                    // Se parece muito um stream, aceita mesmo com erro para não perder o canal
                    looksLikeStream
                }
            }
        } catch (e: Exception) {
            // Em caso de erro de rede, se a URL parece válida, aceita
            val cleanUrl = url.split("|")[0]
            val valid = cleanUrl.contains(".m3u8", ignoreCase = true) || 
                       cleanUrl.contains(".mpd", ignoreCase = true)
            Log.w(TAG, "Handshake exception: ${e.message}. Aceito como válido? $valid")
            valid
        }
    }

    private val binder = object : IExtension.Stub() {
        override fun resolve(url: String): String? {
            Log.d(TAG, "resolve chamado via AIDL para: $url")
            return try {
                runBlocking {
                    // Check if YouTube
                    if (url.contains("youtube.com") || url.contains("youtu.be")) {
                        Log.d(TAG, "AIDL resolve: YouTube detected, using ExtractorV2")
                        val v2Result = extractorV2.extractChannel(
                            name = "YouTube Stream",
                            url = url,
                            forceRefresh = false
                        )
                        if (v2Result.success && v2Result.m3u8Url != null) {
                            return@runBlocking buildString {
                                append(v2Result.m3u8Url)
                                if (v2Result.headers.isNotEmpty()) {
                                    append("|")
                                    append(v2Result.headers.entries.joinToString("&") { "${it.key}=${it.value}" })
                                }
                            }
                        }
                    }
                    
                    val result = resolveAndEnrich(url)
                    result?.kodiUrl
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro fatal ao resolver via AIDL: ${e.message}")
                null
            }
        }

        override fun extractLinksAsync(jsonContent: String?, callback: IExtensionCallback?) {
            if (jsonContent.isNullOrBlank()) {
                try { callback?.onError("JSON vazio") } catch (e: Exception) { Log.w(TAG, "Callback failed", e) }
                return
            }

            scope.launch {
                processJsonAndNotify(jsonContent, callback)
            }
        }

        override fun syncChannels(callback: IExtensionCallback?) {
            scope.launch {
                try {
                    val repo = com.m3u.extension.dropbox.DropboxRepository(this@ExtensionService)
                    val file = repo.downloadChannelsJson()
                    if (file == null || !file.exists()) {
                         try { callback?.onError("Falha ao baixar channels.json do Dropbox") } catch (e: Exception) { Log.w(TAG, "Callback failed", e) }
                         return@launch
                    }
                    val jsonContent = file.readText()
                    processJsonAndNotify(jsonContent, callback)
                } catch (e: Exception) {
                    Log.e(TAG, "Erro no syncChannels", e)
                    try { callback?.onError("Erro: ${e.message}") } catch (ex: Exception) { Log.w(TAG, "Callback failed", ex) }
                }
            }
        }

        private suspend fun extractLinksInternal(
            jsonContent: String?,
            onItem: suspend (index: Int, total: Int, name: String, resolvedUrl: String?) -> Unit
        ) {
            if (jsonContent.isNullOrBlank()) return
            val gson = Gson()
            val channelsData = gson.fromJson(jsonContent, ChannelsData::class.java)
            val channels = channelsData?.channels ?: emptyList()
            val total = channels.size
            
            channels.forEachIndexed { index, channel ->
                val result = interactor.resolve(channel.url)
                onItem(index + 1, total, channel.name, result.getOrNull())
            }
        }
    }

    private fun parseUrlsFromJson(json: String): List<String> {
        val urls = mutableListOf<String>()
        try {
            // Busca simples por URLs no JSON para evitar dependência pesada de GSON/Kotlinx.Serialization no AIDL
            val regex = """https?://[^\s"']+""".toRegex()
            urls.addAll(regex.findAll(json).map { it.value }.toList())
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parsear JSON: ${e.message}")
        }
        return urls.distinct()
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun saveToCache(content: String) {
        try {
            val cacheFile = java.io.File(cacheDir, "channels_cache.json")
            cacheFile.writeText(content)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao salvar cache: ${e.message}")
        }
    }

    /**
     * Captura e armazena cookies do navegador para uso posterior
     */
    private suspend fun captureAndStoreBrowserCookies() {
        try {
            val cookies = BrowserUtils.warmupYouTubeSession(applicationContext)
            if (cookies.isNotEmpty()) {
                val cookieFile = java.io.File(cacheDir, "browser_cookies.json")
                val cookieJson = com.google.gson.Gson().toJson(cookies)
                cookieFile.writeText(cookieJson)
                Log.d(TAG, "✓ Cookies do navegador capturados e armazenados: ${cookies.size} cookies")
                
                // BROADCAST IDENTITY
                val cookieString = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
                notifyIdentityUpdate(getStoredUserAgent(), cookieString)
            } else {
                Log.w(TAG, "⚠ Nenhum cookie capturado do navegador")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao capturar cookies do navegador: ${e.message}")
        }
    }

    /**
     * Captura e armazena o User-Agent real do dispositivo
     */
    private fun captureAndStoreUserAgent() {
        try {
            val userAgent = BrowserUtils.getRealUserAgent(applicationContext)
            val uaFile = java.io.File(cacheDir, "user_agent.txt")
            uaFile.writeText(userAgent)
            Log.d(TAG, "✓ User-Agent capturado e armazenado: ${userAgent.substring(0, 50)}...")
            
            // BROADCAST IDENTITY (without cookies yet, or with existing)
            notifyIdentityUpdate(userAgent, null)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao capturar User-Agent: ${e.message}")
        }
    }

    /**
     * Notifica o App Universal sobre a nova identidade capturada.
     */
    private fun notifyIdentityUpdate(ua: String?, cookies: String?) {
        try {
            val intent = Intent("com.m3u.IDENTITY_UPDATE")
            if (ua != null) intent.putExtra("user_agent", ua)
            if (cookies != null) intent.putExtra("cookies", cookies)
            intent.setPackage("com.m3u.universal")
            sendBroadcast(intent)
            Log.d(TAG, "Identidade enviada para o App Universal (Broadcast)")
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao enviar broadcast de identidade: ${e.message}")
        }
    }

    /**
     * Recupera cookies armazenados
     */
    fun getStoredCookies(): Map<String, String> {
        return try {
            val cookieFile = java.io.File(cacheDir, "browser_cookies.json")
            if (cookieFile.exists()) {
                val cookieJson = cookieFile.readText()
                val type = object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
                com.google.gson.Gson().fromJson(cookieJson, type)
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao recuperar cookies: ${e.message}")
            emptyMap()
        }
    }

    /**
     * Recupera User-Agent armazenado
     */
    fun getStoredUserAgent(): String {
        return try {
            val uaFile = java.io.File(cacheDir, "user_agent.txt")
            if (uaFile.exists()) {
                uaFile.readText()
            } else {
                BrowserUtils.getRealUserAgent(applicationContext)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao recuperar User-Agent: ${e.message}")
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"
        }
    }

    companion object {
        private const val TAG = "ExtensionService"
        private const val MAX_CONCURRENT_REQUESTS = 1 // Ajustável
    }
}
