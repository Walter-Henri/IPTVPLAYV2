package com.m3u.universal.ui.common

import android.app.Activity
import android.content.Intent
import androidx.activity.OnBackPressedDispatcher
import androidx.compose.runtime.Composable
import androidx.lifecycle.LifecycleCoroutineScope
import com.m3u.data.service.PlaybackManager
import com.m3u.universal.ui.phone.SmartphoneRoot
import com.m3u.universal.ui.tv.TvRoot

@Composable
fun AppRoot(
    isTv: Boolean,
    activity: Activity,
    intent: Intent,
    playbackManager: PlaybackManager,
    lifecycleScope: LifecycleCoroutineScope,
    backDispatcher: OnBackPressedDispatcher
) {
    // Lógica de Auto-Retorno ao abrir o app
    androidx.compose.runtime.LaunchedEffect(Unit) {
        playbackManager.attemptAutoResume(
            onSuccess = { channelId ->
                playbackManager.launchPlayerActivity(channelId)
            },
            onFailure = { reason ->
                // Log opcional ou feedback silencioso
                android.util.Log.d("AppRoot", "Auto-resume falhou ou desativado: $reason")
            }
        )
    }

    if (isTv) {
        TvRoot(
            playbackManager = playbackManager
        )
    } else {
        SmartphoneRoot(
            intent = intent,
            playbackManager = playbackManager
        )
    }
}
