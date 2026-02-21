package com.m3u.data.service.internal.player

import android.content.Context
import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExoPlayerController(
    private val context: Context,
    private val mediaSourceFactory: MediaSource.Factory,
    private val isTunneling: Boolean
) : PlayerController, Player.Listener {

    private var player: ExoPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _playbackState = MutableStateFlow(Player.STATE_IDLE)
    override val playbackState = _playbackState.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying = _isPlaying.asStateFlow()

    private val _videoSize = MutableStateFlow(android.graphics.Rect())
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
        get() = player

    init {
        initializePlayer()
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                playWhenReady = true
                addListener(this@ExoPlayerController)
                if (isTunneling) {
                    // Tunneling setup if needed
                }
            }
        
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                player?.let {
                    _playbackPosition.value = it.currentPosition
                    _duration.value = it.duration.takeIf { d -> d != C.TIME_UNSET } ?: 0L
                }
            }
        }
    }

    override fun play(url: String, headers: Map<String, String>) {
        if (player == null) {
            initializePlayer()
        }
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .build()
        
        val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)
        player?.apply {
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
            play()
        }
    }

    override fun pause() {
        player?.pause()
    }

    override fun resume() {
        player?.play()
    }

    override fun stop() {
        player?.stop()
    }

    override fun release() {
        player?.removeListener(this)
        player?.release()
        player = null
    }

    override fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    override fun seekForward() {
        player?.let { it.seekTo(it.currentPosition + 10000) }
    }

    override fun seekBack() {
        player?.let { it.seekTo(it.currentPosition - 10000) }
    }

    override fun setSurface(surface: Surface?) {
        player?.setVideoSurface(surface)
    }

    override fun setVolume(volume: Float) {
        player?.volume = volume
    }

    override fun setPlaybackSpeed(speed: Float) {
        player?.setPlaybackSpeed(speed)
    }

    override fun chooseTrack(group: TrackGroup, index: Int) {
        player?.let {
            val override = TrackSelectionOverride(group, index)
            it.trackSelectionParameters = it.trackSelectionParameters
                .buildUpon()
                .setOverrideForType(override)
                .setTrackTypeDisabled(group.type, false)
                .build()
        }
    }

    override fun clearTrack(type: Int) {
        player?.let {
            it.trackSelectionParameters = it.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(type, true)
                .build()
        }
    }

    // Player.Listener implementation
    override fun onPlaybackStateChanged(state: Int) {
        _playbackState.value = state
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        _isPlaying.value = isPlaying
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        _videoSize.value = android.graphics.Rect(0, 0, videoSize.width, videoSize.height)
    }

    override fun onPlayerError(error: PlaybackException) {
        _playbackException.value = error
    }

    override fun onTracksChanged(tracks: Tracks) {
        _tracksGroups.value = tracks.groups
    }
}
