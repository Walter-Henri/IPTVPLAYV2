package com.m3u.core.mediaresolver

import com.m3u.core.mediaresolver.cache.MediaResolverDatabase
import com.m3u.core.mediaresolver.cache.ResolvedUrlEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache de URLs resolvidas com persistência em Room Database
 * Implementa a regra de 5 horas para atualização
 */
@Singleton
class UrlCache @Inject constructor(
    private val database: MediaResolverDatabase,
    private val config: MediaResolverConfig
) {
    
    private val dao = database.resolvedUrlDao()
    
    /**
     * Busca URL do cache
     * Retorna null se não existir ou estiver expirada
     */
    suspend fun get(originalUrl: String): CachedUrl? = withContext(Dispatchers.IO) {
        try {
            val entity = dao.getResolvedUrl(originalUrl)
            
            if (entity != null && entity.isValid()) {
                CachedUrl(
                    resolvedUrl = entity.resolvedUrl,
                    headers = entity.headers,
                    quality = entity.quality,
                    format = entity.format,
                    timestamp = entity.timestamp,
                    needsUpdate = entity.needsUpdate(config.cacheValidityHours)
                )
            } else {
                // Remove se expirado
                if (entity != null) {
                    dao.deleteResolvedUrl(originalUrl)
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Armazena URL no cache
     */
    suspend fun put(
        originalUrl: String,
        resolvedUrl: String,
        headers: Map<String, String> = emptyMap(),
        quality: String? = null,
        format: StreamFormat = StreamFormat.UNKNOWN
    ) = withContext(Dispatchers.IO) {
        try {
            val currentTime = System.currentTimeMillis()
            val expiresAt = currentTime + (config.cacheValidityHours * 60 * 60 * 1000L)
            
            val entity = ResolvedUrlEntity(
                originalUrl = originalUrl,
                resolvedUrl = resolvedUrl,
                timestamp = currentTime,
                expiresAt = expiresAt,
                quality = quality,
                format = format,
                headers = headers
            )
            
            dao.insertResolvedUrl(entity)
        } catch (e: Exception) {
            // Log error but don't throw
        }
    }
    
    /**
     * Remove URLs expiradas do cache
     */
    suspend fun clearExpired() = withContext(Dispatchers.IO) {
        try {
            val currentTime = System.currentTimeMillis()
            dao.deleteExpiredUrls(currentTime)
        } catch (e: Exception) {
            // Log error but don't throw
        }
    }
    
    /**
     * Remove todas as URLs do cache
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        try {
            dao.deleteAllUrls()
        } catch (e: Exception) {
            // Log error but don't throw
        }
    }
    
    /**
     * Busca URLs que precisam atualização (baseado na regra de 5 horas)
     */
    suspend fun getUrlsNeedingUpdate(): List<String> = withContext(Dispatchers.IO) {
        try {
            val currentTime = System.currentTimeMillis()
            val thresholdTime = currentTime - (config.cacheValidityHours * 60 * 60 * 1000L)
            
            dao.getUrlsNeedingUpdate(thresholdTime).map { it.originalUrl }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Retorna estatísticas do cache
     */
    suspend fun getStats(): CacheStats = withContext(Dispatchers.IO) {
        try {
            val currentTime = System.currentTimeMillis()
            val totalUrls = dao.countUrls()
            val validUrls = dao.countValidUrls(currentTime)
            
            CacheStats(
                totalUrls = totalUrls,
                validUrls = validUrls,
                expiredUrls = totalUrls - validUrls
            )
        } catch (e: Exception) {
            CacheStats(0, 0, 0)
        }
    }
}

/**
 * URL em cache
 */
data class CachedUrl(
    val resolvedUrl: String,
    val headers: Map<String, String>,
    val quality: String?,
    val format: StreamFormat,
    val timestamp: Long,
    val needsUpdate: Boolean
)

/**
 * Estatísticas do cache
 */
data class CacheStats(
    val totalUrls: Int,
    val validUrls: Int,
    val expiredUrls: Int
)
