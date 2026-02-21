package com.m3u.core.mediaresolver

/**
 * Interface principal para resolução de URLs de mídia
 * Suporta redirecionamentos HTTP e cache inteligente
 */
interface MediaResolver {
    
    /**
     * Resolve uma URL de mídia, seguindo redirecionamentos e extraindo streams
     * 
     * @param url URL original da mídia
     * @param forceRefresh Se true, ignora o cache e força uma nova resolução
     * @return Resultado da resolução contendo a URL final e metadados
     */
    suspend fun resolve(url: String, forceRefresh: Boolean = false): ResolveResult
    
    /**
     * Verifica se uma URL está em cache e ainda é válida
     * 
     * @param url URL para verificar
     * @return true se está em cache e válida, false caso contrário
     */
    suspend fun isCached(url: String): Boolean
    
    /**
     * Limpa URLs expiradas do cache
     */
    suspend fun clearExpiredCache()
    
    /**
     * Limpa todo o cache
     */
    suspend fun clearAllCache()
}

/**
 * Resultado da resolução de URL
 */
sealed class ResolveResult {
    
    /**
     * Resolução bem-sucedida
     * 
     * @param resolvedUrl URL final resolvida
     * @param headers Headers HTTP necessários para reprodução
     * @param quality Qualidade do stream (se disponível)
     * @param format Formato do stream (HLS, DASH, etc)
     * @param fromCache Se o resultado veio do cache
     */
    data class Success(
        val resolvedUrl: String,
        val headers: Map<String, String> = emptyMap(),
        val quality: String? = null,
        val format: StreamFormat = StreamFormat.UNKNOWN,
        val fromCache: Boolean = false
    ) : ResolveResult()
    
    /**
     * Erro na resolução
     * 
     * @param error Tipo de erro ocorrido
     * @param message Mensagem de erro detalhada
     * @param originalUrl URL original que falhou
     */
    data class Error(
        val error: ResolveError,
        val message: String,
        val originalUrl: String
    ) : ResolveResult()
}

/**
 * Tipos de erro na resolução
 */
enum class ResolveError {
    NETWORK_ERROR,          // Erro de rede
    TIMEOUT,                // Timeout na requisição
    INVALID_URL,            // URL inválida
    UNSUPPORTED_FORMAT,     // Formato não suportado
    NO_AVAILABLE_API,       // Nenhuma API disponível
    UNKNOWN                 // Erro desconhecido
}

/**
 * Formatos de stream suportados
 */
enum class StreamFormat {
    HLS,        // HTTP Live Streaming (.m3u8)
    DASH,       // Dynamic Adaptive Streaming over HTTP (.mpd)
    MP4,        // MP4 direto
    RTMP,       // Real-Time Messaging Protocol
    RTSP,       // Real Time Streaming Protocol
    UNKNOWN     // Formato desconhecido
}

/**
 * Configurações do MediaResolver
 */
data class MediaResolverConfig(
    val userAgent: String = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
    val connectTimeout: Long = 15000L,  // 15 segundos
    val readTimeout: Long = 15000L,     // 15 segundos
    val followRedirects: Boolean = true,
    val maxRedirects: Int = 10,
    val cacheValidityHours: Int = 5,    // 5 horas conforme especificação
    val preferredQuality: String = "best",
    val preferredFormat: StreamFormat = StreamFormat.HLS,
    val liveBufferMs: Int = 0,
    val enableDecryption: Boolean = true
)
