package com.m3u.extension.logic.resolvers

import android.content.Context
import com.m3u.extension.newpipe.YtDlpResolver

/**
 * Resolvedor robusto baseado em yt-dlp para sites diversos não-YouTube.
 */
class YtDlpSupportResolver(private val context: Context) : StreamResolver {

    override fun canResolve(url: String): Boolean {
        // O yt-dlp tenta resolver quase tudo que não for tratado pelos outros.
        return true 
    }

    override suspend fun resolve(url: String): Result<String> {
        return YtDlpResolver.resolve(context, url)
    }

    override val priority: Int = 10 // Baixa prioridade (último recurso)
}
