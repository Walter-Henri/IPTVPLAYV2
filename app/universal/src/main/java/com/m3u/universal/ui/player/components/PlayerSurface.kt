package com.m3u.universal.ui.player.components

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView

@Composable
fun PlayerSurface(
    player: Player?,
    playerEngine: Int,
    onSurfaceCreated: (android.view.Surface?) -> Unit,
    onSurfaceDestroyed: () -> Unit,
    modifier: Modifier = Modifier,
    resizeMode: Int = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
) {
    when (playerEngine) {
        0 -> {
            // ExoPlayer / Media3
            if (player != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = false
                            this.resizeMode = resizeMode
                            keepScreenOn = true
                            // Garantir centralização
                            layoutParams = android.widget.FrameLayout.LayoutParams(
                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                android.view.Gravity.CENTER
                            )
                        }
                    },
                    update = { view ->
                        view.player = player
                        view.resizeMode = resizeMode
                    },
                    modifier = modifier
                )
            }
        }
        1 -> {
            // LibVLC
            var currentSurface by remember { mutableStateOf<android.view.Surface?>(null) }
            
            AndroidView(
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        keepScreenOn = true
                        layoutParams = android.widget.FrameLayout.LayoutParams(
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                            android.view.Gravity.CENTER
                        )
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                currentSurface = holder.surface
                                onSurfaceCreated(holder.surface)
                            }
                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                                // Re-notify on size changes
                                currentSurface?.let { onSurfaceCreated(it) }
                            }
                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                currentSurface = null
                                onSurfaceDestroyed()
                            }
                        })
                    }
                },
                modifier = modifier
            )
        }
        2 -> {
            // WebPlay - WebView based player
            AndroidView(
                factory = { ctx ->
                    android.widget.FrameLayout(ctx).apply {
                        keepScreenOn = true
                        layoutParams = android.widget.FrameLayout.LayoutParams(
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                            android.view.Gravity.CENTER
                        )
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { container ->
                    // WebView será gerenciado pelo WebPlayController
                    // Notificar que a superfície está pronta
                    onSurfaceCreated(null)
                },
                modifier = modifier
            )
        }
        else -> {
            // Fallback para outros engines
            AndroidView(
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        keepScreenOn = true
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                onSurfaceCreated(holder.surface)
                            }
                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                onSurfaceDestroyed()
                            }
                        })
                    }
                },
                modifier = modifier
            )
        }
    }
}
