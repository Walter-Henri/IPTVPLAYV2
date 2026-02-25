package com.m3u.plugin

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.google.gson.Gson
import com.m3u.common.ExtractionData
import com.m3u.common.IExtractionCallback
import com.m3u.common.IExtractorService
import com.m3u.plugin.newpipe.NewPipeExDownloader
import com.m3u.plugin.newpipe.NewPipeResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * ExtractorService — Headless AIDL service implementing [IExtractorService].
 *
 * This service extracts YouTube live streams via NewPipe Extractor and delivers
 * the playback data (M3U8 URL + headers) to the host app through AIDL/Binder IPC.
 *
 * It is protected by the `com.m3u.permission.BIND_EXTRACTOR` permission and has
 * no launcher activity — it is invisible to the user.
 *
 * Contract version: 1
 */
class ExtractorService : Service() {

    companion object {
        private const val TAG = "ExtractorService"
        private const val CONTRACT_VERSION = 1
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ExtractorService created — initializing NewPipe Extractor")
        try {
            NewPipe.init(NewPipeExDownloader())
            Log.i(TAG, "NewPipe Extractor initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize NewPipe Extractor", e)
        }
    }

    /**
     * Extracts the HLS live stream URL from a YouTube live.
     *
     * @param youtubeUrl Full YouTube URL (e.g. https://www.youtube.com/watch?v=XXXX)
     * @return [ExtractionData] with m3u8Url, userAgent, cookies and captured headers
     */
    private suspend fun extractLiveStream(youtubeUrl: String): ExtractionData {
        return withContext(Dispatchers.IO) {
            // Clear previous headers before each extraction
            NewPipeExDownloader.clearCapturedHeaders()

            val streamInfo = StreamInfo.getInfo(
                NewPipe.getService(ServiceList.YouTube.serviceId),
                youtubeUrl
            )

            // For lives: prefer HLS stream
            val hlsUrl = streamInfo.hlsUrl
                ?: streamInfo.videoStreams
                    .firstOrNull { !it.isVideoOnly }
                    ?.content
                ?: throw ExtractionException("No stream URL available for: $youtubeUrl")

            // Collect headers captured by the interceptor during the handshake
            val capturedHeaders = NewPipeExDownloader.lastCapturedHeaders
            val headersMap = capturedHeaders.toMap()
            val headersJson = Gson().toJson(headersMap)

            Log.d(TAG, "Extraction successful: ${hlsUrl.take(80)}...")
            Log.d(TAG, "Captured ${headersMap.size} headers for anti-403")

            ExtractionData(
                m3u8Url = hlsUrl,
                userAgent = capturedHeaders["User-Agent"] ?: NewPipeExDownloader.DEFAULT_USER_AGENT,
                cookies = capturedHeaders["Cookie"] ?: "",
                headersJson = headersJson
            )
        }
    }

    private val binder = object : IExtractorService.Stub() {

        override fun extractStream(youtubeUrl: String, callback: IExtractionCallback) {
            Log.d(TAG, "extractStream called for: $youtubeUrl")
            scope.launch {
                try {
                    // Proactive check: if we have NO identity tokens, trigger refresh
                    if (!com.m3u.core.foundation.IdentityRegistry.hasValidIdentity()) {
                        Log.d(TAG, "Missing identity tokens, triggering proactive WebView refresh")
                        com.m3u.plugin.youtube.YouTubeWebViewTokenManager.refresh(applicationContext)
                    }

                    val data = extractLiveStream(youtubeUrl)
                    callback.onSuccess(data)
                } catch (e: Exception) {
                    Log.e(TAG, "Extraction failed for $youtubeUrl", e)

                    // On failure, trigger token refresh as a reactive measure
                    Log.w(TAG, "Extraction failure, triggering WebView refresh for next attempt")
                    com.m3u.plugin.youtube.YouTubeWebViewTokenManager.refresh(applicationContext)

                    callback.onError(e.message ?: "Unknown extraction error")
                }
            }
        }

        override fun getVersion(): Int = CONTRACT_VERSION
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "onBind: $intent")
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        Log.d(TAG, "ExtractorService destroyed")
    }
}

/** Custom exception for extraction failures */
class ExtractionException(message: String) : Exception(message)
