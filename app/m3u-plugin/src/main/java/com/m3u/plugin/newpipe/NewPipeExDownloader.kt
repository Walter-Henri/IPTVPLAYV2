package com.m3u.plugin.newpipe

import android.util.Log
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * NewPipeExDownloader — OkHttp-based Downloader for NewPipe Extractor.
 *
 * Includes a [HeaderInterceptor] that captures request/response headers from
 * `googlevideo.com` domains. These captured headers are essential for the host
 * player to avoid 403 errors during playback.
 */
class NewPipeExDownloader : Downloader() {

    /**
     * Companion object acting as a header interceptor/store.
     * Captures the last successful headers from googlevideo.com requests.
     */
    companion object HeaderInterceptor {
        private const val TAG = "NewPipeExDownloader"

        /** Thread-safe map of the last captured headers */
        val lastCapturedHeaders: ConcurrentHashMap<String, String> = ConcurrentHashMap()

        private val CAPTURE_KEYS = setOf(
            "Cookie", "User-Agent", "Origin", "Referer", "X-Goog-Visitor-Id"
        )

        /** Default User-Agent fallback */
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

        /** Clears the stored headers */
        fun clearCapturedHeaders() {
            lastCapturedHeaders.clear()
        }
    }

    private val headerCaptureInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)

        // Capture headers from googlevideo.com (where 403 typically occurs)
        val host = request.url.host
        if (host.contains("googlevideo.com") && response.isSuccessful) {
            Log.d(TAG, "Capturing headers from googlevideo.com request")

            // Capture request headers (what was sent)
            for (key in CAPTURE_KEYS) {
                val value = request.header(key)
                if (value != null) {
                    lastCapturedHeaders[key] = value
                }
            }

            // Also capture Set-Cookie from response for future requests
            val setCookies = response.headers("Set-Cookie")
            if (setCookies.isNotEmpty()) {
                val existingCookies = lastCapturedHeaders["Cookie"] ?: ""
                val newCookies = setCookies.joinToString("; ") { it.substringBefore(";") }
                lastCapturedHeaders["Cookie"] = if (existingCookies.isNotEmpty()) {
                    "$existingCookies; $newCookies"
                } else {
                    newCookies
                }
            }

            // Capture X-Goog-Visitor-Id from response headers if present
            response.header("X-Goog-Visitor-Id")?.let {
                lastCapturedHeaders["X-Goog-Visitor-Id"] = it
            }

            Log.d(TAG, "Captured ${lastCapturedHeaders.size} header keys from googlevideo.com")
        }

        // Always store the User-Agent from every request as fallback
        request.header("User-Agent")?.let { ua ->
            if (!lastCapturedHeaders.containsKey("User-Agent")) {
                lastCapturedHeaders["User-Agent"] = ua
            }
        }

        response
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(headerCaptureInterceptor)
        .build()

    @Throws(IOException::class)
    override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBuilder = Request.Builder().url(url)

        val headerMap = headers.mapValues { it.value.firstOrNull() ?: "" }.toMutableMap()
        com.m3u.core.foundation.IdentityRegistry.applyTo(headerMap, url)

        headerMap.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }

        if (httpMethod.equals("POST", ignoreCase = true)) {
            val body = dataToSend?.toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull())
            requestBuilder.post(body ?: "".toRequestBody(null))
        } else {
            requestBuilder.get()
        }

        val okResponse = client.newCall(requestBuilder.build()).execute()
        val responseBody = okResponse.body?.string() ?: ""
        val responseHeaders = okResponse.headers.toMultimap()

        return Response(okResponse.code, okResponse.message, responseHeaders, responseBody, okResponse.request.url.toString())
    }
}
