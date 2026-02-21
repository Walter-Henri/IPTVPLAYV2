package com.m3u.extension.newpipe

import android.content.Context
import android.util.Log

/**
 * NewPipeResolver - DEPRECATED / REMOVIDO
 * Este motor foi removido por instabilidade. 
 * O sistema agora utiliza o motor Pro-Max (WebView) via BrowserUtils.
 */
object NewPipeResolver {
    fun init(context: Context) {
        Log.i("NewPipeResolver", "Motor NewPipe desativado. Usando BrowserUtils como motor principal.")
    }
    
    suspend fun resolveLive(url: String): Result<String> {
        return Result.failure(Exception("Motor NewPipe removido do projeto. Use o motor Pro-Max."))
    }
}
