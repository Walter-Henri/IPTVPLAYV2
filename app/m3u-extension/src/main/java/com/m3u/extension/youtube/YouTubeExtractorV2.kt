package com.m3u.extension.youtube

import android.content.Context
import android.util.Log
import com.chaquo.python.Python
import com.m3u.extension.preferences.ExtensionPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * YouTubeExtractorV2
 *
 * Orchestrates YouTube stream extraction using two complementary approaches:
 *
 * APPROACH 1 — WebView HLS Sniffing (fastest, no Python overhead):
 *   The TokenManager's WebView intercepts network requests via shouldInterceptRequest.
 *   If the page triggers an HLS request, we capture the URL instantly.
 *   This mirrors how the "old native engine" worked and is the PRIMARY method.
 *
 * APPROACH 2 — yt-dlp via Python/Chaquopy (fallback, most compatible):
 *   Uses tokens from the WebView session (cookies, visitorData, poToken)
 *   to authenticate yt-dlp as if it were the real browser on this device.
 *   Tries multiple player clients in order: web → tv_embedded → ios → android_embedded.
 *
 * Cache TTLs:
 *   - Stream URLs: 5 h (YouTube signed URLs expire in ~6 h)
 *   - Tokens:      45 min (PO Token typical TTL)
 */
class YouTubeExtractorV2(private val context: Context) {

    companion object {
        private const val TAG              = "YouTubeExtractorV2"
        private const val STREAM_CACHE_TTL = 5 * 60 * 60 * 1_000L  // 5 h
        private const val TOKEN_CACHE_TTL  = 45 * 60 * 1_000L       // 45 min
    }

    private val python: Python by lazy { Python.getInstance() }
    private val tokenManager by lazy { YouTubeWebViewTokenManager(context) }
    private val cacheDir: File by lazy {
        File(context.cacheDir, "yt_streams_v4").apply { mkdirs() }
    }

    // ──────────────────────────────────────────────────────────────
    // DATA
    // ──────────────────────────────────────────────────────────────

    data class ExtractionResult(
        val success: Boolean,
        val m3u8Url: String?,
        val headers: Map<String, String>,
        val method: String?,
        val error: String?
    )

    // ──────────────────────────────────────────────────────────────
    // PUBLIC: extractChannel
    // ──────────────────────────────────────────────────────────────

    suspend fun extractChannel(
        name: String,
        url: String,
        logo: String?  = null,
        group: String? = null,
        forceRefresh: Boolean = false,
        format: String? = null
    ): ExtractionResult = withContext(Dispatchers.IO) {

        Log.d(TAG, "════════════════════════════════")
        Log.d(TAG, "🚀 EXTRACTING: $name")

        // 1. Fast path: serve from cache if fresh
        if (!forceRefresh) {
            getCachedStream(url)?.let {
                Log.d(TAG, "✓ Cache hit")
                return@withContext it
            }
        }

        // 2. Fetch WebView tokens (may include a bonus HLS URL from sniffing)
        val tokens = withContext(Dispatchers.Main) {
            tokenManager.fetchTokens(forceRefresh, url)
        }

        // 3. If the WebView sniffed an HLS URL, use it directly
        if (tokens.hasHlsManifest) {
            Log.d(TAG, "⚡ HLS URL from WebView sniffing: ${tokens.hlsManifestUrl.take(60)}")
            val headers = buildBaseHeaders(tokens)
            val result = ExtractionResult(
                success = true,
                m3u8Url = tokens.hlsManifestUrl,
                headers = headers,
                method  = "webview_sniffing",
                error   = null
            )
            cacheStream(url, result, name)
            return@withContext result
        }

        // 4. yt-dlp extraction via Python
        var result = runPythonExtraction(name, url, logo, group, tokens, format)

        // 5. Smart retry: if failed, force-refresh tokens and try once more
        if (!result.success) {
            Log.w(TAG, "⚠ Primeira tentativa falhou. Smart retry com tokens frescos...")
            val freshTokens = withContext(Dispatchers.Main) {
                tokenManager.fetchTokens(forceRefresh = true, targetUrl = url)
            }
            result = runPythonExtraction(name, url, logo, group, freshTokens, format)
        }

        cacheStream(url, result, name)
        Log.d(TAG, "════════════════════════════════")
        result
    }

    // ──────────────────────────────────────────────────────────────
    // PYTHON EXTRACTION
    // ──────────────────────────────────────────────────────────────

