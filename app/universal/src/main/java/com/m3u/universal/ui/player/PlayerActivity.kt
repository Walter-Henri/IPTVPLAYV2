package com.m3u.universal.ui.player

import android.app.PictureInPictureParams
import android.content.Intent
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.m3u.core.Contracts
import com.m3u.data.service.PlayerManager
import com.m3u.universal.ui.player.components.MiniGuide
import com.m3u.universal.ui.player.components.PlayerControls
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import javax.inject.Inject

@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {
    private val viewModel: PlayerViewModel by viewModels()
    @Inject lateinit var playerManager: PlayerManager
    @Inject lateinit var settings: com.m3u.core.architecture.preferences.Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        val channelId = intent.getIntExtra(Contracts.PLAYER_SHORTCUT_CHANNEL_ID, -1)
        if (channelId != -1) {
            viewModel.loadChannel(channelId)
        }
        
        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val exoPlayer by playerManager.player.collectAsState()
            
            val isPlaying by playerManager.isPlaying.collectAsState()
            val playbackState by playerManager.playbackState.collectAsState()
            val tracksGroups by playerManager.tracksGroups.collectAsState()
            
            var controlsVisible by remember { mutableStateOf(true) }
            val currentPosition by playerManager.playbackPosition.collectAsState()
            val duration by playerManager.duration.collectAsState()
            var miniGuideVisible by remember { mutableStateOf(false) }
            var resizeIndex by remember { mutableStateOf(0) }
            
            val resizeModes = listOf(
                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT,
                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL,
                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            )

            // Auto-hide controls
            LaunchedEffect(controlsVisible, isPlaying) {
                if (controlsVisible && isPlaying) {
                    delay(5000)
                    controlsVisible = false
                }
            }

            // Progress Loop removed as it is now handled in PlayerManagerImpl

            val zappingMode by settings.zappingMode.collectAsState()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                val playerEngine by playerManager.playerEngine.collectAsState()

                // Video Rendering
                com.m3u.universal.ui.player.components.PlayerSurface(
                    player = exoPlayer,
                    playerEngine = playerEngine,
                    onSurfaceCreated = { playerManager.setSurface(it) },
                    onSurfaceDestroyed = { playerManager.setSurface(null) },
                    resizeMode = resizeModes[resizeIndex],
                    modifier = Modifier.fillMaxSize()
                )

                // Gesture Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { controlsVisible = !controlsVisible },
                                onDoubleTap = { offset ->
                                    if (offset.x < size.width / 2) {
                                        playerManager.seekBack()
                                    } else {
                                        playerManager.seekForward()
                                    }
                                    controlsVisible = true
                                }
                            )
                        }
                        .pointerInput(zappingMode) {
                            if (!zappingMode) return@pointerInput
                            var totalDrag = 0f
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { _, dragAmount ->
                                    totalDrag += dragAmount
                                },
                                onDragEnd = {
                                    if (totalDrag > 300f) {
                                        viewModel.playAdjacent(false)
                                    } else if (totalDrag < -300f) {
                                        viewModel.playAdjacent(true)
                                    }
                                    totalDrag = 0f
                                },
                                onDragCancel = { totalDrag = 0f }
                            )
                        }
                        .onKeyEvent { ev ->
                            if (ev.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                                when (ev.nativeKeyEvent.keyCode) {
                                    android.view.KeyEvent.KEYCODE_DPAD_UP,
                                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> { 
                                        if (zappingMode) {
                                            viewModel.playAdjacent(false)
                                            true
                                        } else false
                                    }
                                    android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> { 
                                        if (zappingMode) {
                                            viewModel.playAdjacent(true)
                                            true
                                        } else false
                                    }
                                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                                    android.view.KeyEvent.KEYCODE_ENTER -> { controlsVisible = !controlsVisible; true }
                                    else -> false
                                }
                            } else false
                        }
                        .focusable() // Importante para receber eventos de teclado no overlay
                ) {

                if (playbackState == Player.STATE_BUFFERING) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                PlayerControls(
                    visible = controlsVisible,
                    title = uiState.channel?.title ?: "Carregando...",
                    isPlaying = isPlaying,
                    currentPosition = currentPosition,
                    duration = duration,
                    tracksGroups = tracksGroups,
                    isFavourite = uiState.channel?.favourite ?: false,
                    onFavourite = { viewModel.onFavourite() },
                    onBack = { finish() },
                    onPlayPause = { if (isPlaying) playerManager.pause() else playerManager.play() },
                    onSeek = { playerManager.seekTo(it) },
                    onToggleMiniGuide = { miniGuideVisible = !miniGuideVisible },
                    onChooseTrack = { group, index -> playerManager.chooseTrack(group, index) },
                    onEnterPiP = { 
                        enterPictureInPictureMode(
                            PictureInPictureParams.Builder()
                                .setAspectRatio(Rational(16, 9))
                                .build()
                        )
                    },
                    onOpenWebPlayer = {
                        val intent = Intent(this@PlayerActivity, WebPlayerActivity::class.java).apply {
                            putExtra(Contracts.PLAYER_SHORTCUT_CHANNEL_ID, uiState.channel?.id)
                        }
                        startActivity(intent)
                    },
                    onResize = { 
                        resizeIndex = (resizeIndex + 1) % resizeModes.size 
                    }
                )

                MiniGuide(
                    visible = miniGuideVisible,
                    channels = uiState.miniGuideChannels,
                    onChannelClick = { ch ->
                        viewModel.loadChannel(ch.id)
                        miniGuideVisible = false
                    },
                    onFavouriteClick = { ch -> viewModel.onFavourite(ch.id) },
                    modifier = Modifier.align(Alignment.CenterEnd)
                )

                // Error Overlay
                uiState.error?.let { err ->
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)).padding(32.dp)) {
                        Text(
                            text = err,
                            color = Color.Red,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Auto PiP if enabled in settings
        if (settings.autoPipMode.value) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) {
            playerManager.release()
        }
    }
}
