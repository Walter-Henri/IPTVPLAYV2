package com.m3u.plugin

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * PluginApplication - Aplicação Android da Extensão M3U
 */

class PluginApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Plugin Application initialized")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Inicializa o motor de extração nativo
                com.m3u.plugin.newpipe.NewPipeResolver.init(applicationContext)
                Log.d(TAG, "NewPipe Extractor inicializado com sucesso.")
            } catch (e: Exception) {
                Log.e(TAG, "Erro na inicialização da extensão", e)
            }
        }
    }

    companion object {
        private const val TAG = "PluginApplication"
    }
}
