package com.m3u.universal.ui.setting

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.business.setting.SettingViewModel
import com.m3u.core.architecture.preferences.AutoResumePreferences
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.rememberMutablePreference
import com.m3u.data.database.model.Playlist

@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    viewModel: SettingViewModel = hiltViewModel()
) {
    var tabIndex by remember { mutableStateOf(0) }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Configurações",
                style = MaterialTheme.typography.titleLarge
            )
            SecondaryTabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("Geral") })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("Player") })
                Tab(selected = tabIndex == 2, onClick = { tabIndex = 2 }, text = { Text("Rede") })
                Tab(selected = tabIndex == 3, onClick = { tabIndex = 3 }, text = { Text("EPG") })
                Tab(selected = tabIndex == 4, onClick = { tabIndex = 4 }, text = { Text("UI/UX") })
                Tab(selected = tabIndex == 5, onClick = { tabIndex = 5 }, text = { Text("Aparência") })
                Tab(selected = tabIndex == 6, onClick = { tabIndex = 6 }, text = { Text("Listas") })
            }
            when (tabIndex) {
                0 -> GeneralTab(onClose)
                1 -> PlayerTab()
                2 -> NetworkTab()
                3 -> EpgTab()
                4 -> UiUxTab()
                5 -> AppearanceTab()
                6 -> PlaylistManagementTab(viewModel)
            }
        }
    }
}

@Composable
private fun PlaylistManagementTab(viewModel: SettingViewModel) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    var editingPlaylist by remember { mutableStateOf<Playlist?>(null) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(
            onClick = { 
                editingPlaylist = null
                showDialog = true 
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.padding(4.dp))
            Text("Adicionar Nova Lista")
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(playlists.size) { index ->
                val playlist = playlists[index]
                PlaylistStartItem(
                    playlist = playlist,
                    onActivate = { viewModel.setActive(playlist.url) },
                    onEdit = { 
                        editingPlaylist = playlist
                        showDialog = true
                    },
                    onDelete = { viewModel.deletePlaylist(playlist.url) }
                )
            }
        }
    }

    if (showDialog) {
        PlaylistDialog(
            playlist = editingPlaylist,
            onDismiss = { showDialog = false },
            onConfirm = { title, url ->
                if (editingPlaylist == null) {
                    viewModel.addPlaylist(title, url)
                } else {
                    val current = editingPlaylist!!
                    if (current.title != title) {
                        viewModel.updatePlaylistTitle(current.url, title)
                    }
                    if (current.url != url) {
                        // Use id to update URL
                        viewModel.updatePlaylistUrl(current.id, url)
                    }
                }
                showDialog = false
            }
        )
    }
}

