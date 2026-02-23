package com.m3u.core.foundation

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * Global registry for User-Agent and Cookies sharing between Extension and Main App.
 */
object IdentityRegistry {
    private const val PREFS_NAME = "m3u_identity_registry"
    
    // In-memory cache for fast access
    private var currentUserAgent: String? = null
    private val cookies = ConcurrentHashMap<String, String>()

    fun setUserAgent(ua: String) {
        currentUserAgent = ua
    }

    fun getUserAgent(): String? = currentUserAgent

    fun setCookie(domain: String, cookie: String) {
        cookies[domain] = cookie
    }

    fun getCookie(domain: String): String? = cookies[domain]

    /**
     * Mescla headers globais com os específicos fornecidos.
     */
    fun applyTo(headers: MutableMap<String, String>, url: String? = null) {
        currentUserAgent?.let { 
            if (headers["User-Agent"].isNullOrBlank()) {
                headers["User-Agent"] = it
            }
        }
        
        if (url != null) {
            val domain = try {
                val host = java.net.URI(url).host
                if (host != null) {
                    if (host.contains("youtube.com") || host.contains("googlevideo.com")) "youtube.com"
                    else host
                } else null
            } catch (e: Exception) { null }
            
            domain?.let { d ->
                getCookie(d)?.let { c ->
                    if (headers["Cookie"].isNullOrBlank()) {
                        headers["Cookie"] = c
                    }
                }
            }
        }
    }
}
