package com.m3u.extension.logic

import android.content.Context
import android.util.Log
import com.m3u.extension.util.LogManager
import com.m3u.extension.logic.resolvers.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * YouTubeInteractor (Refatorado para LinkExtractionOrchestrator)
 * 
 * Orquestra múltiplos motores de extração de forma modular.
 */
class YouTubeInteractor(private val context: Context) {
    
    companion object {
        private const val TAG = "LinkOrchestrator"
        
        /**
         * Valida se a URL é um link M3U8/stream válido
         * Aceita:
         * - URLs .m3u8 (com ou sem query params)
         * - URLs de manifest/playlist dinâmicos
         * - URLs com tokens de autenticação
         * - Streams MPEG-TS diretos
         */
        fun isValidM3u8Url(url: String): Boolean {
            // Verifica se a URL contém extensões de stream conhecidas
            val hasM3u8Extension = url.contains(".m3u8", ignoreCase = true)
            val hasMpdExtension = url.contains(".mpd", ignoreCase = true)
            val hasTsExtension = url.contains(".ts", ignoreCase = true)
            val hasMp4Extension = url.contains(".mp4", ignoreCase = true)
            
            // Verifica padrões de URL de manifest/stream
            val isManifestUrl = url.contains("/manifest", ignoreCase = true) ||
                               url.contains("/playlist", ignoreCase = true) ||
                               url.contains("/live/", ignoreCase = true) ||
                               url.contains("/hls/", ignoreCase = true) ||
                               url.contains("/stream/", ignoreCase = true)
            
            // Verifica se é uma URL de manifesto do YouTube que termina com /file/index.m3u8
            val isYouTubeManifest = url.contains("manifest.googlevideo.com") && url.endsWith("/file/index.m3u8")
            
            // Rejeita URLs de estatísticas/analytics do YouTube (não são streams)
            val isYouTubeStats = url.contains("youtube.com/api/stats") || url.contains("/api/stats/qoe")
            
            // URLs com tokens geralmente são streams válidos
            val hasStreamToken = url.contains("token=", ignoreCase = true) ||
                                 url.contains("sig=", ignoreCase = true) ||
                                 url.contains("key=", ignoreCase = true)
            
            return (hasM3u8Extension || hasMpdExtension || hasTsExtension || hasMp4Extension || 
                   isManifestUrl || isYouTubeManifest || hasStreamToken) && 
                   !isYouTubeStats
        }
    }

    // LISTA DE MOTORES REGISTRADOS (Modular & Extensível)
    private val resolvers: List<StreamResolver> by lazy {
        listOf(
            DirectLinkResolver(),
            YouTubeProMaxResolver(context),
            YtDlpSupportResolver(context)
        ).sortedByDescending { it.priority }
    }

    /**
     * Resolve uma URL tentando os melhores motores disponíveis para o formato.
     */
    suspend fun resolve(url: String): Result<String> = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext Result.failure(IllegalArgumentException("URL vazia"))
        
        LogManager.debug("Orquestração inteligente: ${url.take(50)}...", "INTERACTOR")
        
        var lastError: Throwable? = null

        for (resolver in resolvers) {
            if (resolver.canResolve(url)) {
                    LogManager.debug("Tentando motor: ${resolver.javaClass.simpleName}", "INTERACTOR")
                    val result = try {
                        val resolved = resolver.resolve(url)
                        LogManager.debug("${resolver.javaClass.simpleName} -> ${resolved.getOrNull()?.take(40)}...", "INTERACTOR")
                        resolved
                    } catch (e: Exception) {
                        Result.failure(e)
                    }

                if (result.isSuccess) {
                    val finalUrl = result.getOrNull()
                    if (!finalUrl.isNullOrBlank() && isValidM3u8Url(finalUrl)) {
                         LogManager.info("✓ Sucesso [${resolver.javaClass.simpleName}]", "INTERACTOR")
                         return@withContext Result.success(finalUrl)
                    } else if (!finalUrl.isNullOrBlank()) {
                         LogManager.warn("URL inválida via ${resolver.javaClass.simpleName}", "INTERACTOR")
                    }
                } else {
                    lastError = result.exceptionOrNull()
                    LogManager.warn("Motor ${resolver.javaClass.simpleName} falhou: ${lastError?.message}", "INTERACTOR")
                }
            }
        }
        
        LogManager.error("✗ Falha crítica em todos os motores registrados para a URL fornecida", "INTERACTOR")
        return@withContext Result.failure(lastError ?: Exception("Nenhum motor compatível encontrado"))
    }
}
