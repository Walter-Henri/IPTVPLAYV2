package com.m3u.universal.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.m3u.core.foundation.JsonHeaderRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ChannelDataReceiver - Listens for "com.m3u.CHANNEL_DATA_READY" from APP 2.
 * Triggers loading of updated headers into JsonHeaderRegistry.
 */
@dagger.hilt.android.AndroidEntryPoint
class ChannelDataReceiver : BroadcastReceiver() {
    private val TAG = "ChannelDataReceiver"
    private val scope = CoroutineScope(Dispatchers.IO)

    @javax.inject.Inject
    lateinit var playlistRepository: com.m3u.data.repository.playlist.PlaylistRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.m3u.CHANNEL_DATA_READY") {
            val uri = intent.data
            if (uri != null) {
                Log.d(TAG, "Channel data ready received. URI: $uri")
                
                // Read content immediately (while permission is granted)
                scope.launch {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            val content = inputStream.bufferedReader().use { it.readText() }
                            
                            // 1. Reactive Header Storage (In-Memory)
                            JsonHeaderRegistry.loadFromJson(content)
                            
                            // 2. Persistent Merger (Database)
                            val importedCount = playlistRepository.importChannelsJsonBody(content)
                            Log.d(TAG, "Merged $importedCount channels into database.")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to process channel data: ${e.message}")
                    }
                }
            } else {
                Log.w(TAG, "Received CHANNEL_DATA_READY but URI is null.")
            }
        }
    }
}
