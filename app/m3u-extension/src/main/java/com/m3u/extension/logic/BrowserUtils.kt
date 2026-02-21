package com.m3u.extension.logic

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.m3u.extension.util.LogManager
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.TimeUnit

/**
 * BrowserUtils - Utilitários para lidar com WebView, User-Agent real e Cookies.
 */
object BrowserUtils {
    private const val TAG = "BrowserUtils"
    private var cachedUserAgent: String? = null

    /**
     * Extrai o link HLS (M3U8) usando um navegador real (WebView) em background.
     * Isso é o "Standard de Ouro" contra bloqueios, pois usa o motor Chromium real.
     * 
     * NOVIDADE: Adicionado Sniffing de Rede para captura instantânea.
     */
    suspend fun extractHlsWithWebView(context: Context, url: String): String? {
        LogManager.debug("Abrindo navegador em background para: ${url.take(30)}...")
        // Se já for um arquivo m3u8, não precisa de navegador
        if (url.endsWith(".m3u8", true) || url.contains(".m3u8?")) return url
        
        val deferred = CompletableDeferred<String?>()
        
        Handler(Looper.getMainLooper()).post {
            try {
                val webView = WebView(context)
                val ua = getRealUserAgent(context)
                
                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    userAgentString = ua
                }

                webView.webViewClient = object : WebViewClient() {
                    // 1. SNIFFING DE REDE: Captura o m3u8/mpd antes mesmo da página terminar de carregar
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?
                    ): android.webkit.WebResourceResponse? {
                        val requestUrl = request?.url?.toString() ?: ""
                        
                        // Verifica se é uma URL válida de stream (não URLs de estatísticas ou analytics)
                        val isValidStreamUrl = !deferred.isCompleted && (
                            // URLs que terminam com .m3u8 ou contêm .m3u8? (com query params)
                            (requestUrl.endsWith(".m3u8") || requestUrl.contains(".m3u8?")) ||
                            // URLs de manifesto do YouTube que terminam com /file/index.m3u8
                            (requestUrl.contains("manifest.googlevideo.com") && requestUrl.endsWith("/file/index.m3u8")) ||
                            // URLs DASH (.mpd)
                            requestUrl.endsWith(".mpd") ||
                            // URLs de manifesto gerais que terminam com manifest
                            requestUrl.endsWith("manifest")
                        )
                        
                        if (isValidStreamUrl) {
                            LogManager.info("Link interceptado via rede (Sniffing)!", "BROWSER")
                            LogManager.debug("Tipo detectado: ${if (requestUrl.contains(".m3u8")) "M3U8" else if (requestUrl.contains(".mpd")) "MPD" else "MANIFEST"}", "BROWSER")
                            deferred.complete(requestUrl)
                            
                            // Limpa WebView em background
                            Handler(Looper.getMainLooper()).post { view?.destroy() }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (deferred.isCompleted) return
                        
                        LogManager.debug("Página carregada. Injetando extratores...", "BROWSER")
                        
                        val js = """
                            (function() {
                                try {
                                    // Estratégia A: Metadados Internos do YouTube
                                    if (window.ytInitialPlayerResponse && window.ytInitialPlayerResponse.streamingData) {
                                        var data = window.ytInitialPlayerResponse.streamingData;
                                        if (data.hlsManifestUrl) return data.hlsManifestUrl;
                                    }
                                    
                                    // Estratégia B: Configurações do Player
                                    if (window.ytplayer && window.ytplayer.config && window.ytplayer.config.args) {
                                        var args = window.ytplayer.config.args;
                                        if (args.raw_player_response && args.raw_player_response.streamingData) {
                                            var hls = args.raw_player_response.streamingData.hlsManifestUrl;
                                            if (hls) return hls;
                                        }
                                    }

                                    // Estratégia C: Busca Brutal no HTML
                                    var html = document.documentElement.innerHTML;
                                    var patterns = [
                                        /hlsManifestUrl":"([^"]+)"/,
                                        /m3u8":"([^"]+)"/,
                                        /url":"([^"]+\.m3u8[^"]*)"/
                                    ];
                                    
                                    for (var i = 0; i < patterns.size; i++) {
                                        var match = html.match(patterns[i]);
                                        if (match) return match[1].replace(/\\/g, '');
                                    }
                                    
                                    // Estratégia D: Capturar de tags <video>
                                    var videos = document.getElementsByTagName('video');
                                    for (var i = 0; i < videos.length; i++) {
                                        if (videos[i].src && videos[i].src.includes('.m3u8')) return videos[i].src;
                                    }
                                } catch (e) {}
                                return null;
                            })();
                        """.trimIndent()
 
                        view?.evaluateJavascript(js) { result ->
                            val hlsUrl = result?.trim('"')?.takeIf { it != "null" && it.isNotBlank() }
                            if (hlsUrl != null && !deferred.isCompleted) {
                                LogManager.info("Link extraído via Injeção JS!", "BROWSER")
                                deferred.complete(hlsUrl)
                                view?.destroy()
                            }
                        }
                    }
                }

                Log.d(TAG, "Navegador Pro-Max acessando: $url")
                webView.loadUrl(url)
                
                // Timeout de segurança local para fechar a WebView se nada for achado
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!deferred.isCompleted) {
                        LogManager.warn("Navegador atingiu timeout interno. Tentando captura final...", "BROWSER")
                        webView.evaluateJavascript("document.getElementsByTagName('video')[0]?.src") { finalSrc ->
                            val src = finalSrc?.trim('"')?.takeIf { it != "null" && it.isNotBlank() && it.contains(".m3u8") }
                            deferred.complete(src) 
                            webView.destroy()
                        }
                    }
                }, 20000)
                
            } catch (e: Exception) {
                LogManager.error("Erro no navegador interno: ${e.message}", "BROWSER")
                deferred.complete(null)
            }
        }

        return try {
            kotlinx.coroutines.withTimeout(25000) {
                deferred.await()
            }
        } catch (e: Exception) {
            LogManager.warn("Timeout total do motor de navegador", "BROWSER")
            null
        }
    }

    /**
     * Obtém o User-Agent real do dispositivo usando o motor do WebView.
     */
    fun getRealUserAgent(context: Context): String {
        cachedUserAgent?.let { return it }
        return try {
            val ua = WebSettings.getDefaultUserAgent(context)
            cachedUserAgent = ua
            ua
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao obter UA real, usando fallback", e)
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"
        }
    }

    /**
     * "Aquece" uma sessão do YouTube no WebView para obter cookies válidos.
     */
    suspend fun warmupYouTubeSession(context: Context): Map<String, String> {
        val deferred = CompletableDeferred<Map<String, String>>()
        
        Handler(Looper.getMainLooper()).post {
            try {
                val webView = WebView(context)
                val cookieManager = CookieManager.getInstance()
                
                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    userAgentString = getRealUserAgent(context)
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        val cookies = cookieManager.getCookie(url)
                        LogManager.debug("Sessão aquecida! Cookies obtidos.", "BROWSER")
                        
                        val cookieMap = mutableMapOf<String, String>()
                        cookies?.split(";")?.forEach { pair ->
                            val parts = pair.split("=")
                            if (parts.size >= 2) {
                                cookieMap[parts[0].trim()] = parts[1].trim()
                            }
                        }
                        deferred.complete(cookieMap)
                        view?.destroy()
                    }
                }
 
                LogManager.debug("Aquecendo sessão do motor no WebView...", "BROWSER")
                webView.loadUrl("https://www.youtube.com")
            } catch (e: Exception) {
                LogManager.error("Erro no warmup do WebView: ${e.message}", "BROWSER")
                deferred.complete(emptyMap())
            }
        }

        return try {
            kotlinx.coroutines.withTimeout(15000) {
                deferred.await()
            }
        } catch (e: Exception) {
            LogManager.warn("Timeout no warmup do motor do navegador", "BROWSER")
            emptyMap()
        }
    }
}
