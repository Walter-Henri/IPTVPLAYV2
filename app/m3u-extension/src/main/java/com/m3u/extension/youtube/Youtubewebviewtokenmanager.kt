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
import java.io.File

/**
 * YouTubeWebViewTokenManager
 *
 * Uses a hidden WebView to extract YouTube session tokens needed by yt-dlp:
 *  - Real device User-Agent
 *  - YouTube session cookies (VISITOR_INFO1_LIVE, SID, HSID, etc.)
 *  - visitor_data (base64, from ytcfg)
 *  - PO Token (Proof of Origin Token) when available
 *  - client version, API key
 *
 * Strategy:
 *   1. Load youtube.com in a hidden WebView.
 *   2. After page settles (3s), run aggressive JS extraction.
 *   3. PO Token is extracted from multiple known locations.
 *   4. Tokens cached for 45 min to avoid excessive WebView sessions.
 */
class YouTubeWebViewTokenManager(private val context: Context) {

    companion object {
        private const val TAG = "YTWebViewTokenManager"
        private const val YOUTUBE_URL = "https://www.youtube.com"
        private const val TOKEN_URL   = "https://www.youtube.com/tv" // TV client often skips bot checks
        private const val WEBVIEW_TIMEOUT_MS   = 25_000L
        private const val PAGE_SETTLE_DELAY_MS = 3_500L
        private const val PO_TOKEN_WAIT_MS     = 2_500L
        private const val CACHE_TTL_MS         = 45 * 60 * 1_000L // 45 min

        /**
         * Aggressive JS that inspects every known place YouTube stores tokens.
         * Runs AFTER page settled to ensure ytcfg is populated.
         */
        private val EXTRACT_TOKENS_JS = """
            (function() {
                try {
                    var r = {};
                    r.userAgent = navigator.userAgent;

                    // --- ytcfg (primary source for all tokens) ---
                    if (window.ytcfg && typeof window.ytcfg.get === 'function') {
                        r.visitorData      = window.ytcfg.get('VISITOR_DATA')            || '';
                        r.visitorInfoLive  = window.ytcfg.get('VISITOR_INFO1_LIVE')      || '';
                        r.clientVersion    = window.ytcfg.get('INNERTUBE_CLIENT_VERSION') || '';
                        r.clientName       = window.ytcfg.get('INNERTUBE_CLIENT_NAME')    || '';
                        r.apiKey           = window.ytcfg.get('INNERTUBE_API_KEY')        || '';
                        r.hl               = window.ytcfg.get('HL')                       || 'pt';
                        r.gl               = window.ytcfg.get('GL')                       || 'BR';
                    }

                    // --- yt.config_ (fallback, older layout) ---
                    if (!r.visitorData && window.yt && window.yt.config_) {
                        r.visitorData     = window.yt.config_.VISITOR_DATA            || r.visitorData;
                        r.visitorInfoLive = window.yt.config_.VISITOR_INFO1_LIVE      || r.visitorInfoLive;
                        r.clientVersion   = window.yt.config_.INNERTUBE_CLIENT_VERSION|| r.clientVersion;
                        r.apiKey          = window.yt.config_.INNERTUBE_API_KEY       || r.apiKey;
                    }

                    // --- PO Token: direct botguard ---
                    r.poToken = '';
                    if (window.botguard && typeof window.botguard.invoke === 'function') {
                        try {
                            window.botguard.invoke(function(tok) { window._yt_potok = tok || ''; });
                        } catch(e) {}
                    }
                    if (window._yt_potok) r.poToken = window._yt_potok;

                    // --- PO Token: WEB_PLAYER_CONTEXT_CONFIGS ---
                    if (!r.poToken) {
                        try {
                            var cfg = (window.yt && window.yt.config_ && window.yt.config_.WEB_PLAYER_CONTEXT_CONFIGS) || {};
                            var key = Object.keys(cfg)[0];
                            if (key && cfg[key].poToken) r.poToken = cfg[key].poToken;
                        } catch(e) {}
                    }

                    // --- PO Token: ytInitialPlayerResponse ---
                    if (!r.poToken) {
                        try {
                            var ipr = window.ytInitialPlayerResponse;
                            if (ipr && ipr.attestation && ipr.attestation.playerAttestationRenderer) {
                                var par = ipr.attestation.playerAttestationRenderer;
                                if (par.botguardData && par.botguardData.integrityToken) {
                                    r.poToken = par.botguardData.integrityToken;
                                }
                            }
                        } catch(e) {}
                    }

                    // --- PO Token: scan ytcfg dump for any po_token key ---
                    if (!r.poToken) {
                        try {
                            var dump = JSON.stringify(window.ytcfg ? window.ytcfg.dump() : {});
                            var m = dump.match(/"po_token"\s*:\s*"([^"]+)"/);
                            if (!m) m = dump.match(/"poToken"\s*:\s*"([^"]+)"/);
                            if (m) r.poToken = m[1];
                        } catch(e) {}
                    }

                    // --- HLS Manifest URL (bonus - direct stream URL if available) ---
                    r.hlsManifestUrl = '';
                    try {
                        var ipr2 = window.ytInitialPlayerResponse;
                        if (ipr2 && ipr2.streamingData && ipr2.streamingData.hlsManifestUrl) {
                            r.hlsManifestUrl = ipr2.streamingData.hlsManifestUrl;
                        }
                    } catch(e) {}

                    // --- localStorage extras ---
                    try {
                        r.sessionToken = localStorage.getItem('yt-player-session-token') || '';
                    } catch(e) {}

                    return JSON.stringify(r);
                } catch(e) {
                    return JSON.stringify({ error: e.toString() });
                }
            })();
        """.trimIndent()
    }

