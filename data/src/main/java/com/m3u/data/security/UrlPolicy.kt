package com.m3u.data.security

object UrlPolicy {
    private val allowedSchemes = listOf("http", "https", "rtmp", "rtsp", "udp", "rtp")
    private val blockedSchemes = listOf("file", "content", "javascript", "data")

    fun isSafe(url: String): Boolean {
        val lowerUrl = url.lowercase().trim()
        
        // Bloquear esquemas perigosos
        if (blockedSchemes.any { lowerUrl.startsWith("$it:") }) {
            return false
        }
        
        // Permitir apenas esquemas autorizados
        return allowedSchemes.any { lowerUrl.startsWith("$it:") }
    }
}
