package com.m3u.universal.ui.extension

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.core.extension.IExtension
import com.m3u.core.extension.IExtensionCallback
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull

@HiltViewModel
class ExtensionIntegrationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistRepository: PlaylistRepository
) : ViewModel() {
    
    private val timber = Timber.tag("ExtensionIntegration")
    
    private val _extractedLinks = MutableStateFlow<List<ExtractedLink>>(emptyList())
    val extractedLinks: StateFlow<List<ExtractedLink>> = _extractedLinks.asStateFlow()
    
    private val _status = MutableStateFlow<ExtractionStatus>(ExtractionStatus.Idle)
    val status: StateFlow<ExtractionStatus> = _status.asStateFlow()
    
    /**
     * Fluxo completo: Baixa JSON do Dropbox, extrai links via extensão e adiciona à playlist virtual
     */
    fun syncFromDropbox() {
        viewModelScope.launch {
            _status.value = ExtractionStatus.Extracting("Solicitando sincronização à extensão...")
            try {
                // Call extension to sync
                val resultJson = syncChannelsAsync()
                
                if (resultJson != null) {
                    timber.d("JSON recebido, chamando importChannelsJsonBody")
                    val count = playlistRepository.importChannelsJsonBody(resultJson)
                    timber.d("importChannelsJsonBody retornou: $count canais importados")
                    
                    val fails = try {
                        val obj = Json.parseToJsonElement(resultJson).jsonObject
                        val channels = obj["channels"]?.jsonArray
                        channels?.count { it.jsonObject["success"]?.jsonPrimitive?.booleanOrNull == false } ?: 0
                    } catch (e: Exception) {
                        0
                    }

                    if (fails > 0) {
                        _status.value = ExtractionStatus.AddedToPlaylist("$count sucessos, $fails falhas")
                    } else {
                        _status.value = ExtractionStatus.AddedToPlaylist("$count canais atualizados")
                    }
                    timber.d("Sincronização concluída: $count links, $fails falhas")
                } else {
                    _status.value = ExtractionStatus.Error("Falha na sincronização")
                }
            } catch (e: Exception) {
                _status.value = ExtractionStatus.Error("Erro: ${e.message}")
                timber.e(e, "Erro no fluxo Dropbox")
            }
        }
    }

    private suspend fun syncChannelsAsync(): String? = withContext(Dispatchers.IO) {
        timber.d("Iniciando syncChannelsAsync")
        val deferred = CompletableDeferred<String?>()
        val intent = Intent("com.m3u.extension.ExtensionService")
        intent.setPackage("com.m3u.extension")
        
        val connection: ServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val self = this
                try {
                    val binder = IExtension.Stub.asInterface(service)
                    binder.syncChannels(object : IExtensionCallback.Stub() {
                        override fun onProgress(current: Int, total: Int, name: String) {
                             viewModelScope.launch {
                                _status.value = ExtractionStatus.Extracting("[$current/$total] $name")
                            }
                        }
                        override fun onResult(jsonResult: String?) {
                            timber.d("JSON recebido da extensão: ${jsonResult?.take(200)}")
                            deferred.complete(jsonResult)
                            context.unbindService(self)
                        }
                        override fun onError(message: String?) {
                            timber.e("Erro na extensão: $message")
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
                timber.e("Falha ao conectar com o serviço de extensão")
                deferred.complete(null)
            }
        } catch (e: Exception) {
            timber.e(e, "Exceção ao conectar com o serviço de extensão")
            deferred.completeExceptionally(e)
        }
        
        kotlinx.coroutines.delay(1500)
        deferred.await()
    }

    private suspend fun extractLinksAsync(json: String): String? = withContext(Dispatchers.IO) {
        val deferred = CompletableDeferred<String?>()
        
        val intent = Intent("com.m3u.extension.ExtensionService")
        intent.setPackage("com.m3u.extension")
        
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val currentConnection = this
                try {
                    val binder = IExtension.Stub.asInterface(service)
                    
                    val callback = object : IExtensionCallback.Stub() {
                        override fun onProgress(current: Int, total: Int, name: String) {
                            viewModelScope.launch {
                                _status.value = ExtractionStatus.Extracting("[$current/$total] Processando: $name")
                            }
                        }

                        override fun onResult(jsonResult: String?) {
                            deferred.complete(jsonResult)
                            context.unbindService(currentConnection)
                        }

                        override fun onError(message: String?) {
                            viewModelScope.launch {
                                _status.value = ExtractionStatus.Error(message ?: "Erro desconhecido")
                            }
                            deferred.complete(null)
                            context.unbindService(currentConnection)
                        }
                    }
                    
                    binder.extractLinksAsync(json, callback)
                    
                } catch (e: Exception) {
                    timber.e(e, "Erro ao iniciar extração assíncrona")
                    deferred.complete(null)
                    try { context.unbindService(currentConnection) } catch (err: Exception) {}
                }
            }
            
            override fun onServiceDisconnected(name: ComponentName?) {
                if (!deferred.isCompleted) deferred.complete(null)
            }
        }
        
        if (!context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
            deferred.complete(null)
        }
        
        deferred.await()
    }

    /**
     * Extrai link m3u8 de uma URL usando a extensão
     */
    fun extractLink(url: String, title: String = "") {
        viewModelScope.launch {
            _status.value = ExtractionStatus.Extracting(url)
            try {
                val resolvedUrl = resolveWithExtension(url)
                if (resolvedUrl != null) {
                    val link = ExtractedLink(
                        originalUrl = url,
                        resolvedUrl = resolvedUrl,
                        title = title.ifBlank { "Link extraído" },
                        timestamp = System.currentTimeMillis()
                    )
                    _extractedLinks.value = _extractedLinks.value + link
                    _status.value = ExtractionStatus.Success(resolvedUrl)
                    
                    // Adiciona automaticamente à playlist virtual conforme solicitado
                    playlistRepository.addVirtualStream(link.title, link.resolvedUrl)
                    
                    timber.d("Link extraído e adicionado: $resolvedUrl")
                } else {
                    _status.value = ExtractionStatus.Error("Não foi possível extrair o link")
                    timber.e("Falha ao extrair link de: $url")
                }
            } catch (e: Exception) {
                _status.value = ExtractionStatus.Error(e.message ?: "Erro desconhecido")
                timber.e(e, "Erro ao extrair link")
            }
        }
    }

    private suspend fun extractLinksFromExtension(json: String): List<String> = emptyList()
    
    /**
     * Adiciona um link extraído como canal virtual
     */
    fun addAsVirtualChannel(link: ExtractedLink) {
        viewModelScope.launch {
            try {
                val id = playlistRepository.addVirtualStream(
                    title = link.title,
                    url = link.resolvedUrl
                )
                _status.value = ExtractionStatus.AddedToPlaylist(link.title)
                timber.d("Canal virtual adicionado: ${link.title} (id: $id)")
            } catch (e: Exception) {
                _status.value = ExtractionStatus.Error("Erro ao adicionar canal: ${e.message}")
                timber.e(e, "Erro ao adicionar canal virtual")
            }
        }
    }

    /**
     * Reproduz o link imediatamente, adicionando à playlist virtual primeiro para garantir que tenha um ID
     */
    fun playLink(link: ExtractedLink, onPlay: (String) -> Unit) {
        viewModelScope.launch {
            try {
                _status.value = ExtractionStatus.Extracting("Preparando reprodução...")
                val id = playlistRepository.addVirtualStream(
                    title = link.title,
                    url = link.resolvedUrl
                )
                onPlay(id.toString())
            } catch (e: Exception) {
                _status.value = ExtractionStatus.Error("Erro ao reproduzir: ${e.message}")
            }
        }
    }
    
    /**
     * Remove um link da lista de extraídos
     */
    fun removeLink(link: ExtractedLink) {
        _extractedLinks.value = _extractedLinks.value - link
    }
    
    /**
     * Limpa todos os links extraídos
     */
    fun clearAllLinks() {
        _extractedLinks.value = emptyList()
        _status.value = ExtractionStatus.Idle
    }
    
    /**
     * Resolve URL usando a extensão via AIDL
     */
    private suspend fun resolveWithExtension(url: String): String? = withContext(Dispatchers.IO) {
        suspendCoroutine { continuation ->
            val intent = Intent("com.m3u.extension.ExtensionService")
            intent.setPackage("com.m3u.extension")
            
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    try {
                        val extension = IExtension.Stub.asInterface(service)
                        val resolved: String? = extension.resolve(url)
                        context.unbindService(this)
                        continuation.resume(resolved)
                    } catch (e: Exception) {
                        timber.e(e, "Erro ao conectar com extensão")
                        context.unbindService(this)
                        continuation.resume(null)
                    }
                }
                
                override fun onServiceDisconnected(name: ComponentName?) {
                    timber.w("Serviço de extensão desconectado")
                }
            }
            
            val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!bound) {
                timber.e("Falha ao conectar com serviço de extensão")
                continuation.resume(null)
            }
        }
    }
    
    data class ExtractedLink(
        val originalUrl: String,
        val resolvedUrl: String,
        val title: String,
        val timestamp: Long
    )
    
    sealed class ExtractionStatus {
        object Idle : ExtractionStatus()
        data class Extracting(val url: String) : ExtractionStatus()
        data class Success(val resolvedUrl: String) : ExtractionStatus()
        data class Error(val message: String) : ExtractionStatus()
        data class AddedToPlaylist(val title: String) : ExtractionStatus()
    }
}
