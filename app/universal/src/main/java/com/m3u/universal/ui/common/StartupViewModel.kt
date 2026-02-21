package com.m3u.universal.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.core.architecture.preferences.AutoResumePreferences
import com.m3u.core.architecture.preferences.Settings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.m3u.data.database.model.Playlist
import kotlinx.coroutines.flow.map

@HiltViewModel
class StartupViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val playlistRepository: PlaylistRepository,
    private val settings: Settings
) : ViewModel() {
    private val _state = MutableStateFlow(StartupState())
    val state: StateFlow<StartupState> = _state.asStateFlow()
    val playlists: StateFlow<List<Playlist>> = playlistRepository
        .observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            val all = playlistRepository.getAll()
            val hasPlaylists = all.isNotEmpty()
            val preferred = settings.get(AutoResumePreferences.LAST_PLAYLIST_URL)
            val activeUrl = when {
                !preferred.isNullOrBlank() && all.any { it.url == preferred } -> preferred
                else -> all.firstOrNull()?.url
            }
            _state.update { it.copy(isChecking = false, hasPlaylists = hasPlaylists, activePlaylistUrl = activeUrl) }
        }
    }

    fun addVirtualStream(title: String, url: String) {
        viewModelScope.launch {
            val trimmedUrl = url.trim()
            if (trimmedUrl.isBlank()) return@launch
            try {
                playlistRepository.addVirtualStream(title, trimmedUrl)
                // Switch to virtual playlist?
                // settings.update(AutoResumePreferences.LAST_PLAYLIST_URL, Playlist.URL_VIRTUAL)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun importFromUrl(title: String, url: String) {
        viewModelScope.launch {
            val trimmedUrl = url.trim()
            if (trimmedUrl.isBlank()) {
                _state.update { it.copy(error = "URL não pode ser vazia") }
                return@launch
            }
            val lower = trimmedUrl.lowercase()
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
                _state.update {
                    it.copy(
                        error = "URL inválida. Use um link HTTP ou HTTPS."
                    )
                }
                return@launch
            }
            _state.update { it.copy(isImporting = true, error = null) }
            try {
                val finalTitle = if (title.isBlank()) trimmedUrl else title
                playlistRepository.m3uOrThrow(finalTitle, trimmedUrl)
                settings.update(AutoResumePreferences.LAST_PLAYLIST_URL, trimmedUrl)
                _state.update {
                    it.copy(
                        isImporting = false,
                        hasPlaylists = true,
                        error = null,
                        activePlaylistUrl = trimmedUrl
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isImporting = false,
                        error = e.message ?: "Erro ao importar lista"
                    )
                }
            }
        }
    }

    fun importFromFile(title: String, uri: String) {
        viewModelScope.launch {
            val trimmedUri = uri.trim()
            if (trimmedUri.isBlank()) {
                _state.update { it.copy(error = "Arquivo inválido") }
                return@launch
            }
            _state.update { it.copy(isImporting = true, error = null) }
            try {
                val finalTitle = if (title.isBlank()) "Minha lista" else title
                playlistRepository.m3uOrThrow(finalTitle, trimmedUri)
                settings.update(AutoResumePreferences.LAST_PLAYLIST_URL, trimmedUri)
                _state.update {
                    it.copy(
                        isImporting = false,
                        hasPlaylists = true,
                        error = null,
                        activePlaylistUrl = trimmedUri
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isImporting = false,
                        error = e.message ?: "Erro ao importar lista"
                    )
                }
            }
        }
    }

    fun setActivePlaylist(url: String) {
        viewModelScope.launch {
            settings.update(AutoResumePreferences.LAST_PLAYLIST_URL, url)
            playlistRepository.updateActive(url)
            _state.update { it.copy(activePlaylistUrl = url) }
        }
    }

    fun importChannelsJsonBody(body: String) {
        viewModelScope.launch {
            try {
                val count = playlistRepository.importChannelsJsonBody(body)
                if (count > 0) {
                    val all = playlistRepository.getAll()
                    val userPlaylist = all.firstOrNull { it.source != com.m3u.data.database.model.DataSource.EPG && !it.url.startsWith("virtual:") }
                    val playlistUrl = userPlaylist?.url ?: "virtual:channels_json"
                    setActivePlaylist(playlistUrl)
                    _state.update { it.copy(hasPlaylists = true, error = null) }
                } else {
                    _state.update { it.copy(error = "Nenhum canal importado") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Erro ao importar canais") }
            }
        }
    }
}

data class StartupState(
    val isChecking: Boolean = true,
    val hasPlaylists: Boolean = false,
    val isImporting: Boolean = false,
    val error: String? = null,
    val activePlaylistUrl: String? = null
)
