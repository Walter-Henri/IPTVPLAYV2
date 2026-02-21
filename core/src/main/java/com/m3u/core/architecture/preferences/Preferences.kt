@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.m3u.core.architecture.preferences

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

interface AppPreferences {
    val playlistStrategy: StateFlow<Int>
    val rowCount: StateFlow<Int>
    val connectTimeout: StateFlow<Long>
    val godMode: StateFlow<Boolean>
    val clipMode: StateFlow<Int>
    val autoRefreshChannels: StateFlow<Boolean>
    val fullInfoPlayer: StateFlow<Boolean>
    val noPictureMode: StateFlow<Boolean>
    val darkMode: StateFlow<Boolean>
    val useDynamicColors: StateFlow<Boolean>
    val followSystemTheme: StateFlow<Boolean>
    val zappingMode: StateFlow<Boolean>
    val brightnessGesture: StateFlow<Boolean>
    val volumeGesture: StateFlow<Boolean>
    val screencast: StateFlow<Boolean>
    val screenRotating: StateFlow<Boolean>
    val unseensMilliseconds: StateFlow<Long>
    val reconnectMode: StateFlow<Int>
    val colorArgb: StateFlow<Int>
    val tunneling: StateFlow<Boolean>
    val clockMode: StateFlow<Boolean>
    val remoteControl: StateFlow<Boolean>
    val slider: StateFlow<Boolean>
    val alwaysShowReplay: StateFlow<Boolean>
    val playerPanel: StateFlow<Boolean>
    val compactDimension: StateFlow<Boolean>
    val autoResumeEnabled: StateFlow<Boolean>
    val launchOnBoot: StateFlow<Boolean>
    val startupDelay: StateFlow<Int>
    val lastChannelId: StateFlow<Int>
    val lastStreamUrl: StateFlow<String?>
    val lastCategoryName: StateFlow<String?>
    val lastPlaylistUrl: StateFlow<String?>
    val lastPlaybackTimestamp: StateFlow<String?>
    val bufferMs: StateFlow<Int>
    val streamFormat: StateFlow<Int>
    val videoDecryptionEnabled: StateFlow<Boolean>
    val favoritesJson: StateFlow<String?>
    val deviceType: StateFlow<String>
    val lastLayoutName: StateFlow<String>
    val tvModeEnabled: StateFlow<Boolean>
    val playerEngine: StateFlow<Int>
    val autoPipMode: StateFlow<Boolean>
    val alwaysTrustAllSSL: StateFlow<Boolean>
    suspend fun update(key: Preferences.Key<*>, value: Any?)
    suspend operator fun <T> get(key: Preferences.Key<T>): T?
}

typealias Settings = AppPreferences

