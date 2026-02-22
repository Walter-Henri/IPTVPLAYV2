package com.m3u.universal.ui.player

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.webkit.JavascriptInterface
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.m3u.core.Contracts
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.data.security.UrlPolicy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

import org.json.JSONObject
import com.google.gson.Gson

@AndroidEntryPoint
class WebPlayerActivity : ComponentActivity() {

    @Inject
    lateinit var settings: com.m3u.core.architecture.preferences.Settings

    @Inject
    lateinit var channelRepository: ChannelRepository

    private lateinit var webView: WebView
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private val fullscreenContainer: FrameLayout by lazy { FrameLayout(this) }

    private var currentChannelId: Int = -1
    private var adjacentJob: Job? = null
    private var nextId: Int? = null
    private var prevId: Int? = null
    private var currentHeaders: Map<String, String> = emptyMap()

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val channelId = intent.getIntExtra(Contracts.PLAYER_SHORTCUT_CHANNEL_ID, -1)

        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                useWideViewPort = true
                loadWithOverviewMode = true
                allowFileAccess = true
                allowContentAccess = true
                javaScriptCanOpenWindowsAutomatically = false
                safeBrowsingEnabled = true
                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            }

            addJavascriptInterface(AndroidBackend(), "AndroidBackend")

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    return url?.let { !UrlPolicy.isSafe(it) } ?: true
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?
                ): android.webkit.WebResourceResponse? {
                    val url = request?.url?.toString() ?: return null
                    // Intercepta requisições de mídia (M3U8, TS, KEY) para injetar headers
                    if (url.contains(".m3u8") || url.contains(".ts") || url.contains(".key") || url.contains("googlevideo.com")) {
                        try {
                            val client = okhttp3.OkHttpClient.Builder()
                                .followRedirects(true)
                                .followSslRedirects(true)
                                .build()

                            val reqBuilder = okhttp3.Request.Builder().url(url)
                            
                            // Adiciona headers customizados (Referer, Origin, etc)
                            currentHeaders.forEach { (k, v) -> 
                                if (!k.equals("User-Agent", ignoreCase = true)) {
                                    reqBuilder.addHeader(k, v)
                                }
                            }
                            // Garante o User-Agent correto
                            val ua = currentHeaders["User-Agent"] ?: settings.userAgentString
                            reqBuilder.header("User-Agent", ua)

                            val response = client.newCall(reqBuilder.build()).execute()
                            
                            if (!response.isSuccessful) return null

                            val contentType = response.header("Content-Type")?.split(";")?.first() ?: "application/octet-stream"
                            val encoding = response.header("Content-Encoding") ?: "utf-8"
                            
                            val responseHeaders = mutableMapOf<String, String>()
                            response.headers.names().forEach { name ->
                                responseHeaders[name] = response.header(name) ?: ""
                            }
                            // Permite CORS na resposta proxy
                            responseHeaders["Access-Control-Allow-Origin"] = "*"

                            return android.webkit.WebResourceResponse(
                                contentType,
                                encoding,
                                200,
                                "OK",
                                responseHeaders,
                                response.body?.byteStream()
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("WebPlayer", "Erro ao interceptar: ${e.message}")
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                @Suppress("DEPRECATION")
                override fun onReceivedSslError(
                    view: WebView?,
                    handler: android.webkit.SslErrorHandler?,
                    error: android.net.http.SslError?
                ) {
                    // Se a configuração de Trust All SSL estiver ativa, ignorar o erro
                    if (this@WebPlayerActivity.settings.alwaysTrustAllSSL.value) {
                        handler?.proceed()
                    } else {
                        super.onReceivedSslError(view, handler, error)
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?,
                    error: android.webkit.WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    android.util.Log.e("WebPlayer", "Erro de recurso: ${error?.description}")
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (url?.startsWith("file:///android_asset/webplayer/") == true && channelId != -1) {
                        loadChannel(channelId)
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?,
                    errorResponse: android.webkit.WebResourceResponse?
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    val statusCode = errorResponse?.statusCode ?: -1
                    if (statusCode == 404 || statusCode == 403) {
                        val reqUrl = request?.url?.toString() ?: "unknown"
                        android.util.Log.e("WebPlayer", "HTTP Error $statusCode loading $reqUrl")
                        if (reqUrl.contains(".m3u8") || reqUrl.contains("playlist")) {
                             runOnUiThread {
                                 android.widget.Toast.makeText(this@WebPlayerActivity, "Erro de Reprodução: Stream não encontrado ou bloqueado ($statusCode)", android.widget.Toast.LENGTH_LONG).show()
                             }
                        }
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (customView != null) {
                        callback?.onCustomViewHidden()
                        return
                    }
                    customView = view
                    customViewCallback = callback
                    
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    
                    (window.decorView as FrameLayout).addView(
                        fullscreenContainer,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                    fullscreenContainer.addView(
                        view,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                    webView.visibility = View.GONE
                    
                    val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
                    windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                }

                override fun onHideCustomView() {
                    if (customView == null) return
                    
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    
                    (window.decorView as FrameLayout).removeView(fullscreenContainer)
                    fullscreenContainer.removeView(customView)
                    customView = null
                    customViewCallback?.onCustomViewHidden()
                    
                    webView.visibility = View.VISIBLE
                    
                    val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
                    windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }

        setContentView(webView)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (customView != null) {
                    webView.webChromeClient?.onHideCustomView()
                } else if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })

        // Carregar o player local
        webView.loadUrl("file:///android_asset/webplayer/player.html")
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                android.view.KeyEvent.KEYCODE_BACK -> {
                    onBackPressedDispatcher.onBackPressed()
                    return true
                }
                android.view.KeyEvent.KEYCODE_DPAD_UP,
                android.view.KeyEvent.KEYCODE_CHANNEL_UP -> {
                    nextId?.let { loadChannel(it) }
                    return true
                }
                android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                android.view.KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    prevId?.let { loadChannel(it) }
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun loadChannel(id: Int) {
        currentChannelId = id
        adjacentJob?.cancel()
        adjacentJob = lifecycleScope.launch {
            val channel = channelRepository.get(id) ?: return@launch
            
            val originalUrl = channel.url
            val options = originalUrl.readKodiUrlOptions()
            val safeUrl = originalUrl.stripKodiOptions().replace("'", "\\'")
            
            val headersMap = mutableMapOf<String, String>()
            options.forEach { (key, value) ->
                if (!value.isNullOrBlank()) {
                    headersMap[key] = value
                }
            }
            
            // Armazena headers mapeados da URL (Kodi Format)
            // Estes headers foram injetados pela extensão no formato url|Key=Value
            currentHeaders = headersMap.toMap()

            // INJEÇÃO DE COOKIES NATIVA: Crucial para o player nativo do WebView e HLS.js
            // Diferente do XHR (JS), o CookieManager consegue injetar cookies persistentes.
            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, true)
            
            headersMap["Cookie"]?.let { cookieString ->
                android.util.Log.d("WebPlayer", "Injetando cookies para: $safeUrl")
                cookieManager.setCookie(safeUrl, cookieString)
                cookieManager.flush()
            }

            // Configurar User-Agent no WebView (Nativo)
            val userAgent = headersMap["User-Agent"] 
                ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            
            webView.settings.userAgentString = userAgent
            
            // REMOVER User-Agent dos headers customizados passados ao JS
            // Isso evita que o HLS.js tente enviar um header 'User-Agent' via XHR,
            // o que geralmente causa erro de CORS (Preflight) em servidores IPTV.
            // O WebView já envia o User-Agent correto nativamente.
            headersMap.remove("User-Agent")

            val headersJson = JSONObject(headersMap as Map<*, *>).toString().replace("'", "\\'")
            
            webView.evaluateJavascript("AndroidPlayer.loadUrl('$safeUrl', '$headersJson')", null)

            channelRepository.observeAdjacentChannels(id, channel.playlistUrl, channel.category)
                .collectLatest { adjacent ->
                    nextId = adjacent.nextId
                    prevId = adjacent.prevId
                }
        }
    }

    private fun String.readKodiUrlOptions(): Map<String, String?> {
        val index = this.indexOf('|')
        if (index == -1) return emptyMap()
        val options = this.drop(index + 1).split("&")
        return options
            .filter { it.isNotBlank() }
            .associate {
                val splitIndex = it.indexOf('=')
                if (splitIndex != -1) {
                    val key = it.substring(0, splitIndex)
                    val value = it.substring(splitIndex + 1)
                    key to value
                } else {
                    it to null
                }
            }
    }

    private fun String.stripKodiOptions(): String {
        val index = this.indexOf('|')
        return if (index == -1) this else this.substring(0, index)
    }

    private fun getExtensionCookies(): Map<String, String> = emptyMap()

    private fun getExtensionUserAgent(): String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private inner class AndroidBackend {
        @JavascriptInterface
        fun next() {
            nextId?.let { id ->
                runOnUiThread { loadChannel(id) }
            }
        }

        @JavascriptInterface
        fun previous() {
            prevId?.let { id ->
                runOnUiThread { loadChannel(id) }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        webView.pauseTimers()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.destroy()
    }
}
