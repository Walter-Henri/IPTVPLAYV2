package com.m3u.core.foundation.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.m3u.core.foundation.ui.PremiumColors
import com.m3u.core.foundation.ui.PremiumAnimations

/**
 * Card de canal premium ADAPTATIVO para multiplataforma
 * 
 * Características:
 * - Design responsivo (Phone, Tablet, TV, Large TV)
 * - Suporte completo para D-Pad (TVs)
 * - Glassmorphism effect
 * - Animações suaves de hover e focus
 * - Gradientes sofisticados
 * - Sombras dinâmicas
 */
@Composable
fun AdaptiveChannelCard(
    title: String,
    category: String,
    logoUrl: String?,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    val deviceType = AdaptiveDesign.getDeviceType()
    val isTv = AdaptiveDesign.isTvDevice()
    
    // Estados de interação
    var isPressed by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    // Animações
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.95f
            isFocused && isTv -> 1.05f  // Efeito de zoom em TVs
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    val elevation by animateDpAsState(
        targetValue = when {
            isPressed -> 2.dp
            isFocused && isTv -> 16.dp  // Elevação maior em TVs
            else -> 8.dp
        },
        animationSpec = PremiumAnimations.normalTween(),
        label = "elevation"
    )
    
    // Dimensões adaptativas
    val cardHeight = AdaptiveDesign.adaptiveCardHeight()
    val cornerRadius = AdaptiveDesign.adaptiveCornerRadius()
    val thumbnailSize = AdaptiveDesign.adaptiveThumbnailSize()
    val iconSize = AdaptiveDesign.adaptiveIconSize()
    val spacing = AdaptiveDesign.adaptiveSpacing()
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(cardHeight)
            .scale(scale)
            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(cornerRadius),
                ambientColor = if (isFocused && isTv) 
                    PremiumColors.Accent.copy(alpha = 0.3f) 
                else 
                    PremiumColors.Accent.copy(alpha = 0.1f),
                spotColor = if (isFocused && isTv) 
                    PremiumColors.Accent.copy(alpha = 0.4f) 
                else 
                    PremiumColors.Accent.copy(alpha = 0.2f)
            )
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
            .focusable(interactionSource = interactionSource),
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused && isTv) 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
            else 
                MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isFocused && isTv) {
            androidx.compose.foundation.BorderStroke(
                width = 3.dp,
                color = PremiumColors.Accent
            )
        } else null
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = PremiumColors.Accent),
                    onClick = {
                        isPressed = true
                        onClick()
                    }
                )
        ) {
            // Gradiente de fundo sutil
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            )
                        )
                    )
            )
            
            // Brilho de foco para TVs
            if (isFocused && isTv) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    PremiumColors.Accent.copy(alpha = 0.1f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
            
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(spacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Logo do canal com efeito de brilho
                Box(
                    modifier = Modifier
                        .size(thumbnailSize)
                        .clip(RoundedCornerShape(cornerRadius * 0.8f))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    
                    // Indicador de reprodução
                    if (isPlaying) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            PremiumColors.Accent.copy(alpha = 0.3f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "alpha"
                        )
                        
                        val playIconSize = when (deviceType) {
                            AdaptiveDesign.DeviceType.PHONE -> 32.dp
                            AdaptiveDesign.DeviceType.TABLET -> 40.dp
                            AdaptiveDesign.DeviceType.TV -> 56.dp
                            AdaptiveDesign.DeviceType.LARGE_TV -> 72.dp
                        }
                        
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(playIconSize)
                                .graphicsLayer { this.alpha = alpha }
                                .background(
                                    color = PremiumColors.Accent,
                                    shape = RoundedCornerShape(cornerRadius * 0.5f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Playing",
                                tint = Color.White,
                                modifier = Modifier.size(playIconSize * 0.6f)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(spacing))
                
                // Informações do canal
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = title,
                        style = when (deviceType) {
                            AdaptiveDesign.DeviceType.PHONE -> MaterialTheme.typography.titleLarge
                            AdaptiveDesign.DeviceType.TABLET -> MaterialTheme.typography.titleLarge
                            AdaptiveDesign.DeviceType.TV -> MaterialTheme.typography.headlineSmall
                            AdaptiveDesign.DeviceType.LARGE_TV -> MaterialTheme.typography.headlineMedium
                        }.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = if (isFocused && isTv) 
                            PremiumColors.Accent 
                        else 
                            MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(spacing * 0.5f))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(when (deviceType) {
                                    AdaptiveDesign.DeviceType.PHONE -> 6.dp
                                    AdaptiveDesign.DeviceType.TABLET -> 8.dp
                                    AdaptiveDesign.DeviceType.TV -> 10.dp
                                    AdaptiveDesign.DeviceType.LARGE_TV -> 12.dp
                                })
                                .background(
                                    color = PremiumColors.Accent,
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        )
                        
                        Spacer(modifier = Modifier.width(spacing * 0.7f))
                        
                        Text(
                            text = category.ifBlank { "Geral" },
                            style = when (deviceType) {
                                AdaptiveDesign.DeviceType.PHONE -> MaterialTheme.typography.bodyMedium
                                AdaptiveDesign.DeviceType.TABLET -> MaterialTheme.typography.bodyLarge
                                AdaptiveDesign.DeviceType.TV -> MaterialTheme.typography.titleMedium
                                AdaptiveDesign.DeviceType.LARGE_TV -> MaterialTheme.typography.titleLarge
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                // Ícone de navegação (apenas em dispositivos móveis)
                if (!isTv) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Abrir",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
    }
    
    LaunchedEffect(isPressed) {
        if (isPressed) {
            kotlinx.coroutines.delay(100)
            isPressed = false
        }
    }
}

/**
 * Card de canal compacto ADAPTATIVO para listas densas
 */
@Composable
fun CompactChannelCard(
    title: String,
    category: String,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    val deviceType = AdaptiveDesign.getDeviceType()
    val isTv = AdaptiveDesign.isTvDevice()
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val cardHeight = when (deviceType) {
        AdaptiveDesign.DeviceType.PHONE -> 64.dp
        AdaptiveDesign.DeviceType.TABLET -> 72.dp
        AdaptiveDesign.DeviceType.TV -> 88.dp
        AdaptiveDesign.DeviceType.LARGE_TV -> 104.dp
    }
    
    val cornerRadius = AdaptiveDesign.adaptiveCornerRadius() * 0.75f
    val spacing = AdaptiveDesign.adaptiveSpacing()
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(cardHeight)
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
            .focusable(interactionSource = interactionSource)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(cornerRadius),
        color = when {
            isFocused && isTv -> PremiumColors.Accent.copy(alpha = 0.2f)
            isPlaying -> PremiumColors.Accent.copy(alpha = 0.1f)
            else -> MaterialTheme.colorScheme.surface
        },
        tonalElevation = when {
            isFocused && isTv -> 8.dp
            isPlaying -> 2.dp
            else -> 0.dp
        },
        border = if (isFocused && isTv) {
            androidx.compose.foundation.BorderStroke(
                width = 2.dp,
                color = PremiumColors.Accent
            )
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = spacing, vertical = spacing * 0.8f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isPlaying || (isFocused && isTv)) {
                Box(
                    modifier = Modifier
                        .size(when (deviceType) {
                            AdaptiveDesign.DeviceType.PHONE -> 8.dp
                            AdaptiveDesign.DeviceType.TABLET -> 10.dp
                            AdaptiveDesign.DeviceType.TV -> 12.dp
                            AdaptiveDesign.DeviceType.LARGE_TV -> 14.dp
                        })
                        .background(
                            color = PremiumColors.Accent,
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(spacing))
            }
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = when (deviceType) {
                        AdaptiveDesign.DeviceType.PHONE -> MaterialTheme.typography.bodyLarge
                        AdaptiveDesign.DeviceType.TABLET -> MaterialTheme.typography.titleMedium
                        AdaptiveDesign.DeviceType.TV -> MaterialTheme.typography.titleLarge
                        AdaptiveDesign.DeviceType.LARGE_TV -> MaterialTheme.typography.headlineSmall
                    }.copy(
                        fontWeight = if (isPlaying || (isFocused && isTv)) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = when {
                        isFocused && isTv -> PremiumColors.Accent
                        isPlaying -> PremiumColors.Accent
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = category.ifBlank { "Geral" },
                    style = when (deviceType) {
                        AdaptiveDesign.DeviceType.PHONE -> MaterialTheme.typography.bodySmall
                        AdaptiveDesign.DeviceType.TABLET -> MaterialTheme.typography.bodyMedium
                        AdaptiveDesign.DeviceType.TV -> MaterialTheme.typography.bodyLarge
                        AdaptiveDesign.DeviceType.LARGE_TV -> MaterialTheme.typography.titleMedium
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
