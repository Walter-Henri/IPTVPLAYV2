package com.m3u.core.foundation.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.m3u.core.foundation.ui.CustomShapes
import com.m3u.core.foundation.ui.PremiumColors

/**
 * Card premium com elevação e sombra sofisticada
 */
@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    elevation: Dp = 4.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null // Ripple can be added if needed
                    ) { onClick() }
                } else Modifier
            ),
        shape = CustomShapes.ChannelCard,
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

/**
 * Botão premium com gradiente
 */
@Composable
fun PremiumGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val scale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.95f,
        animationSpec = tween(durationMillis = 150),
        label = "buttonScale"
    )
    
    Button(
        onClick = onClick,
        modifier = modifier
            .scale(scale)
            .height(56.dp),
        enabled = enabled,
        shape = CustomShapes.FAB,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            PremiumColors.GradientStart,
                            PremiumColors.GradientMiddle,
                            PremiumColors.GradientEnd
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Chip premium para categorias
 */
@Composable
fun PremiumCategoryChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val backgroundColor = if (selected) color else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    
    Surface(
        modifier = modifier
            .height(32.dp)
            .clickable(onClick = onClick),
        shape = CustomShapes.CategoryChip,
        color = backgroundColor,
        tonalElevation = if (selected) 4.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

/**
 * Indicador de status premium
 */
@Composable
fun PremiumStatusIndicator(
    status: ChannelStatus,
    modifier: Modifier = Modifier
) {
    val (color, icon, text) = when (status) {
        ChannelStatus.Online -> Triple(
            PremiumColors.SuccessLight,
            Icons.Default.Check,
            "Online"
        )
        ChannelStatus.Offline -> Triple(
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Default.Error,
            "Offline"
        )
        ChannelStatus.Loading -> Triple(
            PremiumColors.InfoLight,
            Icons.Default.Info,
            "Carregando"
        )
        ChannelStatus.Error -> Triple(
            PremiumColors.ErrorLight,
            Icons.Default.Warning,
            "Erro"
        )
    }
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Loading shimmer effect premium
 */
@Composable
fun PremiumShimmerEffect(
    modifier: Modifier = Modifier,
    isLoading: Boolean = true
) {
    if (isLoading) {
        Box(
            modifier = modifier
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ),
                    shape = CustomShapes.Thumbnail
                )
        )
    }
}

/**
 * Snackbar premium com ícone
 */
@Composable
fun PremiumSnackbar(
    message: String,
    type: SnackbarType = SnackbarType.Info,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val (backgroundColor, icon) = when (type) {
        SnackbarType.Success -> Pair(PremiumColors.SuccessLight, Icons.Default.Check)
        SnackbarType.Error -> Pair(PremiumColors.ErrorLight, Icons.Default.Error)
        SnackbarType.Warning -> Pair(PremiumColors.WarningLight, Icons.Default.Warning)
        SnackbarType.Info -> Pair(PremiumColors.InfoLight, Icons.Default.Info)
    }
    
    Snackbar(
        modifier = Modifier.padding(16.dp),
        action = if (actionLabel != null && onAction != null) {
            {
                TextButton(onClick = onAction) {
                    Text(actionLabel, color = Color.White)
                }
            }
        } else null,
        dismissAction = {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Fechar",
                    tint = Color.White
                )
            }
        },
        containerColor = backgroundColor,
        contentColor = Color.White,
        shape = CustomShapes.Dialog
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }
    }
}

/**
 * Enums para componentes
 */
enum class ChannelStatus {
    Online, Offline, Loading, Error
}

enum class SnackbarType {
    Success, Error, Warning, Info
}

/**
 * Botão de alternância de tema premium
 */
@Composable
fun PremiumThemeToggle(
    isDarkTheme: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = { onToggle(!isDarkTheme) },
        modifier = modifier
    ) {
        Icon(
            imageVector = if (isDarkTheme) {
                Icons.Default.Warning // Placeholder
            } else {
                Icons.Default.Info // Placeholder
            },
            contentDescription = if (isDarkTheme) "Modo Claro" else "Modo Escuro",
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Card de canal premium com thumbnail e informações
 */
@Composable
fun PremiumChannelCard(
    title: String,
    subtitle: String?,
    thumbnailUrl: String?,
    status: ChannelStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    PremiumCard(
        modifier = modifier,
        onClick = onClick,
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail placeholder
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CustomShapes.Thumbnail)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                PremiumStatusIndicator(status = status)
            }
        }
    }
}
