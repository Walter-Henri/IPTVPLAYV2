package com.m3u.core.foundation.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Especial para Android TV. Adiciona borda colorida e efeito de escala ao foco.
 */
fun Modifier.tvFocusHighlight(
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    borderWidth: Dp = 2.dp,
    focusedColor: Color = PremiumColors.Accent,
    unfocusedColor: Color = Color.Transparent,
    scaleOnFocus: Float = 1.05f
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) scaleOnFocus else 1f,
        label = "scale"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) focusedColor else unfocusedColor,
        label = "borderColor"
    )
    
    this
        .onFocusChanged { isFocused = it.isFocused }
        .scale(scale)
        .border(borderWidth, borderColor, shape)
}
