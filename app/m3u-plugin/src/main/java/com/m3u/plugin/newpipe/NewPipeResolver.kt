package com.m3u.plugin.newpipe

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * NewPipeResolver - Native YouTube Extractor
 * Reliable and fast extraction using NewPipe Extractor library.
 */
object NewPipeResolver {
    private const val TAG = "NewPipeResolver"
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        try {
            NewPipe.init(NewPipeExDownloader())
            isInitialized = true
            Log.i(TAG, "NewPipe Extractor inicializado com sucesso.")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao inicializar NewPipe Extractor", e)
        }
    }
    
    suspend fun resolve(url: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Normalize URLs like @handle/live or youtube.com/user/live
            var targetUrl = url
            if (url.contains("/live") && !url.contains("watch?v=")) {
                Log.d(TAG, "Detectada URL /live, normalizando para extração...")
            }

            val service = org.schabi.newpipe.extractor.ServiceList.YouTube
            val extractor = service.getStreamExtractor(targetUrl)
            extractor.fetchPage()
            
            // For live streams, it's critical to check the stream type
            val hlsUrl = extractor.hlsUrl
            if (!hlsUrl.isNullOrBlank()) {
                Log.d(TAG, "✓ HLS Stream found: ${hlsUrl?.take(60)}...")
                return@withContext Result.success(hlsUrl!!)
            }
            
            // Fallback for non-HLS or misdetected live streams
            val streams = extractor.videoStreams
            val stream = streams.firstOrNull { it.format?.suffix == "m3u8" }
                ?: streams.sortedByDescending { it.bitrate }.firstOrNull()
                
            if (stream == null) {
                return@withContext Result.failure(Exception("Nenhum stream encontrado para esta URL"))
            }
                
            Log.d(TAG, "✓ Stream found: ${stream.url?.take(60)}...")
            Result.success(stream.url ?: "")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao resolver URL via NewPipe: $url", e)
            Result.failure(e)
        }
    }
}
