package com.m3u.universal

import android.app.UiModeManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.window.layout.WindowMetricsCalculator
import com.m3u.data.service.PlaybackManager
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.settings
import com.m3u.core.architecture.preferences.set
import com.m3u.core.architecture.preferences.rememberPreferenceAsState
import com.m3u.core.foundation.ui.PremiumTheme
import com.m3u.universal.ui.common.AppRoot
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * MainActivity - Activity principal do aplicativo M3U Universal
 * 
 * Suporta múltiplas plataformas:
 * - Smartphones Android (API 26+)
 * - Tablets Android
 * - Android TV
 * - Smart TVs Android
 * 
 * Detecta automaticamente o tipo de dispositivo e adapta a UI/UX adequadamente.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var playbackManager: PlaybackManager

    private val _intentFlow = MutableStateFlow<Intent?>(null)
    
    // Cache the TV mode check
    private val isTvMode by lazy {
        val uiModeManager = getSystemService(UI_MODE_SERVICE) as UiModeManager
        uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        // Edge-to-edge for mobile, standard for TV
        if (!isTvMode) {
            enableEdgeToEdge()
        }

        super.onCreate(savedInstanceState)
        
        _intentFlow.value = intent

        // Detect device configuration
        val bounds = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(this).bounds
        val widthDp = bounds.width() / resources.displayMetrics.density
        
        val deviceType = when {
            isTvMode -> "TV"
            widthDp < 600 -> "MOBILE"
            widthDp < 840 -> "TABLET"
            else -> "DESKTOP" // Dual-pane or large tablets
        }
        
        val layoutName = "layout_${deviceType.lowercase()}.xml"

        lifecycleScope.launch {
            applicationContext.settings.apply {
                set(PreferencesKeys.DEVICE_TYPE, deviceType)
                set(PreferencesKeys.LAST_LAYOUT_NAME, layoutName)
                set(PreferencesKeys.TV_MODE_ENABLED, isTvMode)
            }
        }

        setContent {
            val followSystem by rememberPreferenceAsState(PreferencesKeys.FOLLOW_SYSTEM_THEME, false)
            val darkMode by rememberPreferenceAsState(PreferencesKeys.DARK_MODE, false)
            val dynamicColors by rememberPreferenceAsState(PreferencesKeys.USE_DYNAMIC_COLORS, false)
            
            val currentIntent by _intentFlow.collectAsState()

            PremiumTheme(
                darkTheme = if (followSystem) androidx.compose.foundation.isSystemInDarkTheme() else darkMode,
                dynamicColor = dynamicColors
            ) {
                AppRoot(
                    isTv = isTvMode,
                    activity = this,
                    intent = currentIntent ?: intent,
                    playbackManager = playbackManager,
                    lifecycleScope = lifecycleScope,
                    backDispatcher = onBackPressedDispatcher
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        _intentFlow.value = intent
    }

    override fun onResume() {
        super.onResume()
        if (isTvMode) {
            window.decorView.requestFocus()
        }
    }
}
