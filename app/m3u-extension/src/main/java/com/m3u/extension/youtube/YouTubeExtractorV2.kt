package com.m3u.extension.youtube

import android.content.Context
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.PyObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.flow.first
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * YouTube Stream Extractor V2 - Metodologia Robusta
 * 
 * Características:
 * - Múltiplas tentativas com diferentes clients
 * - Validação de streams antes de retornar
 * - Fallback automático
 * - Cache de resultados bem-sucedidos
 * - Logs detalhados para debug
 * - Relatórios de extração
 */
class YouTubeExtractorV2(private val context: Context) {
    
    companion object {
        private const val TAG = "YouTubeExtractorV2"
        private const val CACHE_VALIDITY_HOURS = 6
    }
    
    private val python: Python by lazy { Python.getInstance() }
    private val cacheDir: File by lazy {
        File(context.cacheDir, "yt_streams").apply { mkdirs() }
    }
    private val _logger: com.m3u.extension.logging.ExtractionLogger by lazy {
        com.m3u.extension.logging.ExtractionLogger(context)
    }
    
    data class ExtractionResult(
        val success: Boolean,
        val m3u8Url: String?,
        val headers: Map<String, String>,
        val method: String?,
        val error: String?
    )
    
    /**
     * Extrai um canal do YouTube com validação robusta
     */
    suspend fun extractChannel(
        name: String,
        url: String,
        logo: String? = null,
        group: String? = null
    ): ExtractionResult = withContext(Dispatchers.IO) {
        
        Log.d(TAG, "Extraindo: $name")
        Log.d(TAG, "URL: $url")
        
        // 1. Verificar cache primeiro
        val cached = getCachedResult(url)
        if (cached != null && isCacheValid(url)) {
            Log.d(TAG, "✓ Usando resultado em cache")
            return@withContext cached
        }
        
        // 2. Tentar extração com Python
        try {
            val result = extractWithPython(url, name, logo, group)
            
            if (result.success && result.m3u8Url != null) {
                // 3. Validar o stream antes de aceitar
                if (validateStream(result.m3u8Url, result.headers)) {
                    Log.d(TAG, "✅ Stream validado e funcional")
                    cacheResult(url, result)
                    return@withContext result
                } else {
                    Log.w(TAG, "⚠ Stream extraído mas validação falhou")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro na extração Python: ${e.message}", e)
        }
        
        // 4. Se chegou aqui, todas as tentativas falharam
        ExtractionResult(
            success = false,
            m3u8Url = null,
            headers = emptyMap(),
            method = null,
            error = "Falha na extração e validação"
        )
    }
    
    /**
     * Extrai usando o módulo Python v2
     */
    private suspend fun extractWithPython(
        url: String,
        name: String,
        logo: String?,
        group: String?
    ): ExtractionResult {
        
        val module = python.getModule("extractor_v2")
        
        // Criar JSON de entrada
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
        
        // Arquivos temporários
        val inputFile = File(cacheDir, "input_${System.currentTimeMillis()}.json")
        val outputFile = File(cacheDir, "output_${System.currentTimeMillis()}.json")
        
        try {
            // Salvar input
            inputFile.writeText(inputJson.toString())
            
            // Executar extração
            val preferences = com.m3u.extension.preferences.ExtensionPreferences(context)
            val format = preferences.format.first()
            
            module.callAttr("extract", inputFile.absolutePath, outputFile.absolutePath, null, format)
            
            // Ler resultado
            if (outputFile.exists()) {
                val resultJson = JSONObject(outputFile.readText())
                val channels = resultJson.getJSONArray("channels")
                
                if (channels.length() > 0) {
                    val channel = channels.getJSONObject(0)
                    val attemptsArray = channel.optJSONArray("attempts")
                    val attemptLogs = mutableListOf<com.m3u.extension.logging.ExtractionLogger.AttemptLog>()
                    
                    if (attemptsArray != null) {
                        for (i in 0 until attemptsArray.length()) {
                            val attemptJson = attemptsArray.getJSONObject(i)
                            val attempt = com.m3u.extension.logging.ExtractionLogger.AttemptLog(
                                method = attemptJson.getString("method"),
                                userAgent = null, // UA logado internamente no python
                                success = attemptJson.getBoolean("success"),
                                error = attemptJson.optString("error").takeIf { it.isNotEmpty() },
                                duration = attemptJson.optLong("duration", 0L)
                            )
                            attemptLogs.add(attempt)
                            
                            // Loga cada tentativa individualmente no logger global
                            _logger.logAttempt(
                                channelName = name,
                                channelUrl = url,
                                method = attempt.method,
                                userAgent = null,
                                success = attempt.success,
                                error = attempt.error,
                                duration = attempt.duration
                            )
                        }
                    }
                    
                    if (channel.getBoolean("success")) {
                        val headers = mutableMapOf<String, String>()
                        val headersJson = channel.getJSONObject("headers")
                        headersJson.keys().forEach { key ->
                            headers[key] = headersJson.getString(key)
                        }
                        
                        val result = ExtractionResult(
                            success = true,
                            m3u8Url = channel.getString("m3u8"),
                            headers = headers,
                            method = channel.optString("extraction_method"),
                            error = null
                        )
                        
                        // Registra sucesso do canal com as tentativas
                        _logger.logChannel(name, url, true, result.method, null, attemptLogs)
                        
                        return result
                    } else {
                        val error = channel.optString("error", "Unknown error")
                        _logger.logChannel(name, url, false, null, error, attemptLogs)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro fatal no processo python: ${e.message}")
            _logger.logChannel(name, url, false, null, "Erro fatal: ${e.message}", emptyList())
        } finally {
            inputFile.delete()
            outputFile.delete()
        }
        
        return ExtractionResult(
            success = false,
            m3u8Url = null,
            headers = emptyMap(),
            method = null,
            error = "Python extraction failed"
        )
    }
    
    /**
     * Valida se um stream M3U8 está acessível
     */
    private fun validateStream(url: String, headers: Map<String, String>): Boolean {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
            
            val requestBuilder = Request.Builder()
                .url(url)
                .get() // GET com Range é mais confiável que HEAD (que é bloqueado em muitos servidores)
                .addHeader("Range", "bytes=0-1024") 
            
            headers.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }
            
            val response = client.newCall(requestBuilder.build()).execute()
            val isValid = response.isSuccessful || response.code == 206
            
            if (isValid) {
                Log.d(TAG, "✓ Validação GET/Range bem-sucedida: ${response.code}")
            } else {
                Log.w(TAG, "⚠ Validação falhou: ${response.code}")
            }
            
            response.close()
            isValid
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro na validação: ${e.message}")
            false
        }
    }
    
    /**
     * Cache de resultados
     */
    private fun getCachedResult(url: String): ExtractionResult? {
        val cacheFile = File(cacheDir, url.hashCode().toString() + ".json")
        
        if (!cacheFile.exists()) return null
        
        return try {
            val json = JSONObject(cacheFile.readText())
            
            ExtractionResult(
                success = json.getBoolean("success"),
                m3u8Url = json.optString("m3u8Url").takeIf { it.isNotEmpty() },
                headers = json.getJSONObject("headers").let { headersJson ->
                    mutableMapOf<String, String>().apply {
                        headersJson.keys().forEach { key ->
                            put(key, headersJson.getString(key))
                        }
                    }
                },
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
            Log.d(TAG, "✓ Resultado cacheado")
            
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
            val maxAge = CACHE_VALIDITY_HOURS * 60 * 60 * 1000
            
            age < maxAge
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Limpa cache antigo
     */
    fun clearOldCache() {
        cacheDir.listFiles()?.forEach { file ->
            try {
                val json = JSONObject(file.readText())
                val timestamp = json.getLong("timestamp")
                val age = System.currentTimeMillis() - timestamp
                val maxAge = CACHE_VALIDITY_HOURS * 60 * 60 * 1000
                
                if (age > maxAge) {
                    file.delete()
                    Log.d(TAG, "Cache antigo removido: ${file.name}")
                }
            } catch (e: Exception) {
                // Se não conseguir ler, deletar
                file.delete()
            }
        }
    }
    
    /**
     * Gera relatório de extração
     */
    suspend fun generateReport(): String {
        return _logger.formatReportAsText()
    }
    
    /**
     * Salva relatório em arquivo
     */
    suspend fun saveReport(): File {
        return _logger.saveReportToFile()
    }
    
    /**
     * Obtém resumo rápido
     */
    suspend fun getQuickSummary(): String {
        return _logger.getQuickSummary()
    }
    
    /**
     * Reseta o _logger para nova sessão
     */
    suspend fun resetLogger() {
        _logger.reset()
    }
}
