package com.m3u.plugin.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "Plugin_settings")

class PluginPreferences(private val context: Context) {
    
    companion object {
        val KEY_FORMAT = stringPreferencesKey("format")
        val KEY_USER_AGENT = stringPreferencesKey("user_agent")
        
        val KEY_LAST_RUN_TIMESTAMP = androidx.datastore.preferences.core.longPreferencesKey("last_run_timestamp")
        val KEY_LAST_RUN_STATUS = stringPreferencesKey("last_run_status") // "Success", "Failed", "Running"
        val KEY_SUCCESS_COUNT = androidx.datastore.preferences.core.intPreferencesKey("success_count")
        val KEY_FAILURE_COUNT = androidx.datastore.preferences.core.intPreferencesKey("failure_count")
        val KEY_SUCCESS_CHANNELS = stringPreferencesKey("success_channels")
        val KEY_FAILED_CHANNELS = stringPreferencesKey("failed_channels")
        val KEY_LAST_DROPBOX_SYNC = androidx.datastore.preferences.core.longPreferencesKey("last_dropbox_sync")
        
        // Prioritize HLS/m3u8 streams for live compatibility (ExoPlayer handles adaptive quality).
        // Format 95/96 = HLS master playlist from YouTube. Falls back through m3u8 variants.
        const val DEFAULT_FORMAT = "bestvideo[protocol^=m3u8]+bestaudio[protocol^=m3u8]/best[protocol^=m3u8]/best"
    }

    val format: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_FORMAT] ?: DEFAULT_FORMAT
        }

    val userAgent: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_USER_AGENT]
        }
        
    val lastRunTimestamp: Flow<Long> = context.dataStore.data.map { it[KEY_LAST_RUN_TIMESTAMP] ?: 0L }
    val lastRunStatus: Flow<String> = context.dataStore.data.map { it[KEY_LAST_RUN_STATUS] ?: "Aguardando execução" }
    val successCount: Flow<Int> = context.dataStore.data.map { it[KEY_SUCCESS_COUNT] ?: 0 }
    val failureCount: Flow<Int> = context.dataStore.data.map { it[KEY_FAILURE_COUNT] ?: 0 }
    val successChannels: Flow<List<String>> = context.dataStore.data.map { 
        it[KEY_SUCCESS_CHANNELS]?.split("|")?.filter { s -> s.isNotEmpty() } ?: emptyList() 
    }
    val failedChannels: Flow<List<String>> = context.dataStore.data.map { 
        it[KEY_FAILED_CHANNELS]?.split("|")?.filter { s -> s.isNotEmpty() } ?: emptyList() 
    }
    
    // Non-flow property for the worker
    val lastDropboxSync: Long
        get() = try {
            val prefs = kotlinx.coroutines.runBlocking { context.dataStore.data.first() }
            prefs[KEY_LAST_DROPBOX_SYNC] ?: 0L
        } catch (_: Exception) { 0L }

    suspend fun setFormat(format: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_FORMAT] = format
        }
    }

    suspend fun setUserAgent(userAgent: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_USER_AGENT] = userAgent
        }
    }
    
    suspend fun updateStatus(status: String) {
        context.dataStore.edit { it[KEY_LAST_RUN_STATUS] = status }
    }
    
    suspend fun recordRun(timestamp: Long, successes: Int, failures: Int, successNames: List<String> = emptyList(), failNames: List<String> = emptyList()) {
        context.dataStore.edit { 
            it[KEY_LAST_RUN_TIMESTAMP] = timestamp
            it[KEY_SUCCESS_COUNT] = successes
            it[KEY_FAILURE_COUNT] = failures
            it[KEY_SUCCESS_CHANNELS] = successNames.joinToString("|")
            it[KEY_FAILED_CHANNELS] = failNames.joinToString("|")
            it[KEY_LAST_RUN_STATUS] = "Aguardando próxima execução (Última: Sucesso)"
        }
    }

    suspend fun recordDropboxSync(timestamp: Long) {
        context.dataStore.edit { it[KEY_LAST_DROPBOX_SYNC] = timestamp }
    }
}
