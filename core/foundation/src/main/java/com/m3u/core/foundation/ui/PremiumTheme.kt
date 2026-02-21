package com.m3u.core.foundation.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Esquema de cores premium ultra-elegante para tema claro
 */
private val LightColorScheme = lightColorScheme(
    primary = PremiumColors.PrimaryLight,
    onPrimary = PremiumColors.OnPrimaryLight,
    primaryContainer = PremiumColors.PrimaryVariantLight,
    onPrimaryContainer = PremiumColors.OnPrimaryLight,
    
    secondary = PremiumColors.SecondaryLight,
    onSecondary = PremiumColors.OnSecondaryLight,
    secondaryContainer = PremiumColors.SecondaryVariantLight,
    onSecondaryContainer = PremiumColors.OnSecondaryLight,
    
    tertiary = PremiumColors.Accent,
    onTertiary = PremiumColors.OnPrimaryLight,
    tertiaryContainer = PremiumColors.AccentVariant,
    onTertiaryContainer = PremiumColors.OnPrimaryLight,
    
    error = PremiumColors.ErrorLight,
    onError = PremiumColors.OnPrimaryLight,
    errorContainer = PremiumColors.ErrorLight.copy(alpha = 0.1f),
    onErrorContainer = PremiumColors.ErrorLight,
    
    background = PremiumColors.BackgroundLight,
    onBackground = PremiumColors.OnBackgroundLight,
    
    surface = PremiumColors.SurfaceLight,
    onSurface = PremiumColors.OnSurfaceLight,
    surfaceVariant = PremiumColors.SurfaceElevated1Light,
    onSurfaceVariant = PremiumColors.OnSurfaceVariantLight,
    
    outline = PremiumColors.OutlineLight,
    outlineVariant = PremiumColors.OutlineVariantLight,
    
    scrim = PremiumColors.ScrimLight,
    
    inverseSurface = PremiumColors.SurfaceDark,
    inverseOnSurface = PremiumColors.OnSurfaceDark,
    inversePrimary = PremiumColors.PrimaryDark,
    
    surfaceTint = PremiumColors.Accent
)

/**
 * Esquema de cores premium ultra-elegante para tema escuro
 * Otimizado para displays OLED com preto absoluto
 */
private val DarkColorScheme = darkColorScheme(
    primary = PremiumColors.PrimaryDark,
    onPrimary = PremiumColors.OnPrimaryDark,
    primaryContainer = PremiumColors.PrimaryVariantDark,
    onPrimaryContainer = PremiumColors.OnPrimaryDark,
    
    secondary = PremiumColors.SecondaryDark,
    onSecondary = PremiumColors.OnSecondaryDark,
    secondaryContainer = PremiumColors.SecondaryVariantDark,
    onSecondaryContainer = PremiumColors.OnSecondaryDark,
    
    tertiary = PremiumColors.Accent,
    onTertiary = PremiumColors.OnPrimaryDark,
    tertiaryContainer = PremiumColors.AccentVariant,
    onTertiaryContainer = PremiumColors.OnPrimaryDark,
    
    error = PremiumColors.ErrorDark,
    onError = PremiumColors.OnPrimaryDark,
    errorContainer = PremiumColors.ErrorDark.copy(alpha = 0.1f),
    onErrorContainer = PremiumColors.ErrorDark,
    
    background = PremiumColors.BackgroundDark,
    onBackground = PremiumColors.OnBackgroundDark,
    
    surface = PremiumColors.SurfaceDark,
    onSurface = PremiumColors.OnSurfaceDark,
    surfaceVariant = PremiumColors.SurfaceElevated1Dark,
    onSurfaceVariant = PremiumColors.OnSurfaceVariantDark,
    
    outline = PremiumColors.OutlineDark,
    outlineVariant = PremiumColors.OutlineVariantDark,
    
    scrim = PremiumColors.ScrimDark,
    
    inverseSurface = PremiumColors.SurfaceLight,
    inverseOnSurface = PremiumColors.OnSurfaceLight,
    inversePrimary = PremiumColors.PrimaryLight,
    
    surfaceTint = PremiumColors.Accent
)

/**
 * Tema premium do M3U Play - Versão Ultra-Elegante
 * 
 * Características:
 * - Design minimalista luxuoso
 * - Suporte completo para Material You (Android 12+)
 * - Otimizado para displays OLED
 * - Transições suaves e animações fluidas
 * - Acessibilidade WCAG AAA
 * 
 * @param darkTheme Se true, usa tema escuro. Por padrão, segue o sistema.
 * @param dynamicColor Se true, usa cores dinâmicas do Material You (Android 12+)
 * @param content Conteúdo do composable
 */
@Composable
fun PremiumTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            
            // Configuração moderna de cores do sistema (Android 15+)
            if (Build.VERSION.SDK_INT >= 35) {
                // Android 15+ usa edge-to-edge nativo
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
            } else {
                // Versões anteriores usam cores do esquema
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
            }
            
            // Configuração de ícones da barra de status
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
            
            // Ativar edge-to-edge para experiência imersiva
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = PremiumTypography,
        shapes = PremiumShapes
    ) {
        androidx.compose.material3.Surface(
            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
            color = colorScheme.background,
            contentColor = colorScheme.onBackground,
            content = content
        )
    }
}

/**
 * Variante do tema premium sem cores dinâmicas
 * Útil para manter consistência visual em todas as versões do Android
 */
@Composable
fun PremiumThemeStatic(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    PremiumTheme(
        darkTheme = darkTheme,
        dynamicColor = false,
        content = content
    )
}