    // ──────────────────────────────────────────────────────────────
    // DATA CLASS
    // ──────────────────────────────────────────────────────────────

    data class YouTubeTokens(
        val userAgent: String,
        val cookies: String,
        val visitorData: String,
        val visitorInfoLive: String,
        val poToken: String,
        val clientVersion: String,
        val apiKey: String,
        val hl: String,
        val gl: String,
        val hlsManifestUrl: String = "" // Bonus: direct HLS URL if page had it
    ) {
        val hasPoToken: Boolean       get() = poToken.isNotBlank()
        val hasCookies: Boolean       get() = cookies.isNotBlank()
        val hasVisitorData: Boolean   get() = visitorData.isNotBlank()
        val hasHlsManifest: Boolean   get() = hlsManifestUrl.isNotBlank()

        override fun toString(): String = buildString {
            appendLine("=== YouTubeTokens ===")
            appendLine("UA      : ${userAgent.take(80)}")
            appendLine("Cookies : ${if (hasCookies) "${cookies.length} chars" else "AUSENTE"}")
            appendLine("Visitor : ${if (hasVisitorData) "${visitorData.take(24)}..." else "AUSENTE"}")
            appendLine("PoToken : ${if (hasPoToken) "${poToken.take(24)}..." else "AUSENTE"}")
            appendLine("Version : $clientVersion | hl=$hl gl=$gl")
            appendLine("HLS URL : ${if (hasHlsManifest) "${hlsManifestUrl.take(40)}..." else "none"}")
        }
    }

    // ──────────────────────────────────────────────────────────────
    // PUBLIC API
    // ──────────────────────────────────────────────────────────────