class AppPreferencesImpl(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope
) : AppPreferences {
    private fun <T> createStateFlow(
        key: Preferences.Key<T>,
        default: T
    ): StateFlow<T> = dataStore.data
        .map { it[key] ?: default }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = default
        )

    override val playlistStrategy = createStateFlow(PreferencesKeys.PLAYLIST_STRATEGY, 0)
    override val rowCount = createStateFlow(PreferencesKeys.ROW_COUNT, 1)
    override val connectTimeout = createStateFlow(PreferencesKeys.CONNECT_TIMEOUT, 10000L)
    override val godMode = createStateFlow(PreferencesKeys.GOD_MODE, false)
    override val clipMode = createStateFlow(PreferencesKeys.CLIP_MODE, 0)
    override val autoRefreshChannels = createStateFlow(PreferencesKeys.AUTO_REFRESH_CHANNELS, false)
    override val fullInfoPlayer = createStateFlow(PreferencesKeys.FULL_INFO_PLAYER, false)
    override val noPictureMode = createStateFlow(PreferencesKeys.NO_PICTURE_MODE, false)
    override val darkMode = createStateFlow(PreferencesKeys.DARK_MODE, false)
    override val useDynamicColors = createStateFlow(PreferencesKeys.USE_DYNAMIC_COLORS, false)
    override val followSystemTheme = createStateFlow(PreferencesKeys.FOLLOW_SYSTEM_THEME, false)
    override val zappingMode = createStateFlow(PreferencesKeys.ZAPPING_MODE, false)
    override val brightnessGesture = createStateFlow(PreferencesKeys.BRIGHTNESS_GESTURE, false)
    override val volumeGesture = createStateFlow(PreferencesKeys.VOLUME_GESTURE, false)
    override val screencast = createStateFlow(PreferencesKeys.SCREENCAST, false)
    override val screenRotating = createStateFlow(PreferencesKeys.SCREEN_ROTATING, false)
    override val unseensMilliseconds = createStateFlow(PreferencesKeys.UNSEENS_MILLISECONDS, 3000L)
    override val reconnectMode = createStateFlow(PreferencesKeys.RECONNECT_MODE, 0)
    override val colorArgb = createStateFlow(PreferencesKeys.COLOR_ARGB, 0)
    override val tunneling = createStateFlow(PreferencesKeys.TUNNELING, false)
    override val clockMode = createStateFlow(PreferencesKeys.CLOCK_MODE, false)
    override val remoteControl = createStateFlow(PreferencesKeys.REMOTE_CONTROL, false)
    override val slider = createStateFlow(PreferencesKeys.SLIDER, false)
    override val alwaysShowReplay = createStateFlow(PreferencesKeys.ALWAYS_SHOW_REPLAY, false)
    override val playerPanel = createStateFlow(PreferencesKeys.PLAYER_PANEL, false)
    override val compactDimension = createStateFlow(PreferencesKeys.COMPACT_DIMENSION, false)
    override val autoResumeEnabled = createStateFlow(AutoResumePreferences.AUTO_RESUME_ENABLED, AutoResumePreferences.DEFAULT_AUTO_RESUME_ENABLED)
    override val launchOnBoot = createStateFlow(AutoResumePreferences.LAUNCH_ON_BOOT, AutoResumePreferences.DEFAULT_LAUNCH_ON_BOOT)
    override val startupDelay = createStateFlow(AutoResumePreferences.STARTUP_DELAY, AutoResumePreferences.DEFAULT_STARTUP_DELAY)
    override val bufferMs = createStateFlow(PreferencesKeys.BUFFER_MS, 0)
    override val streamFormat = createStateFlow(PreferencesKeys.STREAM_FORMAT, 0)
    override val videoDecryptionEnabled = createStateFlow(PreferencesKeys.VIDEO_DECRYPTION_ENABLED, true)
    
    override val favoritesJson: StateFlow<String?> = dataStore.data
        .map { it[PreferencesKeys.FAVORITES_JSON] }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), null)
    
    override val lastChannelId = createStateFlow(AutoResumePreferences.LAST_CHANNEL_ID, AutoResumePreferences.DEFAULT_LAST_CHANNEL_ID)
    override val lastStreamUrl: StateFlow<String?> = dataStore.data.map { it[AutoResumePreferences.LAST_STREAM_URL] }.stateIn(scope, SharingStarted.WhileSubscribed(5000), null)
    override val lastCategoryName: StateFlow<String?> = dataStore.data.map { it[AutoResumePreferences.LAST_CATEGORY_NAME] }.stateIn(scope, SharingStarted.WhileSubscribed(5000), null)
    override val lastPlaylistUrl: StateFlow<String?> = dataStore.data.map { it[AutoResumePreferences.LAST_PLAYLIST_URL] }.stateIn(scope, SharingStarted.WhileSubscribed(5000), null)
    override val lastPlaybackTimestamp: StateFlow<String?> = dataStore.data.map { it[AutoResumePreferences.LAST_PLAYBACK_TIMESTAMP] }.stateIn(scope, SharingStarted.WhileSubscribed(5000), null)
    override val deviceType = createStateFlow(PreferencesKeys.DEVICE_TYPE, Defaults.DEFAULT_DEVICE_TYPE)
    override val lastLayoutName = createStateFlow(PreferencesKeys.LAST_LAYOUT_NAME, Defaults.DEFAULT_LAST_LAYOUT_NAME)
    override val tvModeEnabled = createStateFlow(PreferencesKeys.TV_MODE_ENABLED, false)
    override val playerEngine = createStateFlow(PreferencesKeys.PLAYER_ENGINE, 0)
    override val autoPipMode = createStateFlow(PreferencesKeys.PIP_ENABLED, false)
    override val alwaysTrustAllSSL = createStateFlow(PreferencesKeys.ALWAYS_TRUST_ALL_SSL, true)

    override suspend fun update(key: Preferences.Key<*>, value: Any?) {
        dataStore.edit { prefs ->
            @Suppress("UNCHECKED_CAST")
            when (value) {
                null -> prefs.remove(key)
                is Boolean -> prefs[key as Preferences.Key<Boolean>] = value
                is Int -> prefs[key as Preferences.Key<Int>] = value
                is Long -> prefs[key as Preferences.Key<Long>] = value
                is String -> prefs[key as Preferences.Key<String>] = value
                else -> throw IllegalArgumentException("Unsupported preference type: ${value::class.java}")
            }
        }
    }

    override suspend fun <T> get(key: Preferences.Key<T>): T? {
        return dataStore.data.first()[key]
    }
}

