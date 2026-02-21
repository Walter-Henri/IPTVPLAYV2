package com.m3u.data.service.internal.player

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import android.view.Surface
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

class VlcPlayerController(
    private val context: Context
) : PlayerController {

    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
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
        get() = mediaPlayer

    init {
        initializePlayer()
    }

    private fun initializePlayer() {
        val options = arrayListOf(
            "--no-drop-late-frames",
            "--no-skip-frames",
            "--rtsp-tcp",
            "--file-caching=2000",
            "--network-caching=2000",
            "--live-caching=2000"
        )
        libVLC = LibVLC(context, options)
        mediaPlayer = MediaPlayer(libVLC)
        
        mediaPlayer?.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Buffering -> {
                    _playbackState.value = Player.STATE_BUFFERING
                }
                MediaPlayer.Event.Playing -> {
                    _playbackState.value = Player.STATE_READY
                    _isPlaying.value = true
                }
                MediaPlayer.Event.Paused -> {
                    _isPlaying.value = false
                }
                MediaPlayer.Event.Stopped -> {
                    _playbackState.value = Player.STATE_IDLE
                    _isPlaying.value = false
                }
                MediaPlayer.Event.EndReached -> {
                    _playbackState.value = Player.STATE_ENDED
                }
                MediaPlayer.Event.EncounteredError -> {
                    _playbackException.value = PlaybackException(
                        "VLC Encountered Error",
                        null,
                        PlaybackException.ERROR_CODE_UNSPECIFIED
                    )
                }
                MediaPlayer.Event.Vout -> {
                    // Video output changes
                    val vout = mediaPlayer?.vlcVout
                    // Update size if possible
                }
            }
        }
        
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                mediaPlayer?.let {
                    _playbackPosition.value = it.time
                    _duration.value = it.length
                }
            }
        }
    }

    override fun play(url: String, headers: Map<String, String>) {
        val media = Media(libVLC, Uri.parse(url))
        headers.forEach { (k, v) ->
            media.addOption(":$k=$v")
            // VLC specific headers often use :http-user-agent etc.
            if (k.equals("User-Agent", true)) media.addOption(":http-user-agent=$v")
            if (k.equals("Referer", true)) media.addOption(":http-referrer=$v")
        }
        mediaPlayer?.media = media
        mediaPlayer?.play()
    }

    override fun pause() {
        mediaPlayer?.pause()
    }

    override fun resume() {
        mediaPlayer?.play()
    }

    override fun stop() {
        mediaPlayer?.stop()
    }

    override fun release() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        libVLC?.release()
        mediaPlayer = null
        libVLC = null
    }

    override fun seekTo(positionMs: Long) {
        mediaPlayer?.time = positionMs
    }

    override fun seekForward() {
        mediaPlayer?.let { it.time = it.time + 10000 }
    }

    override fun seekBack() {
        mediaPlayer?.let { it.time = it.time - 10000 }
    }

    override fun setSurface(surface: Surface?) {
        mediaPlayer?.vlcVout?.let { vout ->
            // Detach old views first
            vout.detachViews()
            
            if (surface != null) {
                vout.setVideoSurface(surface, null)
                vout.attachViews()
            }
        }
    }

    override fun setVolume(volume: Float) {
        mediaPlayer?.volume = (volume * 100).toInt()
    }

    override fun setPlaybackSpeed(speed: Float) {
        mediaPlayer?.rate = speed
    }

    override fun chooseTrack(group: TrackGroup, index: Int) {
        // VLC track selection logic (Sout, etc.)
    }

    override fun clearTrack(type: Int) {
        // VLC track clearing logic
    }
}
