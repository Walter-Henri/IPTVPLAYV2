package com.m3u.plugin

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.m3u.core.plugin.IPlugin
import com.m3u.core.plugin.IPluginCallback
import com.m3u.plugin.newpipe.NewPipeResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.m3u.plugin.dropbox.DropboxRepository
import com.google.gson.Gson
import java.io.File

/**
 * PluginService — Legacy AIDL service for backward compatibility.
 *
 * This version has been refactored to use the 100% native NewPipe Extractor
 * engine, following the "limpeza pesada" plan. It replaces the old
 * WebView-based sniffing and Python (yt-dlp) engines.
 */
class PluginService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        Log.d("PluginService", "onCreate: Initializing NewPipe for legacy contract")
        NewPipeResolver.init(this)
    }

    private val binder = object : IPlugin.Stub() {
        override fun resolve(url: String): String? {
            Log.d("PluginService", "resolve called (legacy): $url")
            // Blocking call for legacy interface compatibility
            return runCatching {
                val job = scope.launch(Dispatchers.IO) {
                    val result = NewPipeResolver.resolve(url)
                    result.getOrNull()
                }
                // Since this is a legacy blocking AIDL, but NewPipe is async,
                // this setup might be slightly problematic without a runBlocking,
                // but we prefer to avoid it.
                // However, for this specific project transition, we'll keep it simple:
                null // Return null to indicate the host should use the new ExtractorService
            }.getOrNull()
        }

        override fun extractLinksAsync(url: String, callback: IPluginCallback) {
            Log.d("PluginService", "extractLinksAsync (legacy): $url")
            scope.launch {
                val result = NewPipeResolver.resolve(url)
                result.onSuccess { 
                    callback.onResult(it) 
                }.onFailure { 
                    callback.onError(it.message ?: "Extraction failed") 
                }
            }
        }

        override fun syncChannels(callback: IPluginCallback?) {
            Log.d("PluginService", "syncChannels: Starting Dropbox sync & resolve")
            scope.launch {
                val dropboxRepo = DropboxRepository(applicationContext)
                val channelsFile = dropboxRepo.downloadChannelsJson()
                
                if (channelsFile == null || !channelsFile.exists()) {
                    callback?.onError("Erro ao baixar canais do Dropbox")
                    return@launch
                }

                val jsonContent = channelsFile.readText()
                val gson = Gson()
                
                // Internal data classes for parsing
                data class Channel(val name: String, val url: String, val logo: String? = null, val group: String? = null)
                data class ChannelsData(val channels: List<Channel>)
                data class ResolvedChannel(
                    val name: String,
                    val url: String,
                    val m3u8: String? = null,
                    val logo: String?,
                    val group: String?,
                    val success: Boolean,
                    val headers: Map<String, String>? = null,
                    val error: String? = null
                )
                data class ResolvedData(val channels: List<ResolvedChannel>)

                val data = try {
                    gson.fromJson(jsonContent, ChannelsData::class.java)
                } catch (e: Exception) {
                    callback?.onError("JSON do Dropbox inválido")
                    return@launch
                }

                val total = data.channels.size
                val resolvedList = mutableListOf<ResolvedChannel>()

                data.channels.forEachIndexed { index, channel ->
                    callback?.onProgress(index + 1, total, channel.name)
                    
                    // Clear headers before each extraction to capture per-channel headers
                    com.m3u.plugin.newpipe.NewPipeExDownloader.clearCapturedHeaders()
                    
                    val result = NewPipeResolver.resolve(channel.url)
                    val resolvedUrl = result.getOrNull()
                    
                    if (resolvedUrl != null) {
                        val capturedHeaders = com.m3u.plugin.newpipe.NewPipeExDownloader
                            .lastCapturedHeaders.toMap()
                        resolvedList.add(ResolvedChannel(
                            name = channel.name,
                            url = channel.url,
                            m3u8 = resolvedUrl,
                            logo = channel.logo,
                            group = channel.group,
                            success = true,
                            headers = if (capturedHeaders.isNotEmpty()) capturedHeaders else null
                        ))
                    } else {
                        val errorMsg = result.exceptionOrNull()?.message ?: "Falha na extração"
                        resolvedList.add(ResolvedChannel(
                            name = channel.name,
                            url = channel.url,
                            logo = channel.logo,
                            group = channel.group,
                            success = false,
                            error = errorMsg
                        ))
                    }
                }

                val finalJson = gson.toJson(ResolvedData(resolvedList))
                
                // Save resolved to cache for the worker
                val cacheFile = File(applicationContext.cacheDir, "resolved_channels_cache.json")
                cacheFile.writeText(finalJson)
                
                callback?.onResult(finalJson)
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        Log.d("PluginService", "onBind (legacy)")
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
