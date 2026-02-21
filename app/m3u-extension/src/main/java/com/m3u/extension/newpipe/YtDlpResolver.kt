package com.m3u.extension.newpipe

import android.content.Context
import android.util.Log
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.m3u.extension.logic.BrowserUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import org.json.JSONObject

/**
 * YtDlpResolver - Motor de fallback via yt-dlp (Python/Chaquopy)
 * Desenvolvido para máxima compatibilidade em Smart TVs e TV Boxes.
 */
object YtDlpResolver {
    private const val TAG = "YtDlpResolver"
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        try {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(context))
            }
            isInitialized = true
            Log.i(TAG, "Chaquopy (Python) inicializado com sucesso para YtDlpResolver.")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao inicializar Chaquopy", e)
        }
    }

    /**
     * Resolve uma URL usando o extrator fallback em Python.
     * Retorna um Result com a URL final ou erro.
     */
    suspend fun resolve(context: Context, url: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            init(context)
            if (!isInitialized) return@withContext Result.failure(Exception("Python não inicializado"))

            val py = Python.getInstance()
            val module = py.getModule("extractor_fallback")
            
            Log.d(TAG, "Iniciando extração via yt-dlp Fallback: $url")
            
            val preferences = com.m3u.extension.preferences.ExtensionPreferences(context)
            val format = preferences.format.first()
            
            val ffmpegPath: String? = null
            
            val pyResult: PyObject = module.callAttr("extract_url", url, ffmpegPath, format)
            val jsonResponse = pyResult.toString()
            
            val json = JSONObject(jsonResponse)
            
            if (json.has("error")) {
                val errorMsg = json.getString("error")
                Log.e(TAG, "Erro do yt-dlp: $errorMsg")
                return@withContext Result.failure(Exception(errorMsg))
            }

            var finalUrl = json.optString("url")
            if (finalUrl.isNullOrBlank()) {
                return@withContext Result.failure(Exception("URL final vazia no retorno do yt-dlp"))
            }

            // Integrar headers no formato Kodi (pipe)
            val headersJson = json.optJSONObject("headers")
            if (headersJson != null) {
                val headerList = mutableListOf<String>()
                headersJson.keys().forEach { key ->
                    headerList.add("$key=${headersJson.getString(key)}")
                }
                if (headerList.isNotEmpty()) {
                    finalUrl = "$finalUrl|${headerList.joinToString("&")}"
                }
            }

            Log.i(TAG, "✓ Extração Fallback concluída com headers: $finalUrl")
            return@withContext Result.success(finalUrl)

        } catch (e: Exception) {
            Log.e(TAG, "Exceção no YtDlpResolver", e)
            return@withContext Result.failure(e)
        }
    }
}
