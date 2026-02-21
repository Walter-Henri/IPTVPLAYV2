package com.m3u.universal.ui.tv

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.m3u.universal.ui.common.StartupViewModel
import com.m3u.universal.ui.common.StartupState
import androidx.tv.material3.darkColorScheme
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.foundation.focusable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.tv.material3.*
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.m3u.core.foundation.ui.*
import com.m3u.universal.ui.common.ChannelBrowseViewModel
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.ContentScale
import com.m3u.universal.ui.tv.components.*
 
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun TvRoot(
    playbackManager: com.m3u.data.service.PlaybackManager
) {
    val startupViewModel: StartupViewModel = hiltViewModel()
    val state by startupViewModel.state.collectAsStateWithLifecycle()
    
    if (state.isChecking) {
        com.m3u.universal.ui.common.PremiumLoadingScreen(message = "Verificando listas...")
        return
    }

    if (!state.hasPlaylists) {
        TvSetupScreen(startupViewModel, state)
    } else {
        TvBrowserScreen(
            playbackManager = playbackManager,
            activePlaylistUrl = state.activePlaylistUrl
        )
    }
}