    suspend fun fetchTokens(
        forceRefresh: Boolean = false,
        targetUrl: String? = null
    ): YouTubeTokens {
        // If we have a targetUrl (specific video), we ALWAYS do a new capture to sniff HLS
        val canUseCache = !forceRefresh && targetUrl == null
        
        if (canUseCache) {
            getCachedTokens()?.let {
                Log.d(TAG, "✓ Tokens em cache (válidos)")
                return it
            }
        }
        
        Log.d(TAG, "Capturando tokens via WebView (target: ${targetUrl ?: "home"})...")
        val deferred = CompletableDeferred<YouTubeTokens>()

        withContext(Dispatchers.Main) {
            startCapture(deferred, targetUrl ?: YOUTUBE_URL)
        }

        return try {
            withTimeout(WEBVIEW_TIMEOUT_MS + 8_000L) { deferred.await() }
                .also { tokens ->
                    Log.d(TAG, "Tokens capturados:\n$tokens")
                    // Only cache if it's the home page (general tokens)
                    if (targetUrl == null) cacheTokens(tokens)
                    notifyMainApp(tokens)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Timeout/erro na captura de tokens: ${e.message}")
            fallbackTokens()
        }
    }

    fun clearCache() {
        cacheFile.delete()
        Log.d(TAG, "Cache de tokens limpo")
    }

    // ──────────────────────────────────────────────────────────────
    // WEBVIEW CAPTURE
    // ──────────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun startCapture(deferred: CompletableDeferred<YouTubeTokens>, loadUrl: String) {
        val handler = Handler(Looper.getMainLooper())

        try {
            val webView    = WebView(context)
            val cookieMgr  = CookieManager.getInstance()

            cookieMgr.setAcceptCookie(true)
            cookieMgr.setAcceptThirdPartyCookies(webView, true)

            webView.settings.apply {
                javaScriptEnabled       = true
                domStorageEnabled       = true
                databaseEnabled         = true
                cacheMode               = WebSettings.LOAD_DEFAULT
                useWideViewPort         = true
                loadWithOverviewMode    = true
                // Disable media requirements so YouTube player initialises
                mediaPlaybackRequiresUserGesture = false
            }

            // Suppress console noise
            webView.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(msg: ConsoleMessage?): Boolean = true
            }

            // ── SNIFFING: capture HLS manifest BEFORE page finishes ──
            webView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val reqUrl = request?.url?.toString() ?: ""
                    // If we intercept an HLS manifest, complete early with partial tokens
                    if (!deferred.isCompleted &&
                        (reqUrl.contains("manifest.googlevideo.com") ||
                         (reqUrl.contains(".m3u8") && reqUrl.contains("googlevideo")))
                    ) {
                        Log.d(TAG, "HLS interceptado via sniffing: ${reqUrl.take(60)}")
                        // Keep loading to collect other tokens — just note the URL
                        handler.post {
                            view?.tag = reqUrl  // Store in tag for retrieval
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView?, pageUrl: String?) {
                    if (deferred.isCompleted) return
                    Log.d(TAG, "Página carregada: $pageUrl")

                    // Wait for ytcfg to be fully populated
                    handler.postDelayed({
                        if (deferred.isCompleted) return@postDelayed

                        view?.evaluateJavascript(EXTRACT_TOKENS_JS) { rawResult ->
                            handler.postDelayed({
                                if (deferred.isCompleted) return@postDelayed

                                // Re-evaluate after PO Token async callbacks had time to fire
                                view?.evaluateJavascript(EXTRACT_TOKENS_JS) { finalResult ->
                                    val ua      = WebSettings.getDefaultUserAgent(context)
                                    val cookies = cookieMgr.getCookie(YOUTUBE_URL) ?: ""
                                    val snoopedHls = view?.tag as? String ?: ""

                                    val tokens = parseTokens(
                                        raw       = finalResult ?: rawResult,
                                        ua        = ua,
                                        cookies   = cookies,
                                        extraHls  = snoopedHls
                                    )
                                    if (!deferred.isCompleted) {
                                        deferred.complete(tokens)
                                        view?.destroy()
                                    }
                                }
                            }, PO_TOKEN_WAIT_MS)
                        }
                    }, PAGE_SETTLE_DELAY_MS)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        Log.w(TAG, "Erro página principal: ${error?.description}")
                        // Don't fail here — partial load may still give us tokens
                    }
                }
            }

            webView.loadUrl(loadUrl)

