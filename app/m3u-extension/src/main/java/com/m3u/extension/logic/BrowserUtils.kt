package com.m3u.extension.logic

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.m3u.extension.util.LogManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

/**
 * BrowserUtils
 *
 * WebView-based utility that acts as a "native engine" for stream extraction.
 * Mirrors the approach used by the working older APK version: network sniffing
 * via shouldInterceptRequest captured HLS URLs before the page even finished loading.
 *
 * Extraction strategies (applied in order within the same WebView session):
 *   1. NETWORK SNIFFING — intercepts HLS manifest requests at the network layer.
 *   2. JS INJECTION     — reads ytInitialPlayerResponse.streamingData.hlsManifestUrl.
 *   3. JS VIDEO TAG     — reads <video> src directly from the DOM.
 */
object BrowserUtils {
    private const val TAG = "BrowserUtils"

    @Volatile
    private var cachedUserAgent: String? = null

    // ── HLS URL Patterns ──────────────────────────────────────────

    /** Returns true if the URL looks like a real HLS manifest (not analytics/stats). */
    private fun isHlsManifest(url: String): Boolean {
        if (url.contains("youtube.com/api/stats") ||
            url.contains("/api/stats/") ||
            url.contains("doubleclick") ||
            url.contains("googlesyndication")
        ) return false

        return url.contains("manifest.googlevideo.com") ||
               (url.contains("googlevideo.com") && url.contains(".m3u8")) ||
               (url.endsWith(".m3u8", ignoreCase = true)) ||
               (url.contains(".m3u8?")) ||
               url.endsWith("manifest", ignoreCase = true)
    }

    // ── Main Public API ───────────────────────────────────────────

    /**
     * Extracts the HLS stream URL from a YouTube page using a hidden WebView.
     * This is the "Gold Standard" method — same as the old native engine.
     *
     * @param url  YouTube channel/video/live URL
     */
    suspend fun extractHlsWithWebView(context: Context, url: String): String? {
        // Short-circuit for direct m3u8 links
        if (url.endsWith(".m3u8", ignoreCase = true) || url.contains(".m3u8?")) return url

        LogManager.debug("Abrindo WebView para sniffing: ${url.take(40)}...")

        val deferred = CompletableDeferred<String?>()

        Handler(Looper.getMainLooper()).post {
            try {
                setupWebViewCapture(context, url, deferred)
            } catch (e: Exception) {
                LogManager.error("WebView setup falhou: ${e.message}", "BROWSER")
                if (!deferred.isCompleted) deferred.complete(null)
            }
        }

        return try {
            withTimeout(28_000L) { deferred.await() }
        } catch (_: Exception) {
            LogManager.warn("Timeout no WebView sniffing", "BROWSER")
            null
        }
    }

    // ── WebView Setup ─────────────────────────────────────────────

    @Suppress("SetJavaScriptEnabled")
    private fun setupWebViewCapture(
        context: Context,
        url: String,
        deferred: CompletableDeferred<String?>
    ) {
        val handler      = Handler(Looper.getMainLooper())
        val webView      = WebView(context)
        val cookieMgr    = CookieManager.getInstance()
        val ua           = getRealUserAgent(context)

        cookieMgr.setAcceptCookie(true)
        cookieMgr.setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled                = true
            domStorageEnabled                = true
            databaseEnabled                  = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode                 = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString                  = ua
            cacheMode                        = WebSettings.LOAD_DEFAULT
        }

