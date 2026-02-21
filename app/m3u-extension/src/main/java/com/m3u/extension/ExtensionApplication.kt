package com.m3u.extension

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ExtensionApplication - Aplicação Android da Extensão M3U
 */

class ExtensionApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Extension Application initialized")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Inicializa o motor de extração nativo
                com.m3u.extension.newpipe.NewPipeResolver.init(applicationContext)
                Log.d(TAG, "NewPipe Extractor inicializado com sucesso.")
            } catch (e: Exception) {
                Log.e(TAG, "Erro na inicialização da extensão", e)
            }
        }
    }

    companion object {
        private const val TAG = "ExtensionApplication"
    }
}
