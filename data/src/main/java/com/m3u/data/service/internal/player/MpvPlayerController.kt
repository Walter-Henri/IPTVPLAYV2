package com.m3u.data.service.internal.player

import android.content.Context
import android.graphics.Rect
import android.view.Surface
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * MpvPlayerController - Professional MPV Integration
 * 
 * Utilizes libmpv via abdallahmehiz's mpv-android-lib.
 * Primary player for the Universal Player.
 */
class MpvPlayerController(
    private val context: Context
) : PlayerController, MPVLib.EventObserver {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null

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

    override val playerObject: Any? get() = null

    init {
        try {
            MPVLib.create(context)
            MPVLib.setOptionString("config", "yes")
            MPVLib.setOptionString("config-dir", context.cacheDir.absolutePath)
            
            // Performance & HW Acceleration
            MPVLib.setOptionString("hwdec", "mediacodec-copy") 
            MPVLib.setOptionString("vo", "gpu")
            MPVLib.setOptionString("gpu-context", "android")
            
            // Network & Buffer Optimization
            MPVLib.setOptionString("cache", "yes")
            MPVLib.setOptionString("demuxer-max-bytes", "150M")
            MPVLib.setOptionString("demuxer-readahead-secs", "30")
            
            MPVLib.init()
            MPVLib.addObserver(this)

            // 4 corresponds to MPV_FORMAT_INT64
            MPVLib.observeProperty("time-pos", 4)
            MPVLib.observeProperty("duration", 4)
            MPVLib.observeProperty("pause", 1) // 1 corresponds to MPV_FORMAT_FLAG (boolean)
            
            Timber.tag("MPV").d("MPVLib initialized successfully")
        } catch (e: Exception) {
            Timber.tag("MPV").e(e, "Failed to initialize MPVLib")
            _playbackException.value = PlaybackException("MPV Init failed", e, PlaybackException.ERROR_CODE_UNSPECIFIED)
        }
    }

    override fun play(url: String, headers: Map<String, String>) {
        _playbackState.value = Player.STATE_BUFFERING
        
        // Clear previous headers
        MPVLib.setOptionString("http-header-fields", "")
        
        // Header injection
        if (headers.isNotEmpty()) {
            val headerFields = headers.entries.joinToString(",") { 
                "${it.key}: ${it.value}" 
            }
            MPVLib.setOptionString("http-header-fields", headerFields)
            Timber.tag("MPV").d("Headers injected: $headerFields")
        }

        MPVLib.command("loadfile", url)
        MPVLib.setPropertyBoolean("pause", false)
        _isPlaying.value = true
    }

    override fun pause() {
        MPVLib.setPropertyBoolean("pause", true)
        _isPlaying.value = false
    }

    override fun resume() {
        MPVLib.setPropertyBoolean("pause", false)
        _isPlaying.value = true
    }

    override fun stop() {
        MPVLib.command("stop")
        _isPlaying.value = false
        _playbackState.value = Player.STATE_IDLE
    }

    override fun release() {
        stop()
        MPVLib.removeObserver(this)
        MPVLib.destroy()
    }

    override fun seekTo(positionMs: Long) {
        MPVLib.command("seek", (positionMs / 1000).toString(), "absolute")
    }

    override fun seekForward() {
        MPVLib.command("seek", "10", "relative")
    }

    override fun seekBack() {
        MPVLib.command("seek", "-10", "relative")
    }

    override fun setSurface(surface: Surface?) {
        if (surface != null) {
            MPVLib.attachSurface(surface)
        } else {
            MPVLib.detachSurface()
        }
    }

    override fun setVolume(volume: Float) {
        MPVLib.setPropertyInt("volume", (volume * 100).toInt())
    }

    override fun setPlaybackSpeed(speed: Float) {
        MPVLib.setPropertyDouble("speed", speed.toDouble())
    }

    override fun chooseTrack(group: TrackGroup, index: Int) {
        // Implement track selection logic if needed
    }

    override fun clearTrack(type: Int) {
        // Implement track clearing logic if needed
    }

    // MPVLib.EventObserver implementation
    override fun event(eventId: Int) {
        when (eventId) {
            MPVLib.MpvEvent.MPV_EVENT_START_FILE -> {
                _playbackState.value = Player.STATE_BUFFERING
            }
            MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> {
                _playbackState.value = Player.STATE_READY
            }
            MPVLib.MpvEvent.MPV_EVENT_END_FILE -> {
                _playbackState.value = Player.STATE_ENDED
                _isPlaying.value = false
            }
            MPVLib.MpvEvent.MPV_EVENT_IDLE -> {
                _playbackState.value = Player.STATE_IDLE
                _isPlaying.value = false
            }
        }
    }

    override fun eventProperty(name: String, value: String) {
        // Handle string property changes if needed
    }

    override fun eventProperty(name: String, value: Long) {
        when (name) {
            "time-pos" -> _playbackPosition.value = value * 1000L
            "duration" -> _duration.value = value * 1000L
        }
    }

    override fun eventProperty(name: String, value: Boolean) {
        if (name == "pause") {
            _isPlaying.value = !value
        }
    }

    override fun eventProperty(name: String, value: Double) {}

    override fun eventProperty(name: String, value: `is`.xyz.mpv.MPVNode) {}

    override fun eventProperty(name: String) {
        // Handle other property changes
    }
}
