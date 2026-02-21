package com.m3u.data.service.internal.player

import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction for different player engines (ExoPlayer, LibVLC, IJKPlayer).
 */
interface PlayerController {
    val playerObject: Any? // The underlying player instance (ExoPlayer, LibVLC MediaPlayer, etc.)
    
    val playbackState: StateFlow<Int>
    val isPlaying: StateFlow<Boolean>
    val videoSize: StateFlow<android.graphics.Rect>
    val playbackException: StateFlow<PlaybackException?>
    val playbackPosition: StateFlow<Long>
    val duration: StateFlow<Long>
    
    val tracksGroups: StateFlow<List<Tracks.Group>>
    
    fun play(url: String, headers: Map<String, String> = emptyMap())
    fun pause()
    fun resume()
    fun stop()
    fun release()
    fun seekTo(positionMs: Long)
    fun seekForward()
    fun seekBack()
    
    fun setSurface(surface: Surface?)
    fun setVolume(volume: Float)
    fun setPlaybackSpeed(speed: Float)
    
    fun chooseTrack(group: TrackGroup, index: Int)
    fun clearTrack(type: Int)
}
