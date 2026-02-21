package com.m3u.core.mediaresolver.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO para acesso às URLs resolvidas
 */
@Dao
interface ResolvedUrlDao {
    
    /**
     * Busca URL resolvida pelo URL original
     */
    @Query("SELECT * FROM resolved_urls WHERE originalUrl = :url LIMIT 1")
    suspend fun getResolvedUrl(url: String): ResolvedUrlEntity?
    
    /**
     * Busca URL resolvida como Flow (observável)
     */
    @Query("SELECT * FROM resolved_urls WHERE originalUrl = :url LIMIT 1")
    fun getResolvedUrlFlow(url: String): Flow<ResolvedUrlEntity?>
    
    /**
     * Insere ou atualiza URL resolvida
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResolvedUrl(url: ResolvedUrlEntity)
    
    /**
     * Insere múltiplas URLs resolvidas
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResolvedUrls(urls: List<ResolvedUrlEntity>)
    
    /**
     * Deleta URLs expiradas
     */
    @Query("DELETE FROM resolved_urls WHERE expiresAt < :currentTime")
    suspend fun deleteExpiredUrls(currentTime: Long): Int
    
    /**
     * Deleta URL específica
     */
    @Query("DELETE FROM resolved_urls WHERE originalUrl = :url")
    suspend fun deleteResolvedUrl(url: String): Int
    
    /**
     * Deleta todas as URLs
     */
    @Query("DELETE FROM resolved_urls")
    suspend fun deleteAllUrls(): Int
    
    /**
     * Conta total de URLs armazenadas
     */
    @Query("SELECT COUNT(*) FROM resolved_urls")
    suspend fun countUrls(): Int
    
    /**
     * Conta URLs válidas (não expiradas)
     */
    @Query("SELECT COUNT(*) FROM resolved_urls WHERE expiresAt >= :currentTime")
    suspend fun countValidUrls(currentTime: Long): Int
    
    /**
     * Busca todas as URLs (para debug)
     */
    @Query("SELECT * FROM resolved_urls ORDER BY timestamp DESC")
    suspend fun getAllUrls(): List<ResolvedUrlEntity>
    
    /**
     * Busca URLs que precisam atualização
     */
    @Query("SELECT * FROM resolved_urls WHERE timestamp < :thresholdTime")
    suspend fun getUrlsNeedingUpdate(thresholdTime: Long): List<ResolvedUrlEntity>
    
    /**
     * Atualiza timestamp de uma URL específica
     */
    @Query("UPDATE resolved_urls SET timestamp = :newTimestamp, expiresAt = :newExpiresAt WHERE originalUrl = :url")
    suspend fun updateTimestamp(url: String, newTimestamp: Long, newExpiresAt: Long): Int
}