            // Hard timeout — collect whatever we have
            handler.postDelayed({
                if (!deferred.isCompleted) {
                    Log.w(TAG, "Timeout forçado — coletando tokens parciais")
                    val ua      = WebSettings.getDefaultUserAgent(context)
                    val cookies = cookieMgr.getCookie(YOUTUBE_URL) ?: ""
                    val snoopedHls = webView.tag as? String ?: ""

                    webView.evaluateJavascript(EXTRACT_TOKENS_JS) { raw ->
                        val tokens = parseTokens(raw, ua, cookies, snoopedHls)
                        if (!deferred.isCompleted) {
                            deferred.complete(tokens)
                            webView.destroy()
                        }
                    }
                }
            }, WEBVIEW_TIMEOUT_MS)

        } catch (e: Exception) {
            Log.e(TAG, "Erro crítico no WebView: ${e.message}", e)
            if (!deferred.isCompleted) deferred.complete(fallbackTokens())
        }
    }

    // ──────────────────────────────────────────────────────────────
    // PARSING
    // ──────────────────────────────────────────────────────────────

    private fun parseTokens(
        raw: String?,
        ua: String,
        cookies: String,
        extraHls: String = ""
    ): YouTubeTokens {
        var visitorData    = ""
        var visitorInfoLive= ""
        var poToken        = ""
        var clientVersion  = ""
        var apiKey         = ""
        var hl             = "pt"
        var gl             = "BR"
        var hlsManifestUrl = extraHls

        raw?.let { r ->
            // WebView evaluateJavascript returns JSON string wrapped in outer quotes
            val cleaned = r.trim()
                .let { if (it.startsWith("\"") && it.endsWith("\"")) it.drop(1).dropLast(1) else it }
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
            try {
                val json = JSONObject(if (cleaned.startsWith("{")) cleaned else "{}")
                visitorData     = json.optString("visitorData")
                visitorInfoLive = json.optString("visitorInfoLive")
                poToken         = json.optString("poToken")
                clientVersion   = json.optString("clientVersion")
                apiKey          = json.optString("apiKey")
                hl              = json.optString("hl").ifBlank { "pt" }
                gl              = json.optString("gl").ifBlank { "BR" }
                val jsHls       = json.optString("hlsManifestUrl")
                if (jsHls.isNotBlank()) hlsManifestUrl = jsHls
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao parsear JS result: ${e.message} | raw=${cleaned.take(100)}")
            }
        }

        Log.d(TAG, "  visitorData : ${if (visitorData.isNotBlank()) "✓" else "✗"}")
        Log.d(TAG, "  poToken     : ${if (poToken.isNotBlank()) "✓" else "✗"}")
        Log.d(TAG, "  cookies     : ${if (cookies.isNotBlank()) "✓" else "✗"}")
        Log.d(TAG, "  HLS sniffed : ${if (hlsManifestUrl.isNotBlank()) "✓" else "✗"}")

        return YouTubeTokens(
            userAgent       = ua,
            cookies         = cookies,
            visitorData     = visitorData,
            visitorInfoLive = visitorInfoLive,
            poToken         = poToken,
            clientVersion   = clientVersion,
            apiKey          = apiKey,
            hl              = hl,
            gl              = gl,
            hlsManifestUrl  = hlsManifestUrl
        )
    }

    // ──────────────────────────────────────────────────────────────
    // CACHE
    // ──────────────────────────────────────────────────────────────

    private val cacheFile: File by lazy {
        File(context.cacheDir, "yt_webview_tokens.json")
    }

    private fun cacheTokens(tokens: YouTubeTokens) {
        try {
            JSONObject().apply {
                put("userAgent",       tokens.userAgent)
                put("cookies",         tokens.cookies)
                put("visitorData",     tokens.visitorData)
                put("visitorInfoLive", tokens.visitorInfoLive)
                put("poToken",         tokens.poToken)
                put("clientVersion",   tokens.clientVersion)
                put("apiKey",          tokens.apiKey)
                put("hl",              tokens.hl)
                put("gl",              tokens.gl)
                put("hlsManifestUrl",  tokens.hlsManifestUrl)
                put("timestamp",       System.currentTimeMillis())
            }.let { cacheFile.writeText(it.toString()) }
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao salvar cache: ${e.message}")
        }
    }

    private fun getCachedTokens(): YouTubeTokens? {
        if (!cacheFile.exists()) return null
        return try {
            val json = JSONObject(cacheFile.readText())
            val age  = System.currentTimeMillis() - json.getLong("timestamp")
            if (age > CACHE_TTL_MS) return null
            YouTubeTokens(
                userAgent       = json.optString("userAgent"),
                cookies         = json.optString("cookies"),
                visitorData     = json.optString("visitorData"),
                visitorInfoLive = json.optString("visitorInfoLive"),
                poToken         = json.optString("poToken"),
                clientVersion   = json.optString("clientVersion"),
                apiKey          = json.optString("apiKey"),
                hl              = json.optString("hl", "pt"),
                gl              = json.optString("gl", "BR"),
                hlsManifestUrl  = json.optString("hlsManifestUrl")
            )
        } catch (e: Exception) { null }
    }

    // ──────────────────────────────────────────────────────────────
    // FALLBACK / NOTIFY
    // ──────────────────────────────────────────────────────────────

    private fun fallbackTokens() = YouTubeTokens(
        userAgent       = WebSettings.getDefaultUserAgent(context),
        cookies         = CookieManager.getInstance().getCookie(YOUTUBE_URL) ?: "",
        visitorData     = "", visitorInfoLive = "", poToken = "",
        clientVersion   = "", apiKey = "", hl = "pt", gl = "BR"
    )

    private fun notifyMainApp(tokens: YouTubeTokens) {
        try {
            android.content.Intent("com.m3u.IDENTITY_UPDATE").also { intent ->
                intent.putExtra("user_agent",   tokens.userAgent)
                intent.putExtra("cookies",      tokens.cookies)
                intent.putExtra("po_token",     tokens.poToken)
                intent.putExtra("visitor_data", tokens.visitorData)
                intent.setPackage("com.m3u.universal")
                context.sendBroadcast(intent)
            }
            Log.d(TAG, "Identidade enviada ao App Universal")
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao enviar broadcast: ${e.message}")
        }
    }
}