@Composable
private fun PlaylistStartItem(
    playlist: Playlist,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onActivate,
        colors = CardDefaults.cardColors(
            containerColor = if (playlist.active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (playlist.active) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = playlist.url,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (playlist.active) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Ativa",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Editar",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Excluir",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun PlaylistDialog(
    playlist: Playlist? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var title by remember { mutableStateOf(playlist?.title ?: "") }
    var url by remember { mutableStateOf(playlist?.url ?: "") }
    var urlError by remember { mutableStateOf<String?>(null) }
    fun validateUrl(input: String): Boolean {
        val lower = input.lowercase()
        return lower.startsWith("http://") ||
                lower.startsWith("https://") ||
                lower.startsWith("content://") ||
                lower.startsWith("file://")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (playlist == null) "Adicionar Lista" else "Editar Lista") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nome da Lista") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        urlError = if (url.isNotBlank() && !validateUrl(url)) "URL inválida" else null
                    },
                    label = { Text("URL da Lista") },
                    singleLine = true
                )
                if (urlError != null) {
                    Text(
                        text = urlError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title, url) },
                enabled = title.isNotBlank() && url.isNotBlank() && urlError == null
            ) {
                Text(if (playlist == null) "Adicionar" else "Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun GeneralTab(onClose: () -> Unit) {
    val autoResumeEnabled = rememberMutablePreference(AutoResumePreferences.AUTO_RESUME_ENABLED, AutoResumePreferences.DEFAULT_AUTO_RESUME_ENABLED)
    val launchOnBoot = rememberMutablePreference(AutoResumePreferences.LAUNCH_ON_BOOT, AutoResumePreferences.DEFAULT_LAUNCH_ON_BOOT)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Voltar ao último canal ao abrir o app", modifier = Modifier.weight(1f))
            Switch(checked = autoResumeEnabled.value, onCheckedChange = { autoResumeEnabled.value = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Iniciar na inicialização do sistema", modifier = Modifier.weight(1f))
            Switch(checked = launchOnBoot.value, onCheckedChange = { launchOnBoot.value = it })
        }
        Button(onClick = onClose) { Text("Fechar") }
    }
}

@Composable
private fun PlayerTab() {
    val tunneling = rememberMutablePreference(PreferencesKeys.TUNNELING, false)
    val reconnectMode = rememberMutablePreference(PreferencesKeys.RECONNECT_MODE, 0)
    val brightnessGesture = rememberMutablePreference(PreferencesKeys.BRIGHTNESS_GESTURE, false)
    val volumeGesture = rememberMutablePreference(PreferencesKeys.VOLUME_GESTURE, false)
    val zappingMode = rememberMutablePreference(PreferencesKeys.ZAPPING_MODE, false)
    val fullInfoPlayer = rememberMutablePreference(PreferencesKeys.FULL_INFO_PLAYER, false)
    val engine = rememberMutablePreference(PreferencesKeys.PLAYER_ENGINE, 1)
    val bufferMs = rememberMutablePreference(PreferencesKeys.BUFFER_MS, 0)
    val streamFormat = rememberMutablePreference(PreferencesKeys.STREAM_FORMAT, 0)
    val videoDecryption = rememberMutablePreference(PreferencesKeys.VIDEO_DECRYPTION_ENABLED, true)
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Buffer (ms)", modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StyledButton(text = "0", selected = bufferMs.value == 0, onClick = { bufferMs.value = 0 })
                StyledButton(text = "2000", selected = bufferMs.value == 2000, onClick = { bufferMs.value = 2000 })
                StyledButton(text = "5000", selected = bufferMs.value == 5000, onClick = { bufferMs.value = 5000 })
                StyledButton(text = "10000", selected = bufferMs.value == 10000, onClick = { bufferMs.value = 10000 })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Formato de Stream", modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StyledButton(text = "HLS", selected = streamFormat.value == 2, onClick = { streamFormat.value = 2 })
                StyledButton(text = "DASH", selected = streamFormat.value == 1, onClick = { streamFormat.value = 1 })
                StyledButton(text = "Auto", selected = streamFormat.value == 0, onClick = { streamFormat.value = 0 })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Video-Cipher Decrypter", modifier = Modifier.weight(1f))
            Switch(checked = videoDecryption.value, onCheckedChange = { videoDecryption.value = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Tunneling", modifier = Modifier.weight(1f))
            Switch(checked = tunneling.value, onCheckedChange = { tunneling.value = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Reconectar automaticamente ao terminar", modifier = Modifier.weight(1f))
            Switch(checked = reconnectMode.value == 1, onCheckedChange = { reconnectMode.value = if (it) 1 else 0 })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Gestos de brilho", modifier = Modifier.weight(1f))
            Switch(checked = brightnessGesture.value, onCheckedChange = { brightnessGesture.value = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Gestos de volume", modifier = Modifier.weight(1f))
            Switch(checked = volumeGesture.value, onCheckedChange = { volumeGesture.value = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Zapping Mode (Troca rápida)", modifier = Modifier.weight(1f))
            Switch(checked = zappingMode.value, onCheckedChange = { zappingMode.value = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Mostrar barra de informações no player", modifier = Modifier.weight(1f))
            Switch(checked = fullInfoPlayer.value, onCheckedChange = { fullInfoPlayer.value = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Engine do Player", modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StyledButton(text = "ExoPlayer", selected = engine.value == 0, onClick = { engine.value = 0 })
                StyledButton(text = "LibVLC", selected = engine.value == 1, onClick = { engine.value = 1 })
                StyledButton(text = "WebPlay", selected = engine.value == 2, onClick = { engine.value = 2 })
            }
        }
    }
}

@Composable
private fun AppearanceTab() {
    val darkMode = rememberMutablePreference(PreferencesKeys.DARK_MODE, false)
    val followSystem = rememberMutablePreference(PreferencesKeys.FOLLOW_SYSTEM_THEME, false)
    val useDynamicColors = rememberMutablePreference(PreferencesKeys.USE_DYNAMIC_COLORS, false)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Seguir tema do sistema", modifier = Modifier.weight(1f))
            Switch(checked = followSystem.value, onCheckedChange = { followSystem.value = it })
            AssistChip(
                onClick = {},
                label = { Text(if (followSystem.value) "Ativado" else "Desativado") },
                leadingIcon = {
                    val icon = if (followSystem.value) Icons.Filled.Check else Icons.Filled.Close
                    Icon(icon, contentDescription = null, tint = if (followSystem.value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Tema escuro", modifier = Modifier.weight(1f))
            Switch(checked = darkMode.value, onCheckedChange = { 
                darkMode.value = it 
                if (followSystem.value) followSystem.value = false
            })
            AssistChip(
                onClick = {},
                label = { Text(if (darkMode.value) "Ativado" else "Desativado") },
                leadingIcon = {
                    val icon = if (darkMode.value) Icons.Filled.Check else Icons.Filled.Close
                    Icon(icon, contentDescription = null, tint = if (darkMode.value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Usar cores dinâmicas", modifier = Modifier.weight(1f))
            Switch(checked = useDynamicColors.value, onCheckedChange = { useDynamicColors.value = it })
            AssistChip(
                onClick = {},
                label = { Text(if (useDynamicColors.value) "Ativado" else "Desativado") },
                leadingIcon = {
                    val icon = if (useDynamicColors.value) Icons.Filled.Check else Icons.Filled.Close
                    Icon(icon, contentDescription = null, tint = if (useDynamicColors.value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
            )
        }
    }
}

@Composable
private fun NetworkTab() {
    val userAgent = rememberMutablePreference(PreferencesKeys.CUSTOM_USER_AGENT, "")
    val proxy = rememberMutablePreference(PreferencesKeys.PROXY_ENDPOINT, "")
    val timeout = rememberMutablePreference(PreferencesKeys.CONNECT_TIMEOUT, 10000L)
    val ssl = rememberMutablePreference(PreferencesKeys.ALWAYS_TRUST_ALL_SSL, true)
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = userAgent.value,
            onValueChange = { userAgent.value = it },
            label = { Text("User Agent personalizado") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = proxy.value,
            onValueChange = { proxy.value = it },
            label = { Text("Proxy HTTP/SOCKS") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Timeout (ms):", modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StyledButton(text = "5000", selected = timeout.value == 5000L, onClick = { timeout.value = 5000L })
                StyledButton(text = "10000", selected = timeout.value == 10000L, onClick = { timeout.value = 10000L })
                StyledButton(text = "15000", selected = timeout.value == 15000L, onClick = { timeout.value = 15000L })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Confiar em todos certificados SSL", modifier = Modifier.weight(1f))
            Switch(checked = ssl.value, onCheckedChange = { ssl.value = it })
        }
    }
}

@Composable
private fun EpgTab() {
    val refreshHours = rememberMutablePreference(PreferencesKeys.EPG_REFRESH_INTERVAL_HOURS, 24)
    val offsetMins = rememberMutablePreference(PreferencesKeys.EPG_TIME_OFFSET_MINUTES, 0)
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
         Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Atualizar EPG a cada (horas):", modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StyledButton(text = "12h", selected = refreshHours.value == 12, onClick = { refreshHours.value = 12 })
                StyledButton(text = "24h", selected = refreshHours.value == 24, onClick = { refreshHours.value = 24 })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Offset de tempo (minutos):", modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StyledButton(text = "-60", selected = offsetMins.value == -60, onClick = { offsetMins.value = -60 })
                StyledButton(text = "0", selected = offsetMins.value == 0, onClick = { offsetMins.value = 0 })
                StyledButton(text = "+60", selected = offsetMins.value == 60, onClick = { offsetMins.value = 60 })
            }
        }
    }
}

@Composable
private fun UiUxTab() {
    val clockMode = rememberMutablePreference(PreferencesKeys.CLOCK_MODE, false)
    val compact = rememberMutablePreference(PreferencesKeys.COMPACT_DIMENSION, false)
    val noPicture = rememberMutablePreference(PreferencesKeys.NO_PICTURE_MODE, false)
    val godMode = rememberMutablePreference(PreferencesKeys.GOD_MODE, false)
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Modo Relógio 12h", modifier = Modifier.weight(1f))
            Switch(checked = clockMode.value, onCheckedChange = { clockMode.value = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Dimensões Compactas", modifier = Modifier.weight(1f))
            Switch(checked = compact.value, onCheckedChange = { compact.value = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Modo Sem Imagem (Apenas Áudio)", modifier = Modifier.weight(1f))
            Switch(checked = noPicture.value, onCheckedChange = { noPicture.value = it })
        }
         Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Modo Deus", modifier = Modifier.weight(1f))
            Switch(checked = godMode.value, onCheckedChange = { godMode.value = it })
        }
    }
}

@Composable
private fun StyledButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Scale animation: smaller when deactivated (0.9f), normal when active (1.0f).
    // Pressing scales it down slightly from current state.
    val targetScale = if (isPressed) {
        if (selected) 0.95f else 0.85f
    } else {
        if (selected) 1.0f else 0.9f
    }

    val scale by animateFloatAsState(
        targetValue = targetScale,
        label = "scale"
    )

    // Opacity animation: 50% when deactivated, 100% when active.
    val alpha by animateFloatAsState(
        targetValue = if (selected) 1.0f else 0.5f,
        label = "alpha"
    )

    // Pulse animation for active state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by if (selected) {
        infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )
    } else {
        remember { mutableStateOf(1.0f) }
    }

    // Background color: Primary when selected, SurfaceVariant when not.
    // Apply pulse to alpha if selected.
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            },
        shape = MaterialTheme.shapes.medium,
        color = backgroundColor,
        contentColor = contentColor,
        interactionSource = interactionSource
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