object PreferencesKeys {
    val PLAYLIST_STRATEGY = intPreferencesKey("playlist-strategy")
    val ROW_COUNT = intPreferencesKey("rowCount")
    val CONNECT_TIMEOUT = longPreferencesKey("connect-timeout")
    val GOD_MODE = booleanPreferencesKey("god-mode")
    val CLIP_MODE = intPreferencesKey("clip-mode")
    val AUTO_REFRESH_CHANNELS = booleanPreferencesKey("auto-refresh-channels")
    val FULL_INFO_PLAYER = booleanPreferencesKey("full-info-player")
    val NO_PICTURE_MODE = booleanPreferencesKey("no-picture-mode")
    val DARK_MODE = booleanPreferencesKey("dark-mode")
    val USE_DYNAMIC_COLORS = booleanPreferencesKey("use-dynamic-colors")
    val FOLLOW_SYSTEM_THEME = booleanPreferencesKey("follow-system-theme")
    val ZAPPING_MODE = booleanPreferencesKey("zapping-mode")
    val BRIGHTNESS_GESTURE = booleanPreferencesKey("brightness-gesture")
    val VOLUME_GESTURE = booleanPreferencesKey("volume-gesture")
    val SCREENCAST = booleanPreferencesKey("screencast")
    val SCREEN_ROTATING = booleanPreferencesKey("screen-rotating")
    val UNSEENS_MILLISECONDS = longPreferencesKey("unseens-milliseconds")
    val RECONNECT_MODE = intPreferencesKey("reconnect-mode")
    val COLOR_ARGB = intPreferencesKey("color-argb")
    val TUNNELING = booleanPreferencesKey("tunneling")
    val CLOCK_MODE = booleanPreferencesKey("12h-clock-mode")
    val REMOTE_CONTROL = booleanPreferencesKey("remote-control")
    val SLIDER = booleanPreferencesKey("slider")
    val ALWAYS_SHOW_REPLAY = booleanPreferencesKey("always-show-replay")
    val PLAYER_PANEL = booleanPreferencesKey("player_panel")
    val COMPACT_DIMENSION = booleanPreferencesKey("compact-dimension")
    val BUFFER_MS = intPreferencesKey("buffer-ms")
    val STREAM_FORMAT = intPreferencesKey("stream-format")
    val VIDEO_DECRYPTION_ENABLED = booleanPreferencesKey("video-decryption-enabled")
    val CUSTOM_USER_AGENT = stringPreferencesKey("custom-user-agent")
    val PROXY_ENDPOINT = stringPreferencesKey("proxy-endpoint")
    val EPG_REFRESH_INTERVAL_HOURS = intPreferencesKey("epg-refresh-hours")
    val EPG_TIME_OFFSET_MINUTES = intPreferencesKey("epg-time-offset-mins")
    val HIDE_ADULT = booleanPreferencesKey("hide-adult")
    val AUTO_UPDATE_PLAYLIST = booleanPreferencesKey("auto-update-playlist")
    val PIP_ENABLED = booleanPreferencesKey("pip-enabled")
    val PARENTAL_PIN = stringPreferencesKey("parental-pin")
    val REMEMBER_LAST_CHANNEL = booleanPreferencesKey("remember-last-channel")
    val LOGO_PRIORITY = intPreferencesKey("logo-priority")
    val PLAYER_ENGINE = intPreferencesKey("player-engine")
    val DECODE_MODE = intPreferencesKey("decode-mode")
    val FAVORITES_JSON = stringPreferencesKey("favorites-json")
    val DEVICE_TYPE = stringPreferencesKey("device-type")
    val LAST_LAYOUT_NAME = stringPreferencesKey("last-layout-name")
    val TV_MODE_ENABLED = booleanPreferencesKey("tv-mode-enabled")
    val ALWAYS_TRUST_ALL_SSL = booleanPreferencesKey("always-trust-all-ssl")

