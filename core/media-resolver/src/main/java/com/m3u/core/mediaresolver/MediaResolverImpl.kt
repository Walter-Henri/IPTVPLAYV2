package com.m3u.core.mediaresolver

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.m3u.core.plugin.IPlugin
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Implementação principal do MediaResolver
 * Orquestra RedirectResolver e cache
 */
@Singleton
class MediaResolverImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val redirectResolver: RedirectResolver,
    private val urlCache: UrlCache,
    private val config: MediaResolverConfig
) : MediaResolver {
    
    override suspend fun resolve(url: String, forceRefresh: Boolean): ResolveResult = withContext(Dispatchers.IO) {
        try {
            // Extrai a URL base e headers do formato Kodi (url|headers)
            val (baseUrl, kodiHeaders) = parseKodiUrl(url)
            
            // Detecta se é um stream direto (m3u8, mpd, ts, mp4) - NÃO precisa de resolução
            val looksLikeDirect = baseUrl.contains(".m3u8", ignoreCase = true) ||
                                 baseUrl.contains(".mpd", ignoreCase = true) ||
                                 baseUrl.contains(".ts", ignoreCase = true) ||
                                 baseUrl.contains(".mp4", ignoreCase = true) ||
                                 baseUrl.contains("/manifest", ignoreCase = true) ||
                                 baseUrl.contains("/playlist", ignoreCase = true) ||
                                 (baseUrl.contains("/live/", ignoreCase = true) && !baseUrl.contains("googlevideo.com")) ||
                                 (baseUrl.contains("/hls/", ignoreCase = true) && !baseUrl.contains("googlevideo.com"))
            
            // Especial: manifests do GoogleVideo SÃO streams, mas precisam de tratamento especial
            // Então removemos do isDirectStream genérico para cair na resolução com extensão
            val isDirectStream = looksLikeDirect && !baseUrl.contains("googlevideo.com")
            
            // Se é stream direto, retorna imediatamente sem resolver (evita 403/405)
            if (isDirectStream) {
                val format = when {
                    baseUrl.contains(".m3u8", true) || baseUrl.contains("/hls/", true) -> StreamFormat.HLS
                    baseUrl.contains(".mpd", true) -> StreamFormat.DASH
                    baseUrl.contains(".mp4", true) -> StreamFormat.MP4
                    baseUrl.startsWith("rtmp://", true) -> StreamFormat.RTMP
                    baseUrl.startsWith("rtsp://", true) -> StreamFormat.RTSP
                    else -> StreamFormat.UNKNOWN
                }
                return@withContext ResolveResult.Success(
                    resolvedUrl = baseUrl,
                    headers = kodiHeaders,
                    format = format,
                    fromCache = false
                )
            }
            
            // Verifica se é URL do YouTube ou GoogleVideo (manifesto) para usar Extensão
            // Links do googlevideo.com expiram rápido e precisam de renovação pela extensão
            if (url.contains("youtube.com") || url.contains("youtu.be") || url.contains("googlevideo.com")) {
                return@withContext resolveWithPlugin(url)
            }

            // Verifica cache primeiro (se não forçar refresh)
            if (!forceRefresh) {
                val cachedResult = urlCache.get(baseUrl)
                if (cachedResult != null) {
                    // Mescla headers do cache com headers Kodi
                    val mergedHeaders = cachedResult.headers.toMutableMap().apply {
                        putAll(kodiHeaders)
                    }
                    return@withContext ResolveResult.Success(
                        resolvedUrl = cachedResult.resolvedUrl,
                        headers = mergedHeaders,
                        quality = cachedResult.quality,
                        format = cachedResult.format,
                        fromCache = true
                    )
                }
            }
            
            // Tenta resolver como URL regular
            val result = resolveRegularUrl(baseUrl)
            
            // Mescla headers resolvidos com headers Kodi (Kodi tem prioridade)
            val finalResult = if (result is ResolveResult.Success) {
                val mergedHeaders = result.headers.toMutableMap().apply {
                    putAll(kodiHeaders)
                }
                result.copy(headers = mergedHeaders)
            } else result
            
            // Salva no cache se bem-sucedido
            if (finalResult is ResolveResult.Success) {
                urlCache.put(
                    originalUrl = baseUrl,
                    resolvedUrl = finalResult.resolvedUrl,
                    headers = finalResult.headers,
                    quality = finalResult.quality,
                    format = finalResult.format
                )
            }
            
            finalResult
            
        } catch (e: Exception) {
            ResolveResult.Error(
                error = ResolveError.UNKNOWN,
                message = "Erro inesperado: ${e.message}",
                originalUrl = url
            )
        }
    }

    override suspend fun isCached(url: String): Boolean {
        return urlCache.get(url) != null
    }
    
    override suspend fun clearExpiredCache() {
        urlCache.clearExpired()
    }
    
    override suspend fun clearAllCache() {
        urlCache.clearAll()
    }
    
    private suspend fun resolveWithPlugin(url: String): ResolveResult = suspendCoroutine { continuation ->
        val intent = Intent("com.m3u.plugin.PluginService")
        intent.setPackage("com.m3u.plugin")
        
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                try {
                    val binder = IPlugin.Stub.asInterface(service)
                    val resultUrl = binder.resolve(url)
                    if (!resultUrl.isNullOrEmpty()) {
                        val (base, headers) = parseKodiUrl(resultUrl!!)
                        continuation.resume(ResolveResult.Success(
                            resolvedUrl = base,
                            headers = headers,
                            format = StreamFormat.HLS
                        ))
                    } else {
                        continuation.resume(ResolveResult.Error(
                            error = ResolveError.UNKNOWN,
                            message = "Plugin returned null or empty URL",
                            originalUrl = url
                        ))
                    }
                } catch (e: Exception) {
                    continuation.resume(ResolveResult.Error(
                        error = ResolveError.UNKNOWN,
                        message = "Plugin error: ${e.message}",
                        originalUrl = url
                    ))
                } finally {
                    try { context.unbindService(this) } catch (e: Exception) {}
                }
            }
            
            override fun onServiceDisconnected(name: ComponentName?) {}
        }
        
        try {
            val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!bound) {
                continuation.resume(ResolveResult.Error(
                    error = ResolveError.NO_AVAILABLE_API,
                    message = "Plugin not installed",
                    originalUrl = url
                ))
            }
        } catch (e: Exception) {
            continuation.resume(ResolveResult.Error(
                error = ResolveError.UNKNOWN,
                message = "Bind error: ${e.message}",
                originalUrl = url
            ))
        }
    }

    /**
     * Resolve URL regular (com redirecionamentos)
     */
    private suspend fun resolveRegularUrl(url: String): ResolveResult {
        return when (val result = redirectResolver.resolveRedirects(url)) {
            is RedirectResult.Success -> {
                ResolveResult.Success(
                    resolvedUrl = result.finalUrl,
                    headers = result.headers,
                    quality = null,
                    format = result.format,
                    fromCache = false
                )
            }
            is RedirectResult.Error -> {
                ResolveResult.Error(
                    error = result.error,
                    message = result.message,
                    originalUrl = url
                )
            }
        }
    }

    private fun parseKodiUrl(url: String): Pair<String, Map<String, String>> {
        val baseUrl = url.substringBefore("|")
        val headers = if (url.contains("|")) {
            url.substringAfter("|").split("&")
                .filter { it.contains("=") }
                .associate { 
                    val parts = it.split("=", limit = 2)
                    parts[0] to parts.getOrElse(1) { "" }
                }
        } else emptyMap()
        return baseUrl to headers
    }
}
