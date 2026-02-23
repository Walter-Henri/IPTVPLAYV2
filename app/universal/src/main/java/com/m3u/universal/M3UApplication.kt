package com.m3u.universal

import android.app.Application
import android.app.UiModeManager
import android.content.res.Configuration
import androidx.work.Configuration as WorkConfiguration
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient
import com.m3u.data.service.ErrorReporter

/**
 * M3UApplication - Application class principal
 * 
 * Gerencia inicialização global do aplicativo com suporte a:
 * - Hilt Dependency Injection
 * - WorkManager para tarefas em background
 * - Detecção de plataforma (Mobile/TV)
 * - Logging com Timber
 * - Crash reporting com ACRA
 * - Configuração global do Coil (ImageLoader)
 */
@HiltAndroidApp
class M3UApplication : Application(), WorkConfiguration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workConfiguration: WorkConfiguration

    override val workManagerConfiguration: WorkConfiguration
        get() = workConfiguration

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                            .build()
                        chain.proceed(request)
                    }
                    .build()
            }
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        // Inicializar Timber para logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                ErrorReporter.log(this, "CRASH", "Uncaught exception on thread=${t.name}", e)
            }
            previousHandler?.uncaughtException(t, e)
        }

        // Log de inicialização
        Timber.d("M3U Application started")
        Timber.d("Platform: ${if (isTv()) "TV" else "Mobile"}")
        Timber.d("Build Type: ${BuildConfig.BUILD_TYPE}")
        Timber.d("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")

        // Inicializar WorkManager
        try {
            WorkManager.initialize(this, workManagerConfiguration)
            Timber.d("WorkManager initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize WorkManager")
        }

        // Restaurar tokens de sessão (UA, Cookies, PO Token) salvos pela extensão
        // Isso garante que streams YouTube funcionem mesmo após reinicialização do app
        com.m3u.core.foundation.IdentityRegistry.init(this)
        Timber.d("IdentityRegistry initialized — ${if (com.m3u.core.foundation.IdentityRegistry.hasValidIdentity()) "tokens disponíveis" else "aguardando tokens da extensão"}")
    }

    /**
     * Detecta se o dispositivo é uma TV
     */
    private fun isTv(): Boolean {
        val uiModeManager = getSystemService(UI_MODE_SERVICE) as? UiModeManager
        return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Timber.w("Low memory warning received")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Timber.d("Memory trim requested: level=$level")
    }
}
