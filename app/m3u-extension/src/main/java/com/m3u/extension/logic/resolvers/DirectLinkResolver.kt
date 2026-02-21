package com.m3u.extension.logic.resolvers

/**
 * Resolvedor para links que já são formatos finais de IPTV.
 * Suporta: m3u8, mpd (DASH), ts, mp4.
 */
class DirectLinkResolver : StreamResolver {
    
    private val directExtensions = listOf(
        ".m3u8", ".mpd", ".ts", ".tp", ".m2ts", 
        ".cmf", ".m4s", ".mp4", ".ts", ".mkv"
    )
    
    private val directProtocols = listOf(
        "rtsp://", "rtmp://", "udp://", "rtp://", "srt://"
    )

    override fun canResolve(url: String): Boolean {
        val lowercaseUrl = url.lowercase()
        val cleanUrl = url.split("?")[0].lowercase()
        
        return directExtensions.any { cleanUrl.endsWith(it) } || 
               directProtocols.any { lowercaseUrl.startsWith(it) } ||
               url.contains("m3u8?") || 
               url.contains("index.mpd") ||
               url.contains("/udp/")
    }

    override suspend fun resolve(url: String): Result<String> {
        return Result.success(url) // Link já está pronto para o player
    }

    override val priority: Int = 200 // Prioridade máxima (evita processamento desnecessário)
}
