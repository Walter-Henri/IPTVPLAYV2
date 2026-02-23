package com.m3u.universal.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.m3u.core.foundation.IdentityRegistry
import com.m3u.core.foundation.JsonHeaderRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ChannelDataReceiver
 *
 * Handles two broadcasts sent by the Extension app:
 *
 * 1. com.m3u.CHANNEL_DATA_READY
 *    Payload: Uri (content URI with JSON body of extracted channel data)
 *    Action: reads content → updates JsonHeaderRegistry (in-memory) + imports to DB
 *
 * 2. com.m3u.IDENTITY_UPDATE
 *    Payload: extras (user_agent, cookies, po_token, visitor_data, client_version)
 *    Action: updates IdentityRegistry so the player uses the correct session tokens
 *    for goglevideo.com streams without needing to re-bind to the extension.
 */
@dagger.hilt.android.AndroidEntryPoint
class ChannelDataReceiver : BroadcastReceiver() {

    private val tag   = "ChannelDataReceiver"
    private val scope = CoroutineScope(Dispatchers.IO)

    @javax.inject.Inject
    lateinit var playlistRepository: com.m3u.data.repository.playlist.PlaylistRepository

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.m3u.CHANNEL_DATA_READY" -> handleChannelData(context, intent)
            "com.m3u.IDENTITY_UPDATE"    -> handleIdentityUpdate(intent)
            else -> Log.w(tag, "Unknown action: ${intent.action}")
        }
    }

    // ──────────────────────────────────────────────────────────────
    // CHANNEL DATA
    // ──────────────────────────────────────────────────────────────

    private fun handleChannelData(context: Context, intent: Intent) {
        val uri = intent.data
        if (uri == null) {
            Log.w(tag, "CHANNEL_DATA_READY received but URI is null")
            return
        }
        Log.d(tag, "CHANNEL_DATA_READY → URI: $uri")

        // Consume content in a goroutine (goAsync would expire too quickly for DB ops)
        scope.launch {
            try {
                val content = context.contentResolver
                    .openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }

                if (content.isNullOrBlank()) {
                    Log.w(tag, "Content from URI is blank")
                    return@launch
                }

                // 1. In-memory header registry (used instantly by PlayerManagerImpl)
                JsonHeaderRegistry.loadFromJson(content)
                Log.d(tag, "JsonHeaderRegistry updated")

                // 2. Persistent merge into DB playlist
                val imported = playlistRepository.importChannelsJsonBody(content)
                Log.d(tag, "Merged $imported channels into database")

            } catch (e: Exception) {
                Log.e(tag, "Failed to process channel data: ${e.message}", e)
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // IDENTITY UPDATE (tokens from YouTubeWebViewTokenManager)
    // ──────────────────────────────────────────────────────────────

    private fun handleIdentityUpdate(intent: Intent) {
        Log.d(tag, "IDENTITY_UPDATE received")

        // Delegate ALL parsing/storage to IdentityRegistry
        IdentityRegistry.applyBroadcast(intent)

        // Debug summary helps in logcat to verify the pipeline
        Log.d(tag, IdentityRegistry.debugSummary())
    }
}
