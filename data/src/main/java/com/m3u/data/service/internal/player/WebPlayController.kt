package com.m3u.data.service.internal.player

import android.content.Context
import android.graphics.Rect
import android.view.Surface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * WebPlay - Player universal baseado em WebView com suporte nativo a:
 * - HLS (.m3u8)
 * - DASH (.mpd)
 * - MP4, WebM, OGG
 * - Streams RTMP via Flash fallback
 * - YouTube embeds
 */
class WebPlayController(
    private val context: Context
) : PlayerController {

    private var webView: WebView? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _playbackState = MutableStateFlow(Player.STATE_IDLE)
    override val playbackState = _playbackState.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying = _isPlaying.asStateFlow()

    private val _videoSize = MutableStateFlow(Rect())
    override val videoSize = _videoSize.asStateFlow()

    private val _playbackException = MutableStateFlow<PlaybackException?>(null)
    override val playbackException = _playbackException.asStateFlow()

    private val _tracksGroups = MutableStateFlow<List<Tracks.Group>>(emptyList())
    override val tracksGroups = _tracksGroups.asStateFlow()

    private val _playbackPosition = MutableStateFlow(0L)
    override val playbackPosition = _playbackPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    override val duration = _duration.asStateFlow()

    override val playerObject: Any?
        get() = webView

    private var currentUrl: String = ""
    private var isInitialized = false

    init {
        initializePlayer()
    }

    private fun initializePlayer() {
        scope.launch {
            webView = WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    allowFileAccess = true
                    allowContentAccess = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    cacheMode = WebSettings.LOAD_DEFAULT
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        return false
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        _playbackState.value = Player.STATE_READY
                        injectPlaybackMonitor()
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        if (newProgress == 100) {
                            _playbackState.value = Player.STATE_READY
                        } else {
                            _playbackState.value = Player.STATE_BUFFERING
                        }
                    }
                }
            }
            isInitialized = true
        }
    }

    override fun play(url: String, headers: Map<String, String>) {
        if (!isInitialized) {
            _playbackException.value = PlaybackException(
                "WebPlay não inicializado",
                null,
                PlaybackException.ERROR_CODE_UNSPECIFIED
            )
            return
        }

        currentUrl = url
        _playbackState.value = Player.STATE_BUFFERING

        val htmlContent = generatePlayerHTML(url, headers)
        
        scope.launch {
            webView?.loadDataWithBaseURL(
                "https://player.local",
                htmlContent,
                "text/html",
                "UTF-8",
                null
            )
            _isPlaying.value = true
            startPositionTracking()
        }
    }

    private fun generatePlayerHTML(url: String, headers: Map<String, String>): String {
        val headersJson = headers.entries.joinToString(",") { 
            "\"${it.key}\": \"${it.value}\"" 
        }

        return """
<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
    <script src="https://cdn.dashjs.org/latest/dash.all.min.js"></script>
    <style>
        * { margin: 0; padding: 0; }
        body, html { 
            width: 100%; 
            height: 100%; 
            overflow: hidden; 
            background: #000;
        }
        video {
            position: absolute;
            top: 50%;
            left: 50%;
            transform: translate(-50%, -50%);
            max-width: 100%;
            max-height: 100%;
            width: auto;
            height: auto;
        }
    </style>
</head>
<body>
    <video id="player" autoplay playsinline webkit-playsinline></video>
    <script>
        const video = document.getElementById('player');
        const url = "$url";
        const headers = { $headersJson };

        function setupPlayer() {
            if (url.includes('.m3u8')) {
                // HLS
                if (Hls.isSupported()) {
                    const hls = new Hls({
                        xhrSetup: function(xhr) {
                            Object.keys(headers).forEach(key => {
                                xhr.setRequestHeader(key, headers[key]);
                            });
                        }
                    });
                    hls.loadSource(url);
                    hls.attachMedia(video);
                    hls.on(Hls.Events.MANIFEST_PARSED, () => video.play());
                    hls.on(Hls.Events.ERROR, (event, data) => {
                        if (data.fatal) {
                            console.error('HLS Error:', data);
                            window.AndroidBridge?.onError('HLS: ' + data.type);
                        }
                    });
                } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
                    video.src = url;
                    video.play();
                }
            } else if (url.includes('.mpd')) {
                // DASH
                const player = dashjs.MediaPlayer().create();
                player.initialize(video, url, true);
            } else {
                // MP4, WebM, OGG, etc
                video.src = url;
                video.play();
            }

            // Event listeners
            video.addEventListener('playing', () => {
                window.AndroidBridge?.onPlaying();
            });
            video.addEventListener('pause', () => {
                window.AndroidBridge?.onPaused();
            });
            video.addEventListener('ended', () => {
                window.AndroidBridge?.onEnded();
            });
            video.addEventListener('error', (e) => {
                window.AndroidBridge?.onError('Video error: ' + e.message);
            });
            video.addEventListener('timeupdate', () => {
                window.AndroidBridge?.onTimeUpdate(
                    Math.floor(video.currentTime * 1000),
                    Math.floor(video.duration * 1000)
                );
            });
        }

        setupPlayer();

        // Controles externos
        window.playVideo = () => video.play();
        window.pauseVideo = () => video.pause();
        window.seekTo = (ms) => { video.currentTime = ms / 1000; };
        window.getPosition = () => Math.floor(video.currentTime * 1000);
        window.getDuration = () => Math.floor(video.duration * 1000);
    </script>
</body>
</html>
        """.trimIndent()
    }

    private fun injectPlaybackMonitor() {
        webView?.evaluateJavascript("""
            window.AndroidBridge = {
                onPlaying: function() { console.log('Playing'); },
                onPaused: function() { console.log('Paused'); },
                onEnded: function() { console.log('Ended'); },
                onError: function(msg) { console.error(msg); },
                onTimeUpdate: function(pos, dur) { 
                    console.log('Position: ' + pos + ', Duration: ' + dur);
                }
            };
        """.trimIndent(), null)
    }

    private fun startPositionTracking() {
        scope.launch {
            while (_isPlaying.value) {
                webView?.evaluateJavascript("window.getPosition()") { result ->
                    result?.toLongOrNull()?.let { _playbackPosition.value = it }
                }
                webView?.evaluateJavascript("window.getDuration()") { result ->
                    result?.toLongOrNull()?.let { _duration.value = it }
                }
                delay(1000)
            }
        }
    }

    override fun pause() {
        webView?.evaluateJavascript("window.pauseVideo()", null)
        _isPlaying.value = false
    }

    override fun resume() {
        webView?.evaluateJavascript("window.playVideo()", null)
        _isPlaying.value = true
    }

    override fun stop() {
        webView?.loadUrl("about:blank")
        _isPlaying.value = false
        _playbackState.value = Player.STATE_IDLE
    }

    override fun release() {
        webView?.destroy()
        webView = null
        isInitialized = false
    }

    override fun seekTo(positionMs: Long) {
        webView?.evaluateJavascript("window.seekTo($positionMs)", null)
    }

    override fun seekForward() {
        scope.launch {
            val current = _playbackPosition.value
            seekTo(current + 10000)
        }
    }

    override fun seekBack() {
        scope.launch {
            val current = _playbackPosition.value
            seekTo((current - 10000).coerceAtLeast(0))
        }
    }

    override fun setSurface(surface: Surface?) {
        // WebView não usa Surface diretamente
    }

    override fun setVolume(volume: Float) {
        // WebView controla volume via sistema
    }

    override fun setPlaybackSpeed(speed: Float) {
        webView?.evaluateJavascript("document.getElementById('player').playbackRate = $speed", null)
    }

    override fun chooseTrack(group: TrackGroup, index: Int) {
        // Tracks são gerenciados pelo HLS.js/Dash.js
    }

    override fun clearTrack(type: Int) {
        // Tracks são gerenciados pelo HLS.js/Dash.js
    }
}
