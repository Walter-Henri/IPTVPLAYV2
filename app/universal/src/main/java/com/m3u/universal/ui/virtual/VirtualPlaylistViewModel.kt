package com.m3u.universal.ui.virtual

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.ComponentName
import android.os.IBinder
import com.m3u.core.plugin.IPlugin
import com.m3u.core.plugin.IPluginCallback
import com.m3u.data.database.dao.ChannelDao
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist
import com.m3u.data.repository.playlist.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VirtualPlaylistViewModel @Inject constructor(
    private val channelDao: ChannelDao,
    private val playlistRepository: PlaylistRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _filter = MutableStateFlow(Filter.ALL)
    val filter = _filter.asStateFlow()
    private val _status = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)
    val status = _status.asStateFlow()

    val channels = combine(
        channelDao.observeAllByPlaylistUrl(Playlist.URL_VIRTUAL),
        channelDao.observeAllUnhidden(),
        filter
    ) { virtual, all, currentFilter ->
        // Funde os canais virtuais (extraídos) com os canais importados pelo usuário
        val imported = all.filter { it.playlistUrl != Playlist.URL_VIRTUAL }

        when (currentFilter) {
            Filter.ALL -> (virtual + imported).sortedBy { it.title }
            Filter.VIRTUAL -> virtual.sortedBy { it.title }
            Filter.IMPORTED -> imported.sortedBy { it.title }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = emptyList()
    )

    fun setFilter(f: Filter) {
        _filter.value = f
    }

    fun downloadChannelsJson() {
        viewModelScope.launch {
            _status.value = DownloadStatus.InProgress(0)
            try {
                // Now using Plugin via AIDL
                val resultJson = syncChannelsAsync()
                if (resultJson != null) {
                     val count = playlistRepository.importChannelsJsonBody(resultJson)
                     _status.value = DownloadStatus.Success(count)
                } else {
                     _status.value = DownloadStatus.Error("Falha na sincronização com extensão")
                }
            } catch (e: Exception) {
                val msg = e.message ?: "Erro desconhecido"
                _status.value = DownloadStatus.Error(msg)
            }
        }
    }

    private suspend fun syncChannelsAsync(): String? = withContext(Dispatchers.IO) {
        val deferred = CompletableDeferred<String?>()
        val intent = Intent("com.m3u.plugin.PluginService")
        intent.setPackage("com.m3u.plugin")
        
        val connection: ServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val self = this
                try {
                    val binder = IPlugin.Stub.asInterface(service)
                    binder.syncChannels(object : IPluginCallback.Stub() {
                        override fun onProgress(current: Int, total: Int, name: String) {
                             viewModelScope.launch {
                                 _status.value = DownloadStatus.InProgress(current)
                             }
                        }
                        override fun onResult(jsonResult: String?) {
                            deferred.complete(jsonResult)
                            context.unbindService(self)
                        }
                        override fun onError(message: String?) {
                            deferred.complete(null)
                            context.unbindService(self)
                        }
                    })
                } catch (e: Exception) {
                    deferred.completeExceptionally(e)
                    try { context.unbindService(self) } catch(_: Exception) {}
                }
            }
            override fun onServiceDisconnected(name: ComponentName?) {}
        }
        
        try {
            val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!bound) {
                deferred.complete(null)
            }
        } catch (e: Exception) {
            deferred.completeExceptionally(e)
        }
        
        deferred.await()
    }

    fun favourite(id: Int) {
        viewModelScope.launch {
            val channel = channelDao.get(id) ?: return@launch
            channelDao.favouriteOrUnfavourite(id, !channel.favourite)
        }
    }

    enum class Filter {
        ALL, VIRTUAL, IMPORTED
    }

    sealed class DownloadStatus {
        data object Idle : DownloadStatus()
        data class InProgress(val progress: Int) : DownloadStatus()
        data class Success(val count: Int) : DownloadStatus()
        data class Error(val message: String) : DownloadStatus()
    }
}
