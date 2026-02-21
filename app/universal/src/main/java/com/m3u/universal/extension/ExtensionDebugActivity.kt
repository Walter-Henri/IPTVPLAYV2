package com.m3u.universal.extension

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.business.extension.App
import com.m3u.business.extension.ExtensionViewModel
import com.m3u.extension.api.CallTokenConst
import com.m3u.extension.api.RemoteClient
import com.m3u.extension.api.business.InfoApi
import com.m3u.extension.api.business.SubscribeApi
import com.m3u.extension.api.model.AddPlaylistRequest
import com.m3u.extension.api.model.Playlist as ApiPlaylist
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ExtensionDebugActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val token = handleArguments(intent)

        setContent {
            MaterialTheme {
                if (token != null) {
                    ExtensionConnectionScreen(token)
                } else {
                    val viewModel: ExtensionViewModel = hiltViewModel()
                    val apps by viewModel.applications.collectAsStateWithLifecycle()
                    ExtensionManagerScreen(apps, onAppClick = { viewModel.runExtension(it) })
                }
            }
        }
    }

    private fun handleArguments(intent: Intent): CallToken? {
        val packageName = intent.getStringExtra(CallTokenConst.PACKAGE_NAME) ?: return null
        val className = intent.getStringExtra(CallTokenConst.CLASS_NAME) ?: return null
        val accessKey = intent.getStringExtra(CallTokenConst.ACCESS_KEY) ?: return null
        return CallToken(packageName, className, accessKey)
    }
}

data class CallToken(
    val packageName: String,
    val className: String,
    val accessKey: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionManagerScreen(apps: List<App>, onAppClick: (App) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Extensions") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        if (apps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No extensions found")
            }
        } else {
            LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
                items(apps) { app ->
                    ExtensionItem(app = app, onClick = { onAppClick(app) })
                }
            }
        }
    }
}

@Composable
fun ExtensionItem(app: App, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val bitmap = remember(app.icon) { app.icon.toBitmap().asImageBitmap() }
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = app.name, style = MaterialTheme.typography.titleMedium)
                Text(text = app.version, style = MaterialTheme.typography.bodySmall)
                if (app.description.isNotEmpty()) {
                    Text(text = app.description, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                }
            }
        }
    }
}

@Composable
fun ExtensionConnectionScreen(token: CallToken) {
    val client = remember { RemoteClient() }
    val infoApi = remember { client.create<InfoApi>() }
    val subscribeApi = remember { client.create<SubscribeApi>() }
    val coroutineScope = rememberCoroutineScope()
    val isConnected by client.isConnectedObservable.collectAsStateWithLifecycle(false)
    val commands = remember { mutableStateListOf<String>() }
    val context = androidx.compose.ui.platform.LocalContext.current

    DisposableEffect(Unit) {
        onDispose {
            if (isConnected) {
                client.disconnect(context)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text(
                text = "Connected to: ${token.packageName}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val lazyListState = rememberLazyListState()

            LaunchedEffect(commands.size) {
                if (commands.isNotEmpty()) {
                    lazyListState.animateScrollToItem(commands.size - 1)
                }
            }

            // Console
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
                    .weight(1f),
                contentPadding = PaddingValues(8.dp)
            ) {
                itemsIndexed(commands) { index, command ->
                    val isFocused = index == commands.lastIndex
                    val color by animateColorAsState(
                        targetValue = if (isFocused) Color.Yellow else Color.White,
                        label = "color"
                    )

                    Text(
                        text = "> $command",
                        fontFamily = FontFamily.Monospace,
                        color = color,
                        style = LocalTextStyle.current.copy(
                            lineBreak = LineBreak.Paragraph
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Actions
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (!isConnected) {
                            client.connect(context, token.packageName, token.className, token.accessKey)
                            commands += "Connecting..."
                        } else {
                            client.disconnect(context)
                            commands += "Disconnected"
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isConnected) "Disconnect" else "Connect")
                }
            }
            
            if (isConnected) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    val info = infoApi.getAppInfo()
                                    commands += "AppInfo: $info"
                                } catch (e: Exception) {
                                    commands += "Error: ${e.message}"
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("App Info")
                    }
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    val modules = infoApi.getModules()
                                    commands += "Modules: $modules"
                                } catch (e: Exception) {
                                    commands += "Error: ${e.message}"
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Modules")
                    }
                }
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                val result = subscribeApi.addPlaylist(
                                    ApiPlaylist(
                                        url = "https://example.com/playlist.m3u",
                                        title = "Test Playlist",
                                        user_agent = "M3U Extension Debug"
                                    )
                                )
                                commands += "AddPlaylist: $result"
                            } catch (e: Exception) {
                                commands += "Error: ${e.message}"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Test Playlist")
                }
            }
        }
    }
}
