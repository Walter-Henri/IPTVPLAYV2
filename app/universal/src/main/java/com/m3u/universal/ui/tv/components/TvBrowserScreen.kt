package com.m3u.universal.ui.tv.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import com.m3u.core.foundation.ui.PremiumColors
import com.m3u.universal.ui.common.ChannelBrowseViewModel

@Composable
fun TvBrowserScreen(
    playbackManager: com.m3u.data.service.PlaybackManager,
    activePlaylistUrl: String?,
    modifier: Modifier = Modifier
) {
    val viewModel: ChannelBrowseViewModel = hiltViewModel()
    LaunchedEffect(activePlaylistUrl) { viewModel.setPlaylistUrl(activePlaylistUrl) }
    
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val filteredChannels by viewModel.filteredChannels.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    
    var focusedChannel by remember { mutableStateOf<com.m3u.data.database.model.Channel?>(null) }

    Row(modifier = modifier.fillMaxSize()) {
        // Rail de Categorias (Sidebar)
        Column(
            modifier = Modifier
                .width(260.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "M3U PLAY",
                style = MaterialTheme.typography.titleLarge,
                color = PremiumColors.Accent,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Text(
                "CATEGORIAS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    com.m3u.core.foundation.ui.components.PremiumCategoryChip(
                        text = "Todos",
                        selected = selectedCategory == "Todos",
                        onClick = { viewModel.setCategory("Todos") },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    )
                }
                items(categories) { category ->
                    com.m3u.core.foundation.ui.components.PremiumCategoryChip(
                        text = category,
                        selected = selectedCategory == category,
                        onClick = { viewModel.setCategory(category) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    )
                }
            }
        }

        // Conteúdo Principal (Hero + Grid)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // Hero Banner
            TvHeroBanner(channel = focusedChannel ?: filteredChannels.firstOrNull())

            // Grade de Canais
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
            ) {
                Text(
                    text = selectedCategory.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = PremiumColors.Accent,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(Modifier.height(16.dp))

                if (filteredChannels.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Nenhum canal nesta categoria", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(filteredChannels.size) { index ->
                            val channel = filteredChannels[index]
                            com.m3u.core.foundation.ui.components.AdaptiveChannelCard(
                                title = channel.title,
                                category = channel.category,
                                logoUrl = channel.cover,
                                modifier = Modifier.onFocusChanged { if (it.isFocused) focusedChannel = channel },
                                onClick = { 
                                    playbackManager.launchPlayerActivity(channel.id, fullScreen = true) 
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