        // Suppress console noise from YouTube's JS
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(msg: ConsoleMessage?): Boolean = true
        }

        webView.webViewClient = object : WebViewClient() {

            // ── Strategy 1: Network Sniffing ────────────────────────
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val reqUrl = request?.url?.toString() ?: ""
                if (!deferred.isCompleted && isHlsManifest(reqUrl)) {
                    LogManager.info("🎯 HLS interceptado via sniffing: ${reqUrl.take(70)}", "BROWSER")
                    handler.post {
                        if (!deferred.isCompleted) {
                            deferred.complete(reqUrl)
                            view?.destroy()
                        }
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            // ── Strategy 2 & 3: JS Injection on page load ─────────
            override fun onPageFinished(view: WebView?, pageUrl: String?) {
                if (deferred.isCompleted) return
                LogManager.debug("Página carregada — injetando JS extrator", "BROWSER")

                val js = """
                    (function() {
                        try {
                            // Strategy A: ytInitialPlayerResponse (most reliable)
                            if (window.ytInitialPlayerResponse) {
                                var sd = window.ytInitialPlayerResponse.streamingData;
                                if (sd && sd.hlsManifestUrl) return sd.hlsManifestUrl;
                            }

                            // Strategy B: ytplayer.config (older YouTube layout)
                            if (window.ytplayer && window.ytplayer.config) {
                                var args = window.ytplayer.config.args;
                                if (args && args.raw_player_response) {
                                    var hls = args.raw_player_response.streamingData && args.raw_player_response.streamingData.hlsManifestUrl;
                                    if (hls) return hls;
                                }
                            }

                            // Strategy C: Raw HTML scan for hlsManifestUrl
                            var html = document.documentElement.innerHTML;
                            var patterns = [
                                /\"hlsManifestUrl\":\"([^\"]+)\"/,
                                /hlsManifestUrl\":\"([^\"]+)\"/
                            ];
                            for (var i = 0; i < patterns.length; i++) {
                                var m = html.match(patterns[i]);
                                if (m) return m[1].replace(/\\\\/g, '');
                            }

                            // Strategy D: <video> tag src
                            var vids = document.getElementsByTagName('video');
                            for (var i = 0; i < vids.length; i++) {
                                if (vids[i].src && vids[i].src.indexOf('.m3u8') !== -1) {
                                    return vids[i].src;
                                }
                            }
                        } catch(e) {}
                        return null;
                    })();
                """.trimIndent()

                view?.evaluateJavascript(js) { result ->
                    val hlsUrl = result?.trim('"')?.takeIf { it != "null" && it.isNotBlank() }
                    if (hlsUrl != null && !deferred.isCompleted) {
                        LogManager.info("✓ HLS via injeção JS: ${hlsUrl.take(60)}", "BROWSER")
                        deferred.complete(hlsUrl)
                        view?.destroy()
                    }
                }
            }
        }

        Log.d(TAG, "Carregando: $url")
        webView.loadUrl(url)

        // Hard timeout: grab video tag as last resort, then give up
        handler.postDelayed({
            if (!deferred.isCompleted) {
                LogManager.warn("Timeout WebView — última tentativa via <video>", "BROWSER")
                webView.evaluateJavascript(
                    "document.getElementsByTagName('video')[0]?.src"
                ) { src ->
                    val clean = src?.trim('"')?.takeIf {
                        it != "null" && it.isNotBlank() && it.contains(".m3u8")
                    }
                    if (!deferred.isCompleted) {
                        deferred.complete(clean)
                        webView.destroy()
                    }
                }
            }
        }, 22_000L)
    }

    // ── User-Agent ─────────────────────────────────────────────────

    fun getRealUserAgent(context: Context): String {
        cachedUserAgent?.let { return it }
        return try {
            val ua = WebSettings.getDefaultUserAgent(context)
            cachedUserAgent = ua
            ua
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao obter UA real", e)
            "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        }
    }

    // ── YouTube Session Warmup ─────────────────────────────────────

    /**
     * Opens YouTube in a WebView to obtain session cookies.
     * Called before extracting channels so yt-dlp has fresh cookies.
     */
    suspend fun warmupYouTubeSession(context: Context): Map<String, String> {
        val deferred = CompletableDeferred<Map<String, String>>()

        Handler(Looper.getMainLooper()).post {
            try {
                val webView   = WebView(context)
                val cookieMgr = CookieManager.getInstance()

                cookieMgr.setAcceptCookie(true)
                cookieMgr.setAcceptThirdPartyCookies(webView, true)

                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    userAgentString   = getRealUserAgent(context)
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        val cookieStr = cookieMgr.getCookie("https://www.youtube.com") ?: ""
                        val cookieMap = parseCookieString(cookieStr)
                        LogManager.debug("Warmup OK — ${cookieMap.size} cookies", "BROWSER")
                        if (!deferred.isCompleted) {
                            deferred.complete(cookieMap)
                            view?.destroy()
                        }
                    }
                }

                webView.loadUrl("https://www.youtube.com")
            } catch (e: Exception) {
                LogManager.error("Warmup falhou: ${e.message}", "BROWSER")
                if (!deferred.isCompleted) deferred.complete(emptyMap())
            }
        }

        return try {
            withTimeout(15_000L) { deferred.await() }
        } catch (_: Exception) {
            LogManager.warn("Timeout no warmup", "BROWSER")
            emptyMap()
        }
    }

    private fun parseCookieString(raw: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        raw.split(";").forEach { pair ->
            val parts = pair.trim().split("=", limit = 2)
            if (parts.size == 2) map[parts[0].trim()] = parts[1].trim()
        }
        return map
    }
}