    object Defaults {
        const val DEFAULT_DEVICE_TYPE = "mobile"
        const val DEFAULT_LAST_LAYOUT_NAME = "grid"
    }
}

object AutoResumePreferences {
    val AUTO_RESUME_ENABLED = booleanPreferencesKey("auto_resume_enabled")
    val LAUNCH_ON_BOOT = booleanPreferencesKey("launch_on_boot")
    val STARTUP_DELAY = intPreferencesKey("startup_delay")
    val LAST_CHANNEL_ID = intPreferencesKey("last_channel_id")
    val LAST_STREAM_URL = stringPreferencesKey("last_stream_url")
    val LAST_CATEGORY_NAME = stringPreferencesKey("last_category_name")
    val LAST_PLAYLIST_URL = stringPreferencesKey("last_playlist_url")
    val LAST_PLAYBACK_TIMESTAMP = stringPreferencesKey("last_playback_timestamp")
    
    const val DEFAULT_AUTO_RESUME_ENABLED = true
    const val DEFAULT_LAUNCH_ON_BOOT = false
    const val DEFAULT_STARTUP_DELAY = 0
    const val DEFAULT_LAST_CHANNEL_ID = -1
    const val DEFAULT_LAST_STREAM_URL = ""
    const val DEFAULT_LAST_CATEGORY_NAME = ""
    const val DEFAULT_LAST_PLAYLIST_URL = ""
    const val DEFAULT_LAST_PLAYBACK_TIMESTAMP = ""
}

object Defaults {
    const val DEFAULT_DEVICE_TYPE = "MOBILE"
    const val DEFAULT_LAST_LAYOUT_NAME = ""
}

@Composable
fun <T> rememberPreferenceAsState(
    key: Preferences.Key<T>,
    defaultValue: T
): State<T> {
    val context = LocalContext.current
    val dataStore = context.dataStore
    return produceState(initialValue = defaultValue, key1 = dataStore, key2 = key) {
        dataStore.data
            .map { it[key] ?: defaultValue }
            .collect { value = it }
    }
}

@Composable
fun <T> rememberMutablePreference(
    key: Preferences.Key<T>,
    defaultValue: T
): MutableState<T> {
    val state = rememberPreferenceAsState(key, defaultValue)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    return remember(key, defaultValue) {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(newValue) {
                    scope.launch {
                        context.dataStore.edit { prefs ->
                            @Suppress("UNCHECKED_CAST")
                            when (newValue) {
                                is Boolean -> prefs[key as Preferences.Key<Boolean>] = newValue
                                is Int -> prefs[key as Preferences.Key<Int>] = newValue
                                is Long -> prefs[key as Preferences.Key<Long>] = newValue
                                is String -> prefs[key as Preferences.Key<String>] = newValue
                                null -> prefs.remove(key)
                                else -> { }
                            }
                        }
                    }
                }
            override fun component1(): T = value
            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

private var _settings: Settings? = null
val Context.settings: Settings
    get() {
        return _settings ?: synchronized(AppPreferences::class.java) {
            _settings ?: AppPreferencesImpl(applicationContext.dataStore, CoroutineScope(Dispatchers.IO)).also { _settings = it }
        }
    }

suspend fun <T> AppPreferences.set(key: Preferences.Key<T>, value: T) = update(key, value)

fun <T> AppPreferences.flowOf(key: Preferences.Key<T>): Flow<T> {
    @Suppress("UNCHECKED_CAST")
    return when (key.name) {
        PreferencesKeys.ZAPPING_MODE.name -> zappingMode as Flow<T>
        PreferencesKeys.FOLLOW_SYSTEM_THEME.name -> followSystemTheme as Flow<T>
        PreferencesKeys.UNSEENS_MILLISECONDS.name -> unseensMilliseconds as Flow<T>
        else -> {
            Log.e("Preferences", "Flow not supported for key: ${key.name}")
            kotlinx.coroutines.flow.flow<T> { }
        }
    }
}
