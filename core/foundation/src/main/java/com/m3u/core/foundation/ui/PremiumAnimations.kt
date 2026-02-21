package com.m3u.core.foundation.ui

import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Animações premium para o M3U Play
 * Transições suaves e elegantes que elevam a experiência do usuário
 */
object PremiumAnimations {
    
    // ═══════════════════════════════════════════════════════════════════
    // DURAÇÕES - Timing Perfeito
    // ═══════════════════════════════════════════════════════════════════
    
    const val DURATION_INSTANT = 100
    const val DURATION_FAST = 200
    const val DURATION_NORMAL = 300
    const val DURATION_SLOW = 400
    const val DURATION_VERY_SLOW = 600
    
    // ═══════════════════════════════════════════════════════════════════
    // EASING CURVES - Movimento Natural
    // ═══════════════════════════════════════════════════════════════════
    
    val EaseInOut = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
    val EaseOut = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
    val EaseIn = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)
    val EaseOutBack = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1.0f)
    val EaseInOutBack = CubicBezierEasing(0.68f, -0.6f, 0.32f, 1.6f)
    
    // ═══════════════════════════════════════════════════════════════════
    // SPECS DE ANIMAÇÃO - Configurações Prontas
    // ═══════════════════════════════════════════════════════════════════
    
    val FastSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessHigh
    )
    
    val SmoothSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
    
    val BouncySpring = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    
    fun <T> fastTween() = tween<T>(
        durationMillis = DURATION_FAST,
        easing = EaseOut
    )
    
    fun <T> normalTween() = tween<T>(
        durationMillis = DURATION_NORMAL,
        easing = EaseInOut
    )
    
    fun <T> slowTween() = tween<T>(
        durationMillis = DURATION_SLOW,
        easing = EaseInOut
    )
    
    // ═══════════════════════════════════════════════════════════════════
    // TRANSIÇÕES COMPOSTAS - Efeitos Complexos
    // ═══════════════════════════════════════════════════════════════════
    
    /**
     * Fade + Slide suave de baixo para cima
     */
    val FadeSlideUp = fadeIn(
        animationSpec = tween(DURATION_NORMAL, easing = EaseOut)
    ) + slideInVertically(
        animationSpec = tween(DURATION_NORMAL, easing = EaseOut),
        initialOffsetY = { it / 4 }
    )
    
    val FadeSlideUpExit = fadeOut(
        animationSpec = tween(DURATION_FAST, easing = EaseIn)
    ) + slideOutVertically(
        animationSpec = tween(DURATION_FAST, easing = EaseIn),
        targetOffsetY = { -it / 4 }
    )
    
    /**
     * Fade + Expand vertical (para listas)
     */
    val FadeExpand = fadeIn(
        animationSpec = tween(DURATION_NORMAL, easing = EaseOut)
    ) + expandVertically(
        animationSpec = tween(DURATION_NORMAL, easing = EaseOut)
    )
    
    val FadeShrink = fadeOut(
        animationSpec = tween(DURATION_FAST, easing = EaseIn)
    ) + shrinkVertically(
        animationSpec = tween(DURATION_FAST, easing = EaseIn)
    )
    
    // ═══════════════════════════════════════════════════════════════════
    // ANIMAÇÕES INFINITAS - Loading e Efeitos Contínuos
    // ═══════════════════════════════════════════════════════════════════
    
    val InfiniteRotation = infiniteRepeatable<Float>(
        animation = tween(1000, easing = LinearEasing),
        repeatMode = RepeatMode.Restart
    )
    
    val InfinitePulse = infiniteRepeatable<Float>(
        animation = tween(1000, easing = EaseInOut),
        repeatMode = RepeatMode.Reverse
    )
    
    val InfiniteShimmer = infiniteRepeatable<Float>(
        animation = tween(1500, easing = LinearEasing),
        repeatMode = RepeatMode.Restart
    )
}

/**
 * Componente auxiliar para animações de visibilidade com configurações premium
 */
@Composable
fun PremiumAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = PremiumAnimations.FadeSlideUp,
        exit = PremiumAnimations.FadeSlideUpExit,
        content = { content() }
    )
}
