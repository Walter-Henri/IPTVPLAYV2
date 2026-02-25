package com.m3u.core.foundation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Sistema de design adaptativo para multiplataforma
 * Suporta: Smartphones, Tablets, TVs Android e Smart TVs
 */
object AdaptiveDesign {
    
    /**
     * Tipo de dispositivo detectado
     */
    enum class DeviceType {
        PHONE,      // < 600dp
        TABLET,     // 600dp - 840dp
        TV,         // > 840dp
        LARGE_TV    // > 1200dp
    }
    
    /**
     * Classe de tamanho para componentes
     */
    enum class ComponentSize {
        COMPACT,    // Phone
        MEDIUM,     // Tablet
        EXPANDED,   // TV
        LARGE       // Large TV
    }
    
    /**
     * Detecta o tipo de dispositivo baseado na largura da tela
     */
    @Composable
    fun getDeviceType(): DeviceType {
        val configuration = LocalConfiguration.current
        val screenWidthDp = configuration.screenWidthDp
        
        return when {
            screenWidthDp < 600 -> DeviceType.PHONE
            screenWidthDp < 840 -> DeviceType.TABLET
            screenWidthDp < 1200 -> DeviceType.TV
            else -> DeviceType.LARGE_TV
        }
    }
    
    /**
     * Retorna o tamanho do componente baseado no dispositivo
     */
    @Composable
    fun getComponentSize(): ComponentSize {
        return when (getDeviceType()) {
            DeviceType.PHONE -> ComponentSize.COMPACT
            DeviceType.TABLET -> ComponentSize.MEDIUM
            DeviceType.TV -> ComponentSize.EXPANDED
            DeviceType.LARGE_TV -> ComponentSize.LARGE
        }
    }
    
    /**
     * Padding adaptativo baseado no dispositivo
     */
    @Composable
    fun adaptivePadding(): PaddingValues {
        return when (getDeviceType()) {
            DeviceType.PHONE -> PaddingValues(16.dp)
            DeviceType.TABLET -> PaddingValues(24.dp)
            DeviceType.TV -> PaddingValues(32.dp)
            DeviceType.LARGE_TV -> PaddingValues(48.dp)
        }
    }
    
    /**
     * Espaçamento adaptativo entre elementos
     */
    @Composable
    fun adaptiveSpacing(): Dp {
        return when (getDeviceType()) {
            DeviceType.PHONE -> 12.dp
            DeviceType.TABLET -> 16.dp
            DeviceType.TV -> 24.dp
            DeviceType.LARGE_TV -> 32.dp
        }
    }
    
    /**
     * Altura de card adaptativa
     */
    @Composable
    fun adaptiveCardHeight(): Dp {
        return when (getDeviceType()) {
            DeviceType.PHONE -> 120.dp
            DeviceType.TABLET -> 140.dp
            DeviceType.TV -> 180.dp
            DeviceType.LARGE_TV -> 220.dp
        }
    }
    
    /**
     * Tamanho de ícone adaptativo
     */
    @Composable
    fun adaptiveIconSize(): Dp {
        return when (getDeviceType()) {
            DeviceType.PHONE -> 24.dp
            DeviceType.TABLET -> 28.dp
            DeviceType.TV -> 36.dp
            DeviceType.LARGE_TV -> 48.dp
        }
    }
    
    /**
     * Tamanho de logo/thumbnail adaptativo
     */
    @Composable
    fun adaptiveThumbnailSize(): Dp {
        return when (getDeviceType()) {
            DeviceType.PHONE -> 80.dp
            DeviceType.TABLET -> 100.dp
            DeviceType.TV -> 140.dp
            DeviceType.LARGE_TV -> 180.dp
        }
    }
    
    /**
     * Border radius adaptativo
     */
    @Composable
    fun adaptiveCornerRadius(): Dp {
        return when (getDeviceType()) {
            DeviceType.PHONE -> 16.dp
            DeviceType.TABLET -> 20.dp
            DeviceType.TV -> 24.dp
            DeviceType.LARGE_TV -> 28.dp
        }
    }
    
    /**
     * Número de colunas para grid adaptativo
     */
    @Composable
    fun adaptiveGridColumns(): Int {
        return when (getDeviceType()) {
            DeviceType.PHONE -> 1
            DeviceType.TABLET -> 2
            DeviceType.TV -> 3
            DeviceType.LARGE_TV -> 4
        }
    }
    
    /**
     * Verifica se é um dispositivo TV
     */
    @Composable
    fun isTvDevice(): Boolean {
        return when (getDeviceType()) {
            DeviceType.TV, DeviceType.LARGE_TV -> true
            else -> false
        }
    }
    
    /**
     * Verifica se é um dispositivo móvel
     */
    @Composable
    fun isMobileDevice(): Boolean {
        return when (getDeviceType()) {
            DeviceType.PHONE, DeviceType.TABLET -> true
            else -> false
        }
    }
}

/**
 * Modifier Plugin para aplicar padding adaptativo
 */
@Composable
fun Modifier.adaptivePadding(): Modifier {
    return this.padding(AdaptiveDesign.adaptivePadding())
}

/**
 * Modifier Plugin para aplicar espaçamento adaptativo
 */
@Composable
fun Modifier.adaptiveSpacing(): Modifier {
    val spacing = AdaptiveDesign.adaptiveSpacing()
    return this.padding(spacing)
}
