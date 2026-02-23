package com.m3u.extension.logic.resolvers

import android.content.Context
import com.m3u.extension.youtube.YouTubeExtractorV2

/**
 * Resolvedor YouTube V2 que utiliza o extrator robusto (yt-dlp multi-client + WebView tokens).
 * Substitui ou complementa o YouTubeProMaxResolver.
 */
class YouTubeV2Resolver(private val context: Context) : StreamResolver {

    private val extractor by lazy { YouTubeExtractorV2(context) }

    override fun canResolve(url: String): Boolean {
        val lowercaseUrl = url.lowercase()
        return lowercaseUrl.contains("youtube.com") || 
               lowercaseUrl.contains("youtu.be")
    }

    override suspend fun resolve(url: String): Result<String> {
        return try {
            val result = extractor.extractChannel(
                name = "Live Stream",
                url = url,
                forceRefresh = false
            )

            if (result.success && result.m3u8Url != null) {
                // Formata a URL no padrão Kodi (|Headers)
                val kodiUrl = buildString {
                    append(result.m3u8Url)
                    if (result.headers.isNotEmpty()) {
                        append("|")
                        append(result.headers.entries.joinToString("&") { "${it.key}=${it.value}" })
                    }
                }
                Result.success(kodiUrl)
            } else {
                Result.failure(Exception(result.error ?: "Falha na extração YouTube V2"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Prioridade altíssima para YouTube, superior ao ProMax antigo
    override val priority: Int = 150
}
