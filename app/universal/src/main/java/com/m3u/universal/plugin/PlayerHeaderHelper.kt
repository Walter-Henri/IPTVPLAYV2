package com.m3u.universal.plugin

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.m3u.common.ExtractionData

/**
 * PlayerHeaderHelper — Builds player instances with anti-403 headers
 * injected from the ExtractionData received via IPC.
 */
object PlayerHeaderHelper {

    private const val TAG = "PlayerHeaderHelper"

    /**
     * Builds an ExoPlayer configured with all headers from [ExtractionData]
     * to avoid 403 errors during HLS playback.
     */
    fun buildExoPlayerWithHeaders(
        context: Context,
        data: ExtractionData
    ): ExoPlayer {
        val headers = buildHeadersMap(data)

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(headers)
            .setUserAgent(data.userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS)
            .setReadTimeoutMs(DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS)

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)

        val mediaItem = MediaItem.Builder()
            .setUri(data.m3u8Url)
            .setMimeType(MimeTypes.APPLICATION_M3U8) // Force HLS
            .build()

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .also { it.setMediaItem(mediaItem) }
    }

    /**
     * Builds MPV command-line arguments with headers from [ExtractionData].
     */
    fun buildMpvArgs(data: ExtractionData): List<String> {
        val args = mutableListOf<String>()

        // User-Agent
        args += "--user-agent=${data.userAgent}"

        // Cookies
        if (data.cookies.isNotEmpty()) {
            args += "--http-header-fields=Cookie: ${data.cookies}"
        }

        // Additional headers from JSON
        val extraHeaders = deserializeHeaders(data.headersJson)
        extraHeaders.forEach { (key, value) ->
            if (key !in listOf("User-Agent", "Cookie")) {
                args += "--http-header-fields=$key: $value"
            }
        }

        args += data.m3u8Url
        return args
    }

    /**
     * Builds a complete headers map from ExtractionData, merging
     * explicit fields and the deserialized headersJson.
     */
    fun buildHeadersMap(data: ExtractionData): Map<String, String> {
        return buildMap {
            put("User-Agent", data.userAgent)
            if (data.cookies.isNotEmpty()) {
                put("Cookie", data.cookies)
            }
            // Merge additional headers from JSON
            deserializeHeaders(data.headersJson).forEach { (k, v) ->
                put(k, v)
            }
        }
    }

    private fun deserializeHeaders(headersJson: String): Map<String, String> {
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            Gson().fromJson(headersJson, type) ?: emptyMap()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to deserialize headers JSON: ${e.message}")
            emptyMap()
        }
    }
}