    private suspend fun runPythonExtraction(
        name: String,
        url: String,
        logo: String?,
        group: String?,
        tokens: YouTubeWebViewTokenManager.YouTubeTokens,
        format: String? = null
    ): ExtractionResult = withContext(Dispatchers.IO) {

        val ts          = System.currentTimeMillis()
        val inputFile   = File(cacheDir, "input_$ts.json")
        val outputFile  = File(cacheDir, "output_$ts.json")
        val cookiesFile = File(cacheDir, "cookies_$ts.txt")

        try {
            // Build input JSON for yt-dlp Python module
            val inputJson = JSONObject().apply {
                put("channels", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("name",  name)
                        put("url",   url)
                        put("logo",  logo  ?: "")
                        put("group", group ?: "YouTube")
                    })
                })
                put("tokens", JSONObject().apply {
                    put("userAgent",      tokens.userAgent)
                    put("visitorData",    tokens.visitorData)
                    put("visitorInfoLive",tokens.visitorInfoLive)
                    put("poToken",        tokens.poToken)
                    put("clientVersion",  tokens.clientVersion)
                    put("apiKey",         tokens.apiKey)
                    put("hl",             tokens.hl)
                    put("gl",             tokens.gl)
                    put("cookies",        tokens.cookies)
                    put("format",         format ?: ExtensionPreferences.DEFAULT_FORMAT)
                })
            }
            inputFile.writeText(inputJson.toString())

            // Write cookies in Netscape format for yt-dlp --cookies
            if (tokens.hasCookies) {
                cookiesFile.writeText(buildNetscapeCookies(tokens.cookies))
            }

            // Call Python extractor
            val module = python.getModule("extractor_v2")
            module.callAttr(
                "extract",
                inputFile.absolutePath,
                outputFile.absolutePath,
                if (tokens.hasCookies) cookiesFile.absolutePath else null,
                tokens.userAgent
            )

            if (!outputFile.exists()) {
                return@withContext ExtractionResult(
                    false, null, emptyMap(), null,
                    "Python nao gerou arquivo de saida"
                )
            }

            val outputJson = JSONObject(outputFile.readText())
            val channels   = outputJson.getJSONArray("channels")

            if (channels.length() == 0) {
                return@withContext ExtractionResult(false, null, emptyMap(), null, "Sem resultados")
            }

            val ch = channels.getJSONObject(0)
            if (!ch.getBoolean("success")) {
                return@withContext ExtractionResult(
                    success = false, m3u8Url = null, headers = emptyMap(),
                    method  = null,
                    error   = ch.optString("error", "Falha desconhecida")
                )
            }

            // Build headers map
            val headers = buildBaseHeaders(tokens).toMutableMap()
            ch.optJSONObject("headers")?.let { h ->
                h.keys().forEach { k -> headers[k] = h.getString(k) }
            }

            val m3u8   = ch.getString("m3u8")
            val method = ch.optString("extraction_method", "yt-dlp")

            Log.d(TAG, "✅ Extração OK via $method")
            Log.d(TAG, "   M3U8: ${m3u8.take(70)}...")

            ExtractionResult(
                success = true,
                m3u8Url = m3u8,
                headers = headers,
                method  = method,
                error   = null
            )

        } catch (e: Exception) {
            Log.e(TAG, "Erro Python: ${e.message}", e)
            ExtractionResult(false, null, emptyMap(), null, e.message)
        } finally {
            inputFile.delete()
            outputFile.delete()
            cookiesFile.delete()
        }
    }

    // ──────────────────────────────────────────────────────────────
    // HELPERS
    // ──────────────────────────────────────────────────────────────

    private fun buildBaseHeaders(tokens: YouTubeWebViewTokenManager.YouTubeTokens): Map<String, String> {
        return buildMap {
            put("User-Agent", tokens.userAgent)
            put("Referer",    "https://www.youtube.com/")
            put("Origin",     "https://www.youtube.com")
            if (tokens.hasCookies) put("Cookie", tokens.cookies)
            if (tokens.visitorData.isNotBlank()) put("X-Goog-Visitor-Id", tokens.visitorData)
            if (tokens.poToken.isNotBlank()) put("X-YouTube-Po-Token", tokens.poToken)
            if (tokens.clientVersion.isNotBlank()) {
                put("X-YouTube-Client-Name",    "1")
                put("X-YouTube-Client-Version", tokens.clientVersion)
            }
        }
    }

    private fun buildNetscapeCookies(rawCookies: String): String {
        val sb = StringBuilder("# Netscape HTTP Cookie File\n# Auto-generated\n\n")
        rawCookies.split(";").forEach { pair ->
            val t    = pair.trim()
            val eq   = t.indexOf('=')
            if (eq < 0) return@forEach
            val name  = t.substring(0, eq).trim()
            val value = t.substring(eq + 1).trim()
            sb.appendLine(".youtube.com\tTRUE\t/\tTRUE\t2147483647\t$name\t$value")
        }
        return sb.toString()
    }

    // ──────────────────────────────────────────────────────────────
    // STREAM CACHE
    // ──────────────────────────────────────────────────────────────

    private fun cacheStream(url: String, result: ExtractionResult, name: String) {
        if (!result.success || result.m3u8Url.isNullOrBlank()) return
        val file = File(cacheDir, "stream_${url.hashCode()}.json")
        try {
            file.writeText(JSONObject().apply {
                put("name",      name)
                put("success",   result.success)
                put("m3u8Url",   result.m3u8Url)
                put("headers",   JSONObject(result.headers as Map<*, *>))
                put("method",    result.method ?: "")
                put("error",     result.error  ?: "")
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
            val age  = System.currentTimeMillis() - json.getLong("timestamp")
            if (age > STREAM_CACHE_TTL) return null
            if (!json.getBoolean("success")) return null

            val headers = mutableMapOf<String, String>()
            json.optJSONObject("headers")?.let { h ->
                h.keys().forEach { k -> headers[k] = h.getString(k) }
            }
            ExtractionResult(
                success  = true,
                m3u8Url  = json.optString("m3u8Url").takeIf { it.isNotEmpty() },
                headers  = headers,
                method   = json.optString("method"),
                error    = null
            )
        } catch (e: Exception) { null }
    }

    fun clearOldCache() {
        val now = System.currentTimeMillis()
        var removed = 0
        cacheDir.listFiles()?.forEach { file ->
            if (!file.name.startsWith("stream_")) return@forEach
            try {
                val age = now - JSONObject(file.readText()).getLong("timestamp")
                if (age > STREAM_CACHE_TTL) { file.delete(); removed++ }
            } catch (_: Exception) { file.delete(); removed++ }
        }
        Log.d(TAG, "clearOldCache: $removed arquivo(s) removido(s)")
    }

    fun clearAllCache() {
        tokenManager.clearCache()
        cacheDir.listFiles()?.forEach { it.delete() }
        Log.d(TAG, "Cache completo limpo")
    }

    // ──────────────────────────────────────────────────────────────
    // REPORTING
    // ──────────────────────────────────────────────────────────────

    fun generateReport(): String {
        val now         = System.currentTimeMillis()
        val streamFiles = cacheDir.listFiles()?.filter { it.name.startsWith("stream_") } ?: emptyList()
        val active      = streamFiles.count { f ->
            try { now - JSONObject(f.readText()).getLong("timestamp") <= STREAM_CACHE_TTL }
            catch (_: Exception) { false }
        }
        val tokenFile = File(context.cacheDir, "yt_webview_tokens.json")
        val tokenStatus = if (tokenFile.exists()) {
            runCatching {
                val age = now - JSONObject(tokenFile.readText()).getLong("timestamp")
                if (age <= TOKEN_CACHE_TTL) {
                    "✅ VÁLIDO (~${((TOKEN_CACHE_TTL - age) / 60_000).toInt()} min)"
                } else "⚠️ EXPIRADO"
            }.getOrDefault("⚠️ INVÁLIDO")
        } else "❌ AUSENTE"

        val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return buildString {
            appendLine("╔═════════════════════════════════════════════╗")
            appendLine("║      YOUTUBE EXTRACTOR V6 — ANALYTICS       ║")
            appendLine("╠═════════════════════════════════════════════╣")
            appendLine("  MOTOR V6.0  : ATIVO")
            appendLine("  TOKEN STATUS: $tokenStatus")
            appendLine("  STREAMS ATIVOS: $active / ${streamFiles.size}")
            appendLine("╚═════════════════════════════════════════════╝")
            appendLine()
            appendLine("ÚLTIMOS PROCESSAMENTOS:")
            appendLine("───────────────────────────────────────────────")
            if (streamFiles.isEmpty()) {
                appendLine("   (Nenhum canal processado)")
            } else {
                streamFiles.sortedByDescending { it.lastModified() }.take(15).forEach { f ->
                    runCatching {
                        val json    = JSONObject(f.readText())
                        val ok      = json.getBoolean("success")
                        val cName   = json.optString("name", "?")
                        val time    = fmt.format(Date(json.getLong("timestamp")))
                        val method  = json.optString("method", "-")
                        if (ok) appendLine(" ✅ $cName | $method @ $time")
                        else   appendLine(" ❌ $cName | ${json.optString("error", "?").take(35)} @ $time")
                    }
                }
            }
            appendLine("───────────────────────────────────────────────")
            appendLine("FORMATO: ${ExtensionPreferences.DEFAULT_FORMAT}")
        }
    }

    fun saveReport(): File {
        val file = File(context.cacheDir, "youtube_extractor_report.txt")
        runCatching { file.writeText(generateReport()) }
        return file
    }

    fun getQuickSummary(): String {
        val now         = System.currentTimeMillis()
        val streamFiles = cacheDir.listFiles()?.filter { it.name.startsWith("stream_") } ?: emptyList()
        val active      = streamFiles.count { f ->
            runCatching { now - JSONObject(f.readText()).getLong("timestamp") <= STREAM_CACHE_TTL }.getOrDefault(false)
        }
        val tokenFile = File(context.cacheDir, "yt_webview_tokens.json")
        val tokenOk   = tokenFile.exists() && runCatching {
            now - JSONObject(tokenFile.readText()).getLong("timestamp") <= TOKEN_CACHE_TTL
        }.getOrDefault(false)
        return "Token:${if (tokenOk) "OK" else "NO"} | Streams:$active/${streamFiles.size}"
    }
}
