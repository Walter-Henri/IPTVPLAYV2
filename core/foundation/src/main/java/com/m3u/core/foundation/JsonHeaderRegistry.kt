package com.m3u.core.foundation

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

/**
 * JsonHeaderRegistry - Reactive registry for channel-specific headers extracted from YouTube.
 * Stores headers extracted from channels2.json for dynamic injection into ExoPlayer.
 */
object JsonHeaderRegistry {
    private const val TAG = "JsonHeaderRegistry"
    
    // Key: m3u8_url or partial identifier, Value: Map of headers
    private val headerMap = ConcurrentHashMap<String, Map<String, String>>()

    data class ChannelHeader(
        val name: String,
        val m3u8: String,
        val headers: Map<String, String>
    )

    data class ChannelsData(
        val channels: List<ChannelHeader>
    )

    suspend fun loadFromUri(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Loading headers from URI: $uri")
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val content = inputStream.bufferedReader().use { it.readText() }
                loadFromJson(content)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load headers from URI: ${e.message}")
        }
    }

    fun loadFromJson(json: String) {
        try {
            val data = Gson().fromJson(json, ChannelsData::class.java)
            data.channels.forEach { channel ->
                if (channel.m3u8.isNotEmpty()) {
                    headerMap[channel.m3u8] = channel.headers
                }
            }
            Log.d(TAG, "Successfully loaded ${data.channels.size} channel headers into registry.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON into registry: ${e.message}")
        }
    }

    /**
     * Retrieves headers for a specific URL if registered.
     */
    fun getHeadersForUrl(url: String): Map<String, String>? {
        // Try exact match first
        headerMap[url]?.let { return it }
        
        // Try partial match (some URLs might have dynamic params added later)
        // For signed URLs, we check if the base part matches
        val baseUri = url.substringBefore("?")
        headerMap.keys().asSequence().find { it.substringBefore("?") == baseUri }?.let {
            return headerMap[it]
        }
        
        return null
    }

    /**
     * Manually updates headers for a specific URL.
     * Useful for UA rotation during 403 retries.
     */
    fun setHeadersForUrl(url: String, headers: Map<String, String>) {
        headerMap[url] = headers
    }

    /**
     * Clear registry when needed.
     */
    fun clear() {
        headerMap.clear()
    }
}
