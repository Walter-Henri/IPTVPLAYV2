package com.m3u.extension.logic.resolvers

/**
 * Interface base para todos os motores de extração.
 * Facilita a adição de novos suportes (Twitch, Kick, sites de filmes, etc).
 */
interface StreamResolver {
    /**
     * Identifica se este motor consegue lidar com a URL fornecida.
     */
    fun canResolve(url: String): Boolean

    /**
     * Executa a lógica de extração.
     */
    suspend fun resolve(url: String): Result<String>
    
    /**
     * Prioridade do motor (maior = tentado primeiro).
     */
    val priority: Int get() = 0
}
