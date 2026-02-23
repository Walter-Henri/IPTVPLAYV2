package com.m3u.extension.youtube

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/**
 * YouTubeWebViewTokenManager
 *
 * Usa um WebView oculto para extrair automaticamente:
 *  - User-Agent real do dispositivo
 *  - Cookies de sessão do YouTube
 *  - visitor_data (necessário para streams autenticados)
 *  - PO Token (Proof of Origin Token) via JavaScript do botguard
 *
 * USO:
 *   val manager = YouTubeWebViewTokenManager(context)
 *   val tokens = manager.fetchTokens()
 */
class YouTubeWebViewTokenManager(private val context: Context) {

    companion object {
        private const val TAG = "YTWebViewTokenManager"
        private const val YOUTUBE_URL = "https://www.youtube.com"
        private const val WEBVIEW_TIMEOUT_MS = 20_000L
        private const val PAGE_SETTLE_DELAY_MS = 3_000L

        private val EXTRACT_TOKENS_JS = """
            (function() {
                try {
                    var result = {};
                    result.userAgent = navigator.userAgent;
                    if (window.ytcfg && window.ytcfg.get) {
                        result.visitorData      = window.ytcfg.get('VISITOR_DATA')     || '';
                        result.visitorInfoLive  = window.ytcfg.get('VISITOR_INFO1_LIVE')|| '';
                        result.clientVersion    = window.ytcfg.get('INNERTUBE_CLIENT_VERSION') || '';
                        result.clientName       = window.ytcfg.get('INNERTUBE_CLIENT_NAME') || '';
                        result.apiKey           = window.ytcfg.get('INNERTUBE_API_KEY') || '';
                        result.hl               = window.ytcfg.get('HL') || 'pt';
                        result.gl               = window.ytcfg.get('GL') || 'BR';
                    }
                    if (window.botguard && window.botguard.invoke) {
                        try {
                            window.botguard.invoke(function(token) {
                                result.poToken = token || '';
                            });
                        } catch(e) {}
                    }
                    if (!result.poToken && window.yt && window.yt.config_ && window.yt.config_.WEB_PLAYER_CONTEXT_CONFIGS) {
                        try {
                            var configs = window.yt.config_.WEB_PLAYER_CONTEXT_CONFIGS;
                            var firstKey = Object.keys(configs)[0];
                            if (firstKey && configs[firstKey].poToken) {
                                result.poToken = configs[firstKey].poToken;
                            }
                        } catch(e) {}
                    }
                    try {
                        result.sessionToken = localStorage.getItem('yt-player-session-token') || '';
                    } catch(e) {}
                    return JSON.stringify(result);
                } catch(e) {
                    return JSON.stringify({ error: e.toString() });
                }
            })();
        """.trimIndent()

        private val FORCE_PO_TOKEN_JS = """
            (function() {
                try {
                    if (window.ytcfg) {
                        var visitorData = window.ytcfg.get('VISITOR_DATA') || '';
                        fetch('https://www.youtube.com/youtubei/v1/player?prettyPrint=false', {
                            method: 'POST',
                            headers: {
                                'Content-Type': 'application/json',
                                'X-Goog-Visitor-Id': visitorData,
                                'X-YouTube-Client-Name': '1',
                                'X-YouTube-Client-Version': window.ytcfg.get('INNERTUBE_CLIENT_VERSION') || '2.20240101.00.00'
                            },
                            body: JSON.stringify({
                                context: {
                                    client: {
                                        clientName: 'WEB',
                                        clientVersion: window.ytcfg.get('INNERTUBE_CLIENT_VERSION') || '2.20240101.00.00',
                                        visitorData: visitorData
                                    }
                                },
                                videoId: 'dQw4w9WgXcQ'
                            })
                        })
                        .then(r => r.json())
                        .then(data => {
                            if (data && data.serviceTrackingParams) {
                                window._ytPoToken = data.serviceTrackingParams
                                    .find(p => p.service === 'GFEEDBACK')
                                    ?.params?.find(p => p.key === 'token')?.value || '';
                            }
                        })
                        .catch(e => {});
                    }
                    return 'triggered';
                } catch(e) {
                    return 'error: ' + e.toString();
                }
            })();
        """.trimIndent()

        private val COLLECT_PO_TOKEN_JS = """
            (function() {
                return JSON.stringify({
                    poToken: window._ytPoToken || '',
                    visitorData: window.ytcfg ? (window.ytcfg.get('VISITOR_DATA') || '') : ''
                });
            })();
        """.trimIndent()
    }

