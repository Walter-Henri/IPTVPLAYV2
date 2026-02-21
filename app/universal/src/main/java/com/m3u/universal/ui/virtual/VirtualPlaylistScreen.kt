package com.m3u.universal.ui.virtual

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist

@Composable
fun VirtualPlaylistScreen(
    onPlay: (Int) -> Unit,
    viewModel: VirtualPlaylistViewModel = hiltViewModel()
) {
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        // Status & Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (status) {
                is VirtualPlaylistViewModel.DownloadStatus.Idle -> {
                    Text("Status: ocioso", style = MaterialTheme.typography.bodySmall)
                }
                is VirtualPlaylistViewModel.DownloadStatus.InProgress -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Baixando channels.json...", style = MaterialTheme.typography.bodySmall)
                    }
                }
                is VirtualPlaylistViewModel.DownloadStatus.Success -> {
                    val count = (status as VirtualPlaylistViewModel.DownloadStatus.Success).count
                    Text("Baixado: $count canais", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                is VirtualPlaylistViewModel.DownloadStatus.Error -> {
                    val msg = (status as VirtualPlaylistViewModel.DownloadStatus.Error).message
                    Text("Erro: $msg", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Button(onClick = { viewModel.downloadChannelsJson() }) {
                Text("Baixar channels.json")
            }
        }

        // Filter Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filter == VirtualPlaylistViewModel.Filter.ALL,
                onClick = { viewModel.setFilter(VirtualPlaylistViewModel.Filter.ALL) },
                label = { Text("Tudo") }
            )
            FilterChip(
                selected = filter == VirtualPlaylistViewModel.Filter.VIRTUAL,
                onClick = { viewModel.setFilter(VirtualPlaylistViewModel.Filter.VIRTUAL) },
                label = { Text("Virtual") },
                leadingIcon = { Icon(Icons.Default.Link, null) }
            )
            FilterChip(
                selected = filter == VirtualPlaylistViewModel.Filter.IMPORTED,
                onClick = { viewModel.setFilter(VirtualPlaylistViewModel.Filter.IMPORTED) },
                label = { Text("Importado") },
                leadingIcon = { Icon(Icons.Default.Source, null) }
            )
        }

        if (channels.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Nenhum canal encontrado.")
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(channels) { channel ->
                    VirtualChannelItem(
                        channel = channel,
                        onClick = { onPlay(channel.id) },
                        onFavouriteClick = { viewModel.favourite(channel.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun VirtualChannelItem(
    channel: Channel,
    onClick: () -> Unit,
    onFavouriteClick: () -> Unit
) {
    val isVirtual = channel.playlistUrl == Playlist.URL_VIRTUAL
    
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!channel.cover.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(channel.cover)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isVirtual) Icons.Default.Link else Icons.Default.Source,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Column {
                Text(
                    text = channel.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1
                )
                Text(
                    text = if (isVirtual) "Virtual (Extracted)" else "Imported",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onFavouriteClick) {
                Icon(
                    if (channel.favourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = if (channel.favourite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}
