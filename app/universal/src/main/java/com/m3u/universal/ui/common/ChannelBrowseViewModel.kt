package com.m3u.universal.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3u.data.database.model.Channel
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.core.mediaresolver.MediaResolver
import com.m3u.core.mediaresolver.StreamFormat
import com.m3u.data.database.model.Playlist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChannelBrowseViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val mediaResolver: MediaResolver
) : ViewModel() {
    private val playlistUrl = MutableStateFlow<String?>(null)
    private val query = MutableStateFlow("")

    val categories: StateFlow<List<String>> = combine(playlistUrl, query) { url, query -> 
        url to query 
    }.flatMapLatest { (url, query) ->
        if (url.isNullOrBlank()) kotlinx.coroutines.flow.flowOf(emptyList())
        else playlistRepository.observeCategoriesByPlaylistUrlIgnoreHidden(url, query)
            .map { cats -> cats.filter { it.isNotBlank() }.distinct() }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    val channelsByCategory: StateFlow<Map<String, List<Channel>>> = playlistUrl.flatMapLatest { url ->
        if (url.isNullOrBlank()) kotlinx.coroutines.flow.flowOf(emptyMap())
        else {
            combine(
                playlistRepository.observePlaylistWithChannels(url),
                playlistRepository.observePlaylistWithChannels(Playlist.URL_VIRTUAL)
            ) { current, virtual ->
                val currentChannels = current?.channels.orEmpty()
                val virtualChannels = virtual?.channels.orEmpty().map { it.copy(category = "Playlist Virtual") }
                val all = currentChannels + virtualChannels
                all.groupBy { it.category }
            }
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyMap()
        )

    private val _selectedCategory = MutableStateFlow("Todos")
    val selectedCategory: StateFlow<String> = _selectedCategory

    private val _sortOrder = MutableStateFlow(SortOrder.ASC)
    val sortOrder: StateFlow<SortOrder> = _sortOrder

    enum class SortOrder {
        ASC, DESC
    }

    val filteredChannels: StateFlow<List<Channel>> = combine(
        channelsByCategory,
        _selectedCategory,
        query,
        _sortOrder
    ) { map, category, queryText, sort ->
        val list = when (category) {
            "Todos" -> map.values.flatten()
            else -> map[category].orEmpty()
        }
        val filtered = if (queryText.isBlank()) list
        else list.filter { it.title.contains(queryText, ignoreCase = true) }
        
        when (sort) {
            SortOrder.ASC -> filtered.sortedBy { it.title }
            SortOrder.DESC -> filtered.sortedByDescending { it.title }
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    fun setPlaylistUrl(url: String?) {
        playlistUrl.value = url
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    fun toggleSortOrder() {
        _sortOrder.value = if (_sortOrder.value == SortOrder.ASC) SortOrder.DESC else SortOrder.ASC
    }
}