    data class YouTubeTokens(
        val userAgent: String,
        val cookies: String,
        val visitorData: String,
        val visitorInfoLive: String,
        val poToken: String,
        val clientVersion: String,
        val apiKey: String,
        val hl: String,
        val gl: String
    ) {
        val hasPoToken: Boolean get() = poToken.isNotBlank()
        val hasCookies: Boolean get() = cookies.isNotBlank()
        val hasVisitorData: Boolean get() = visitorData.isNotBlank()

        override fun toString(): String = buildString {
            appendLine("=== YouTubeTokens ===")
            appendLine("UA: ${userAgent.take(60)}...")
            appendLine("Cookies: ${if (hasCookies) "${cookies.length} chars" else "AUSENTE"}")
            appendLine("visitorData: ${if (hasVisitorData) "${visitorData.take(20)}..." else "AUSENTE"}")
            appendLine("poToken: ${if (hasPoToken) "${poToken.take(20)}..." else "AUSENTE"}")
            appendLine("clientVersion: $clientVersion")
            appendLine("hl/gl: $hl/$gl")
        }
    }

    suspend fun fetchTokens(forceRefresh: Boolean = false): YouTubeTokens {
        if (!forceRefresh) {
            getCachedTokens()?.let {
                Log.d(TAG, "✓ Usando tokens em cache")
                return it
            }
        }

        Log.d(TAG, "Iniciando captura de tokens via WebView...")
        val deferred = CompletableDeferred<YouTubeTokens>()

        withContext(Dispatchers.Main) {
            startWebViewCapture(deferred)
        }

        return try {
            withTimeout(WEBVIEW_TIMEOUT_MS + 5_000L) {
                deferred.await()
            }.also { tokens ->
                Log.d(TAG, "Tokens capturados:\n$tokens")
                cacheTokens(tokens)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Timeout ou erro na captura de tokens: ${e.message}")
            fallbackTokens()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun startWebViewCapture(deferred: CompletableDeferred<YouTubeTokens>) {
        val handler = Handler(Looper.getMainLooper())

        try {
            val webView = WebView(context)
            val cookieManager = CookieManager.getInstance()

            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, true)

            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                useWideViewPort = true
                loadWithOverviewMode = true
            }

            webView.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(msg: ConsoleMessage?): Boolean = true
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, pageUrl: String?) {
                    Log.d(TAG, "Página carregada: $pageUrl")

                    handler.postDelayed({
                        if (deferred.isCompleted) return@postDelayed

                        view?.evaluateJavascript(FORCE_PO_TOKEN_JS) { _ ->
                            handler.postDelayed({
                                if (deferred.isCompleted) return@postDelayed

                                view?.evaluateJavascript(EXTRACT_TOKENS_JS) { rawResult ->
                                    view?.evaluateJavascript(COLLECT_PO_TOKEN_JS) { rawPoToken ->

                                        val userAgent = WebSettings.getDefaultUserAgent(context)
                                        val cookies = cookieManager.getCookie(YOUTUBE_URL) ?: ""

                                        val tokens = parseTokenResults(
                                            rawResult = rawResult,
                                            rawPoToken = rawPoToken,
                                            userAgent = userAgent,
                                            cookies = cookies
                                        )

                                        if (!deferred.isCompleted) {
                                            deferred.complete(tokens)
                                            view?.destroy()
                                        }
                                    }
                                }
                            }, 2_500L)
                        }
                    }, PAGE_SETTLE_DELAY_MS)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        Log.w(TAG, "Erro na página principal: ${error?.description}")
                    }
                }
            }

            webView.loadUrl(YOUTUBE_URL)

