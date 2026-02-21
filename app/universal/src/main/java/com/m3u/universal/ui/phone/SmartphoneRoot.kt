package com.m3u.universal.ui.phone

import android.content.Intent
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.m3u.data.service.PlaybackManager
import com.m3u.universal.ui.common.StartupViewModel
import com.m3u.universal.ui.common.StartupState
import com.m3u.universal.ui.common.ChannelBrowseViewModel
import com.m3u.core.foundation.ui.PremiumColors
import com.m3u.universal.ui.phone.components.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SmartphoneRoot(
    intent: Intent,
    playbackManager: PlaybackManager
) {
    val startupViewModel: StartupViewModel = hiltViewModel()
    val state by startupViewModel.state.collectAsState()
    val playlists by startupViewModel.playlists.collectAsState()
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    var currentTab by rememberSaveable { mutableStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showPlaylistDialog by rememberSaveable { mutableStateOf(false) }
    
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.POST_NOTIFICATIONS,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
            )
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            )
        }
        
        if (intent.action == android.content.Intent.ACTION_SEND) {
            val type = intent.type
            if (type == "application/json") {
                val body = intent.getStringExtra(android.content.Intent.EXTRA_TEXT)
                if (!body.isNullOrBlank()) {
                    startupViewModel.importChannelsJsonBody(body)
                }
            }
        }
        
        // Auto-resume logic
        playbackManager.attemptAutoResume(
            onSuccess = { channelId ->
                playbackManager.launchPlayerActivity(channelId, fullScreen = true)
            },
            onFailure = { reason ->
                // Timber.d("Auto-resume failed: $reason")
            }
        )
    }

    if (state.isChecking) {
        StartupLoadingScreen()
        return
    }

    if (!state.hasPlaylists) {
        Box(modifier = Modifier.fillMaxSize()) {
            SetupScreen(viewModel = startupViewModel, state = state)
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerTonalElevation = 0.dp
            ) {
                DrawerHeader()
                Spacer(Modifier.height(12.dp))
                NavigationDrawerItem(
                    label = { Text("Home", fontWeight = FontWeight.Medium) },
                    selected = currentTab == 0,
                    onClick = { currentTab = 0; scope.launch { drawerState.close() } },
                    icon = { Icon(Icons.Default.Home, null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text("Favoritos", fontWeight = FontWeight.Medium) },
                    selected = currentTab == 1,
                    onClick = { currentTab = 1; scope.launch { drawerState.close() } },
                    icon = { Icon(Icons.Default.Favorite, null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text("Recentes", fontWeight = FontWeight.Medium) },
                    selected = currentTab == 2,
                    onClick = { currentTab = 2; scope.launch { drawerState.close() } },
                    icon = { Icon(Icons.Default.History, null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text("Extrator M3U8", fontWeight = FontWeight.Medium) },
                    selected = currentTab == 4,
                    onClick = { currentTab = 4; scope.launch { drawerState.close() } },
                    icon = { Icon(Icons.Default.Link, null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                HorizontalDivider(Modifier.padding(vertical = 12.dp, horizontal = 28.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                NavigationDrawerItem(
                    label = { Text("Configurações", fontWeight = FontWeight.Medium) },
                    selected = false,
                    onClick = { showSettings = true; scope.launch { drawerState.close() } },
                    icon = { Icon(Icons.Default.Settings, null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { 
                        Text(
                            text = when(currentTab) {
                                0 -> "M3U PLAY"
                                1 -> "FAVORITOS"
                                2 -> "RECENTES"
                                4 -> "EXTRATOR"
                                else -> "M3U PLAY"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        ) 
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showPlaylistDialog = true }) {
                            Icon(Icons.Default.PlaylistPlay, contentDescription = "Listas")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                if (currentTab == 0) {
                    FloatingActionButton(
                        onClick = { showPlaylistDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = CircleShape,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Adicionar")
                    }
                }
            },
            content = { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    when (currentTab) {
                        0 -> ChannelRowsScreen(
                            playbackManager = playbackManager,
                            activePlaylistUrl = state.activePlaylistUrl
                        )
                        1 -> com.m3u.universal.ui.favorite.FavoritesScreen(
                            onPlay = { id -> playbackManager.launchPlayerActivity(id, fullScreen = true) }
                        )
                        2 -> SmartphoneRecentsScreen(
                            onPlay = { id -> playbackManager.launchPlayerActivity(id, fullScreen = true) }
                        )
                        4 -> com.m3u.universal.ui.extension.ExtensionIntegrationScreen(
                            onPlay = { id -> playbackManager.launchPlayerActivity(id.toInt(), fullScreen = true) }
                        )
                    }
                }
            }
        )
    }

    if (showSettings) {
        com.m3u.universal.ui.setting.SettingsScreen(onClose = { showSettings = false })
    }

    if (showPlaylistDialog) {
        PlaylistSelectionDialog(
            playlists = playlists,
            onSelect = { url -> startupViewModel.setActivePlaylist(url); showPlaylistDialog = false },
            onDismiss = { showPlaylistDialog = false }
        )
    }
}

