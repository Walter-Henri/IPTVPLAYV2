package com.m3u.universal.ui.phone.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.m3u.core.foundation.ui.PremiumColors

@Composable
fun DrawerHeader() {
    val deviceType = com.m3u.core.foundation.ui.components.AdaptiveDesign.getDeviceType()
    val spacing = com.m3u.core.foundation.ui.components.AdaptiveDesign.adaptiveSpacing()
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        PremiumColors.GradientStart,
                        PremiumColors.GradientMiddle,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(spacing * 2)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Avatar com borda de acento
            Box(
                modifier = Modifier
                    .size(when (deviceType) {
                        com.m3u.core.foundation.ui.components.AdaptiveDesign.DeviceType.PHONE -> 72.dp
                        com.m3u.core.foundation.ui.components.AdaptiveDesign.DeviceType.TABLET -> 84.dp
                        else -> 96.dp
                    })
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(
                                PremiumColors.Accent,
                                PremiumColors.AccentVariant
                            )
                        ),
                        shape = CircleShape
                    )
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = PremiumColors.Accent,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(when (deviceType) {
                            com.m3u.core.foundation.ui.components.AdaptiveDesign.DeviceType.PHONE -> 36.dp
                            com.m3u.core.foundation.ui.components.AdaptiveDesign.DeviceType.TABLET -> 42.dp
                            else -> 48.dp
                        })
                )
            }
            
            Spacer(Modifier.height(spacing * 1.5f))
            
            Text(
                "M3U Play Premium",
                style = when (deviceType) {
                    com.m3u.core.foundation.ui.components.AdaptiveDesign.DeviceType.PHONE -> MaterialTheme.typography.headlineSmall
                    com.m3u.core.foundation.ui.components.AdaptiveDesign.DeviceType.TABLET -> MaterialTheme.typography.headlineMedium
                    else -> MaterialTheme.typography.headlineLarge
                }.copy(
                    fontWeight = FontWeight.Bold,
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(
                            Color.White,
                            Color.White.copy(alpha = 0.9f)
                        )
                    )
                ),
                color = Color.White
            )
            
            Spacer(Modifier.height(spacing * 0.5f))
            
            Text(
                "Sua experiência IPTV definitiva",
                style = when (deviceType) {
                    com.m3u.core.foundation.ui.components.AdaptiveDesign.DeviceType.PHONE -> MaterialTheme.typography.bodyMedium
                    com.m3u.core.foundation.ui.components.AdaptiveDesign.DeviceType.TABLET -> MaterialTheme.typography.bodyLarge
                    else -> MaterialTheme.typography.titleMedium
                },
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}
