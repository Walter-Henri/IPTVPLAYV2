package com.m3u.data.service

import android.content.Context
import com.m3u.core.architecture.preferences.AutoResumePreferences
import com.m3u.core.architecture.preferences.Settings
import com.m3u.core.architecture.preferences.set
import com.m3u.core.architecture.preferences.settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gerenciador de estado de reprodução persistente
 * Responsável por salvar e recuperar informações do último canal reproduzido
 */
@Singleton
class PlaybackStateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val settings: Settings = context.settings
    @Volatile
    private var sessionAutoResumeTriggered: Boolean = false
    
    /**
     * Salva o estado de reprodução atual
     * Deve ser chamado apenas após o evento de sucesso do player (onPlaybackStarted)
     */
    suspend fun savePlaybackState(
        channelId: Int,
        streamUrl: String,
        categoryName: String = "",
        playlistUrl: String = ""
    ) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date())
        
        settings.apply {
            set(AutoResumePreferences.LAST_CHANNEL_ID, channelId)
            set(AutoResumePreferences.LAST_STREAM_URL, streamUrl)
            set(AutoResumePreferences.LAST_CATEGORY_NAME, categoryName)
            set(AutoResumePreferences.LAST_PLAYLIST_URL, playlistUrl)
            set(AutoResumePreferences.LAST_PLAYBACK_TIMESTAMP, timestamp)
        }
    }
    
    /**
     * Recupera o estado de reprodução salvo
     */
    suspend fun getPlaybackState(): PlaybackState {
        val channelId = settings.get(AutoResumePreferences.LAST_CHANNEL_ID) ?: AutoResumePreferences.DEFAULT_LAST_CHANNEL_ID
        val streamUrl = settings.get(AutoResumePreferences.LAST_STREAM_URL) ?: AutoResumePreferences.DEFAULT_LAST_STREAM_URL
        val categoryName = settings.get(AutoResumePreferences.LAST_CATEGORY_NAME) ?: AutoResumePreferences.DEFAULT_LAST_CATEGORY_NAME
        val playlistUrl = settings.get(AutoResumePreferences.LAST_PLAYLIST_URL) ?: AutoResumePreferences.DEFAULT_LAST_PLAYLIST_URL
        val timestamp = settings.get(AutoResumePreferences.LAST_PLAYBACK_TIMESTAMP) ?: AutoResumePreferences.DEFAULT_LAST_PLAYBACK_TIMESTAMP
        
        return PlaybackState(
            channelId = channelId,
            streamUrl = streamUrl,
            categoryName = categoryName,
            playlistUrl = playlistUrl,
            timestamp = timestamp
        )
    }
    
    /**
     * Limpa o estado de reprodução salvo
     */
    suspend fun clearPlaybackState() {
        settings.apply {
            set(AutoResumePreferences.LAST_CHANNEL_ID, AutoResumePreferences.DEFAULT_LAST_CHANNEL_ID)
            set(AutoResumePreferences.LAST_STREAM_URL, AutoResumePreferences.DEFAULT_LAST_STREAM_URL)
            set(AutoResumePreferences.LAST_CATEGORY_NAME, AutoResumePreferences.DEFAULT_LAST_CATEGORY_NAME)
            set(AutoResumePreferences.LAST_PLAYLIST_URL, AutoResumePreferences.DEFAULT_LAST_PLAYLIST_URL)
            set(AutoResumePreferences.LAST_PLAYBACK_TIMESTAMP, AutoResumePreferences.DEFAULT_LAST_PLAYBACK_TIMESTAMP)
        }
    }
    
    /**
     * Verifica se o auto-resume está habilitado
     */
    suspend fun isAutoResumeEnabled(): Boolean {
        return try {
            settings.get(AutoResumePreferences.AUTO_RESUME_ENABLED) ?: AutoResumePreferences.DEFAULT_AUTO_RESUME_ENABLED
        } catch (e: Exception) {
            AutoResumePreferences.DEFAULT_AUTO_RESUME_ENABLED
        }
    }
    
    /**
     * Verifica se o launch on boot está habilitado
     */
    suspend fun isLaunchOnBootEnabled(): Boolean {
        return try {
            settings.get(AutoResumePreferences.LAUNCH_ON_BOOT) ?: AutoResumePreferences.DEFAULT_LAUNCH_ON_BOOT
        } catch (e: Exception) {
            AutoResumePreferences.DEFAULT_LAUNCH_ON_BOOT
        }
    }
    
    /**
     * Obtém o delay de inicialização configurado (em segundos)
     */
    suspend fun getStartupDelay(): Int {
        return try {
            settings.get(AutoResumePreferences.STARTUP_DELAY) ?: AutoResumePreferences.DEFAULT_STARTUP_DELAY
        } catch (e: Exception) {
            AutoResumePreferences.DEFAULT_STARTUP_DELAY
        }
    }
    
    /**
     * Flow para observar mudanças no estado de auto-resume
     */
    fun observeAutoResumeEnabled(): Flow<Boolean> {
        return settings.autoResumeEnabled
    }

    fun hasSessionAutoResumeTriggered(): Boolean {
        return sessionAutoResumeTriggered
    }

    fun markSessionAutoResumeTriggered() {
        sessionAutoResumeTriggered = true
    }

    fun resetSessionAutoResume() {
        sessionAutoResumeTriggered = false
    }
}

/**
 * Classe de dados para representar o estado de reprodução
 */
data class PlaybackState(
    val channelId: Int,
    val streamUrl: String,
    val categoryName: String,
    val playlistUrl: String,
    val timestamp: String
)
