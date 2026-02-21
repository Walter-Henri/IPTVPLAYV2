package com.m3u.universal.ui.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks
import com.m3u.data.service.PlayerManager

@Composable
fun PlayerControls(
    visible: Boolean,
    title: String,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    tracksGroups: List<Tracks.Group>,
    isFavourite: Boolean,
    onFavourite: () -> Unit,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleMiniGuide: () -> Unit,
    onChooseTrack: (androidx.media3.common.TrackGroup, Int) -> Unit,
    onEnterPiP: () -> Unit,
    onOpenWebPlayer: () -> Unit,
    onResize: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.7f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f)
                        )
                    )
                )
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                IconButton(onClick = onFavourite) {
                    Icon(
                        if (isFavourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavourite) Color.Red else Color.White
                    )
                }

                IconButton(onClick = onResize) {
                    Icon(Icons.Default.AspectRatio, contentDescription = "Resize", tint = Color.White)
                }
                
                PlayerSettingsMenu(
                    tracksGroups = tracksGroups,
                    onToggleMiniGuide = onToggleMiniGuide,
                    onChooseTrack = onChooseTrack,
                    onEnterPiP = onEnterPiP,
                    onOpenWebPlayer = onOpenWebPlayer
                )
            }

            // Center Controls
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onSeek(currentPosition - 10000) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Replay10, contentDescription = "-10s", tint = Color.White, modifier = Modifier.fillMaxSize())
                }
                
                IconButton(onClick = onPlayPause, modifier = Modifier.size(64.dp)) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                IconButton(onClick = { onSeek(currentPosition + 10000) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Forward10, contentDescription = "+10s", tint = Color.White, modifier = Modifier.fillMaxSize())
                }
            }

            // Bottom Bar
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(16.dp)
            ) {
                Slider(
                    value = currentPosition.toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = formatDuration(currentPosition), color = Color.White)
                    Text(text = formatDuration(duration), color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun PlayerSettingsMenu(
    tracksGroups: List<Tracks.Group>,
    onToggleMiniGuide: () -> Unit,
    onChooseTrack: (androidx.media3.common.TrackGroup, Int) -> Unit,
    onEnterPiP: () -> Unit,
    onOpenWebPlayer: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var qualityExpanded by remember { mutableStateOf(false) }
    
    val videoGroups = remember(tracksGroups) {
        tracksGroups.filter { it.type == C.TRACK_TYPE_VIDEO }
    }

    Box {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(text = { Text("Mudar Proporção") }, onClick = { /* TODO: cycle resize mode */ menuExpanded = false })
            DropdownMenuItem(text = { Text("Mini Guia") }, onClick = { onToggleMiniGuide(); menuExpanded = false })
            DropdownMenuItem(text = { Text("Qualidade") }, onClick = { qualityExpanded = true; menuExpanded = false })
            DropdownMenuItem(text = { Text("Picture-in-Picture") }, onClick = { onEnterPiP(); menuExpanded = false })
            DropdownMenuItem(text = { Text("WebPlayer") }, onClick = { onOpenWebPlayer(); menuExpanded = false })
        }
        
        DropdownMenu(expanded = qualityExpanded, onDismissRequest = { qualityExpanded = false }) {
            if (videoGroups.isEmpty()) {
                DropdownMenuItem(text = { Text("Sem opções") }, onClick = { qualityExpanded = false })
            } else {
                videoGroups.forEach { group ->
                    for (i in 0 until group.length) {
                        val supported = group.isTrackSupported(i)
                        val format = if (supported) group.getTrackFormat(i) else null
                        val selected = group.isTrackSelected(i)
                        val label = buildString {
                            if (format != null) {
                                val height = format.height.takeIf { it > 0 } ?: 0
                                append(if (height > 0) "${height}p" else "Auto")
                                if (format.bitrate > 0) append(" · ${format.bitrate/1000}kbps")
                            } else append("Não suportado")
                            if (selected) append(" ✓")
                        }
                        DropdownMenuItem(
                            enabled = supported,
                            text = { Text(label) },
                            onClick = {
                                onChooseTrack(group.mediaTrackGroup, i)
                                qualityExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
