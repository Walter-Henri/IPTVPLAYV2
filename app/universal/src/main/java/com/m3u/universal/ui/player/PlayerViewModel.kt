package com.m3u.universal.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3u.data.database.model.AdjacentChannels
import com.m3u.data.database.model.Channel
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.data.service.MediaCommand
import com.m3u.data.service.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUIState(
    val channel: Channel? = null,
    val adjacent: AdjacentChannels? = null,
    val miniGuideChannels: List<Channel> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val playerManager: PlayerManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUIState())
    val uiState: StateFlow<PlayerUIState> = _uiState.asStateFlow()

    fun loadChannel(channelId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            launch {
                channelRepository.observe(channelId).collectLatest { channel ->
                    if (channel != null) {
                        _uiState.update { it.copy(channel = channel) }
                    }
                }
            }

            val channel = channelRepository.get(channelId)
            if (channel == null) {
                _uiState.update { it.copy(isLoading = false, error = "Canal não encontrado") }
                return@launch
            }

            launch {
                channelRepository.observeAdjacentChannels(
                    channelId = channel.id,
                    playlistUrl = channel.playlistUrl,
                    category = channel.category
                ).collectLatest { adj ->
                    _uiState.update { it.copy(adjacent = adj) }
                }
            }

            launch {
                val all = channelRepository.getByPlaylistUrl(channel.playlistUrl)
                _uiState.update { it.copy(miniGuideChannels = all.filter { ch -> ch.category == channel.category }) }
            }

            try {
                playerManager.play(MediaCommand.Common(channel.id))
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Erro ao iniciar reprodução: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onFavourite() {
        viewModelScope.launch {
            val channel = _uiState.value.channel ?: return@launch
            channelRepository.favouriteOrUnfavourite(channel.id)
        }
    }

    fun onFavourite(id: Int) {
        viewModelScope.launch {
            channelRepository.favouriteOrUnfavourite(id)
        }
    }

    fun playAdjacent(next: Boolean) {
        val currentAdj = _uiState.value.adjacent
        val targetId = if (next) currentAdj?.nextId else currentAdj?.prevId
        if (targetId != null) {
            loadChannel(targetId)
        }
    }
}
