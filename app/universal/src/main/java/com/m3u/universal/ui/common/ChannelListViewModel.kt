package com.m3u.universal.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3u.data.database.model.Channel
import com.m3u.data.repository.playlist.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ChannelListViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository
) : ViewModel() {
    private val playlistUrl = MutableStateFlow<String?>(null)

    val channels: StateFlow<List<Channel>> = playlistUrl
        .flatMapLatest { url ->
            if (url.isNullOrBlank()) kotlinx.coroutines.flow.flowOf(emptyList())
            else playlistRepository.observePlaylistWithChannels(url).map { it?.channels ?: emptyList() }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    fun setPlaylistUrl(url: String?) {
        playlistUrl.value = url
    }
}