            handler.postDelayed({
                if (!deferred.isCompleted) {
                    Log.w(TAG, "Timeout forçado - coletando o que tiver")
                    val userAgent = WebSettings.getDefaultUserAgent(context)
                    val cookies = cookieManager.getCookie(YOUTUBE_URL) ?: ""

                    webView.evaluateJavascript(EXTRACT_TOKENS_JS) { rawResult ->
                        val tokens = parseTokenResults(
                            rawResult = rawResult,
                            rawPoToken = "null",
                            userAgent = userAgent,
                            cookies = cookies
                        )
                        if (!deferred.isCompleted) {
                            deferred.complete(tokens)
                            webView.destroy()
                        }
                    }
                }
            }, WEBVIEW_TIMEOUT_MS)

        } catch (e: Exception) {
            Log.e(TAG, "Erro crítico no WebView: ${e.message}", e)
            if (!deferred.isCompleted) {
                deferred.complete(fallbackTokens())
            }
        }
    }

    private fun parseTokenResults(
        rawResult: String?,
        rawPoToken: String?,
        userAgent: String,
        cookies: String
    ): YouTubeTokens {
        var visitorData = ""
        var visitorInfoLive = ""
        var poToken = ""
        var clientVersion = ""
        var apiKey = ""
        var hl = "pt"
        var gl = "BR"

        rawResult?.let { raw ->
            val cleaned = raw.trim().removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")
            try {
                val json = JSONObject(
                    if (cleaned.startsWith("{")) cleaned
                    else raw.trim().let {
                        it.substring(1, it.length - 1)
                            .replace("\\\"", "\"")
                            .replace("\\n", "\n")
                    }
                )
                visitorData     = json.optString("visitorData")
                visitorInfoLive = json.optString("visitorInfoLive")
                clientVersion   = json.optString("clientVersion")
                apiKey          = json.optString("apiKey")
                hl              = json.optString("hl").ifBlank { "pt" }
                gl              = json.optString("gl").ifBlank { "BR" }
                poToken         = json.optString("poToken")
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao parsear resultado JS: ${e.message}")
            }
        }

        rawPoToken?.let { raw ->
            try {
                val cleaned = raw.trim().removeSurrounding("\"").replace("\\\"", "\"")
                val json = JSONObject(if (cleaned.startsWith("{")) cleaned else "{}")
                val fetchedPoToken = json.optString("poToken")
                if (fetchedPoToken.isNotBlank()) poToken = fetchedPoToken
                val fetchedVisitorData = json.optString("visitorData")
                if (fetchedVisitorData.isNotBlank() && visitorData.isBlank()) {
                    visitorData = fetchedVisitorData
                }
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao parsear PO Token coletado: ${e.message}")
            }
        }

        Log.d(TAG, "  visitorData: ${if (visitorData.isNotBlank()) "✓" else "✗"}")
        Log.d(TAG, "  poToken: ${if (poToken.isNotBlank()) "✓" else "✗"}")
        Log.d(TAG, "  cookies: ${if (cookies.isNotBlank()) "✓" else "✗"}")

        return YouTubeTokens(
            userAgent = userAgent,
            cookies = cookies,
            visitorData = visitorData,
            visitorInfoLive = visitorInfoLive,
            poToken = poToken,
            clientVersion = clientVersion,
            apiKey = apiKey,
            hl = hl,
            gl = gl
        )
    }

    private val CACHE_TTL_MS = 45 * 60 * 1000L
    private val cacheFile by lazy {
        java.io.File(context.cacheDir, "yt_webview_tokens.json")
    }

    private fun cacheTokens(tokens: YouTubeTokens) {
        try {
            val json = JSONObject().apply {
                put("userAgent", tokens.userAgent)
                put("cookies", tokens.cookies)
                put("visitorData", tokens.visitorData)
                put("visitorInfoLive", tokens.visitorInfoLive)
                put("poToken", tokens.poToken)
                put("clientVersion", tokens.clientVersion)
                put("apiKey", tokens.apiKey)
                put("hl", tokens.hl)
                put("gl", tokens.gl)
                put("timestamp", System.currentTimeMillis())
            }
            cacheFile.writeText(json.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao salvar tokens em cache: ${e.message}")
        }
    }

    private fun getCachedTokens(): YouTubeTokens? {
        if (!cacheFile.exists()) return null
        return try {
            val json = JSONObject(cacheFile.readText())
            val age = System.currentTimeMillis() - json.getLong("timestamp")
            if (age > CACHE_TTL_MS) return null
            YouTubeTokens(
                userAgent = json.getString("userAgent"),
                cookies = json.getString("cookies"),
                visitorData = json.getString("visitorData"),
                visitorInfoLive = json.optString("visitorInfoLive"),
                poToken = json.getString("poToken"),
                clientVersion = json.optString("clientVersion"),
                apiKey = json.optString("apiKey"),
                hl = json.optString("hl", "pt"),
                gl = json.optString("gl", "BR")
            )
        } catch (e: Exception) { null }
    }

    fun clearCache() {
        cacheFile.delete()
        Log.d(TAG, "Cache de tokens limpo")
    }

    private fun fallbackTokens() = YouTubeTokens(
        userAgent = WebSettings.getDefaultUserAgent(context),
        cookies = CookieManager.getInstance().getCookie(YOUTUBE_URL) ?: "",
        visitorData = "", visitorInfoLive = "", poToken = "",
        clientVersion = "", apiKey = "", hl = "pt", gl = "BR"
    )
}
