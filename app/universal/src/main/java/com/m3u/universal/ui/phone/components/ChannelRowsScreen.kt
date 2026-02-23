package com.m3u.universal.ui.phone.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.m3u.data.service.PlaybackManager
import com.m3u.universal.ui.common.ChannelBrowseViewModel

@Composable
fun ChannelRowsScreen(
    playbackManager: PlaybackManager,
    activePlaylistUrl: String?,
    modifier: Modifier = Modifier
) {
    val viewModel: ChannelBrowseViewModel = hiltViewModel()
    LaunchedEffect(activePlaylistUrl) { viewModel.setPlaylistUrl(activePlaylistUrl) }
    
    val categories by viewModel.categories.collectAsState()
    val channelsMap by viewModel.channelsByCategory.collectAsState()
    var queryText by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("Todos") }

    Column(modifier = modifier.fillMaxSize()) {
        // Barra de busca minimalista
        OutlinedTextField(
            value = queryText,
            onValueChange = { queryText = it; viewModel.setQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { 
                Text(
                    "Buscar em ${selectedCategory.lowercase()}...", 
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                ) 
            },
            leadingIcon = { 
                Icon(
                    Icons.Default.Search, 
                    null, 
                    modifier = Modifier.size(20.dp),
                    tint = PremiumColors.Accent
                ) 
            },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                focusedBorderColor = PremiumColors.Accent.copy(alpha = 0.5f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                focusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            singleLine = true
        )

        // Chips de categoria minimalistas
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                com.m3u.core.foundation.ui.components.PremiumCategoryChip(
                    text = "Todos",
                    selected = selectedCategory == "Todos",
                    onClick = { selectedCategory = "Todos" }
                )
            }
            items(categories) { cat ->
                com.m3u.core.foundation.ui.components.PremiumCategoryChip(
                    text = cat,
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat }
                )
            }
        }

        val filteredChannels = remember(selectedCategory, queryText, channelsMap) {
            val base = if (selectedCategory == "Todos") {
                channelsMap.values.flatten()
            } else {
                channelsMap[selectedCategory].orEmpty()
            }
            base.filter { it.title.contains(queryText, ignoreCase = true) }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredChannels) { channel ->
                PremiumChannelCard(
                    channel = channel,
                    onClick = { playbackManager.launchPlayerActivity(channel.id, fullScreen = true) }
                )
            }
        }
    }
}

@Composable
private fun PremiumChannelCard(
    channel: com.m3u.data.database.model.Channel,
    onClick: () -> Unit
) {
    com.m3u.core.foundation.ui.components.AdaptiveChannelCard(
        title = channel.title,
        category = channel.category,
        logoUrl = channel.cover,
        isPlaying = false,
        onClick = onClick
    )
}
