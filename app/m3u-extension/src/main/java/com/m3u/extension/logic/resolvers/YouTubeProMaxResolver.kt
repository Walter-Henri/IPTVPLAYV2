package com.m3u.extension.logic.resolvers

import android.content.Context
import com.m3u.extension.logic.BrowserUtils

/**
 * Resolvedor especializado em YouTube usando o motor Pro-Max (WebView).
 */
class YouTubeProMaxResolver(private val context: Context) : StreamResolver {
    
    override fun canResolve(url: String): Boolean {
        val lowercaseUrl = url.lowercase()
        return lowercaseUrl.contains("youtube.com") || 
               lowercaseUrl.contains("youtu.be") || 
               lowercaseUrl.contains("/live")
    }

    override suspend fun resolve(url: String): Result<String> {
        return try {
            // Usa o BrowserUtils que já otimizamos com Sniffing
            val hlsUrl = BrowserUtils.extractHlsWithWebView(context, url)
            if (hlsUrl != null) {
                val ua = BrowserUtils.getRealUserAgent(context)
                val cookieManager = android.webkit.CookieManager.getInstance()
                val cookies = cookieManager.getCookie(hlsUrl)
                
                val headerList = mutableListOf<String>()
                headerList.add("User-Agent=$ua")
                if (!cookies.isNullOrBlank()) {
                    headerList.add("Cookie=$cookies")
                }
                // Adiciona o Referer para evitar 403 em alguns casos
                headerList.add("Referer=${url.substringBefore("|")}")
                
                val finalUrl = "$hlsUrl|${headerList.joinToString("&")}"
                Result.success(finalUrl)
            }
            else Result.failure(Exception("WebView não encontrou manifesto m3u8"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override val priority: Int = 100 // Alta prioridade
}
