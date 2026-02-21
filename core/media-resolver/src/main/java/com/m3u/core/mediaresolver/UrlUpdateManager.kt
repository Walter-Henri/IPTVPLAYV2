package com.m3u.core.mediaresolver

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gerenciador de atualização de URLs com otimização de bateria
 * Implementa a estratégia Lazy-Update:
 * - Verifica apenas quando o app é aberto (OnResume)
 * - Não executa processos em background com tela bloqueada
 * - Atualiza silenciosamente URLs antigas enquanto usuário navega
 */
@Singleton
class UrlUpdateManager @Inject constructor(
    private val mediaResolver: MediaResolver,
    private val urlCache: UrlCache,
    private val config: MediaResolverConfig
) {
    
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()
    
    private val _stats = MutableStateFlow(UpdateStats())
    val stats: StateFlow<UpdateStats> = _stats.asStateFlow()
    
    private var updateJob: Job? = null
    
    /**
     * Threshold de 5 horas em milissegundos
     */
    private val updateThreshold = config.cacheValidityHours * 60 * 60 * 1000L
    
    /**
     * Verifica se uma URL precisa atualização baseado na regra de 5 horas
     */
    suspend fun shouldUpdate(url: String): Boolean {
        val cachedUrl = urlCache.get(url) ?: return true
        return cachedUrl.needsUpdate
    }
    
    /**
     * Inicia verificação e atualização silenciosa de URLs
     * Chamado no OnResume da Activity/Fragment
     */
    fun startSilentUpdate() {
        if (updateJob?.isActive == true) {
            return // Já está atualizando
        }
        
        updateJob = coroutineScope.launch {
            try {
                _updateState.value = UpdateState.Checking
                
                // Busca URLs que precisam atualização
                val urlsToUpdate = urlCache.getUrlsNeedingUpdate()
                
                if (urlsToUpdate.isEmpty()) {
                    _updateState.value = UpdateState.Idle
                    return@launch
                }
                
                _updateState.value = UpdateState.Updating(
                    total = urlsToUpdate.size,
                    current = 0
                )
                
                var successCount = 0
                var errorCount = 0
                
                // Atualiza URLs uma por uma (sem bloquear UI)
                urlsToUpdate.forEachIndexed { index, url ->
                    try {
                        // Delay para não sobrecarregar
                        delay(500)
                        
                        val result = mediaResolver.resolve(url, forceRefresh = true)
                        
                        if (result is ResolveResult.Success) {
                            successCount++
                        } else {
                            errorCount++
                        }
                        
                        _updateState.value = UpdateState.Updating(
                            total = urlsToUpdate.size,
                            current = index + 1
                        )
                        
                    } catch (e: Exception) {
                        errorCount++
                    }
                }
                
                _stats.value = UpdateStats(
                    lastUpdateTime = System.currentTimeMillis(),
                    totalUpdated = successCount,
                    totalErrors = errorCount
                )
                
                _updateState.value = UpdateState.Completed(
                    success = successCount,
                    errors = errorCount
                )
                
                // Volta para Idle após 3 segundos
                delay(3000)
                _updateState.value = UpdateState.Idle
                
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(e.message ?: "Erro desconhecido")
            }
        }
    }
    
    /**
     * Cancela atualização em andamento
     */
    fun cancelUpdate() {
        updateJob?.cancel()
        _updateState.value = UpdateState.Idle
    }
    
    /**
     * Limpa cache de URLs expiradas
     * Deve ser chamado periodicamente
     */
    suspend fun cleanupExpiredUrls() {
        urlCache.clearExpired()
    }
    
    /**
     * Retorna estatísticas do cache
     */
    suspend fun getCacheStats(): CacheStats {
        return urlCache.getStats()
    }
}

/**
 * Estados da atualização
 */
sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class Updating(val total: Int, val current: Int) : UpdateState()
    data class Completed(val success: Int, val errors: Int) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

/**
 * Estatísticas de atualização
 */
data class UpdateStats(
    val lastUpdateTime: Long = 0,
    val totalUpdated: Int = 0,
    val totalErrors: Int = 0
) {
    fun getLastUpdateTimeFormatted(): String {
        if (lastUpdateTime == 0L) return "Nunca"
        
        val diff = System.currentTimeMillis() - lastUpdateTime
        val hours = diff / (60 * 60 * 1000)
        val minutes = (diff % (60 * 60 * 1000)) / (60 * 1000)
        
        return when {
            hours > 0 -> "${hours}h ${minutes}min atrás"
            minutes > 0 -> "${minutes}min atrás"
            else -> "Agora mesmo"
        }
    }
}
