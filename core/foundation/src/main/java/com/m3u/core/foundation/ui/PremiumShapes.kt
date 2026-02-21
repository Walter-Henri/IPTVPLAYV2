package com.m3u.core.foundation.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Formas premium para componentes do M3Uplay-Manus
 * Bordas arredondadas modernas para um visual sofisticado
 */
val PremiumShapes = Shapes(
    // Extra small - Para chips e badges pequenos
    extraSmall = RoundedCornerShape(4.dp),
    
    // Small - Para botões pequenos e cards compactos
    small = RoundedCornerShape(8.dp),
    
    // Medium - Para cards padrão e botões
    medium = RoundedCornerShape(12.dp),
    
    // Large - Para dialogs e bottom sheets
    large = RoundedCornerShape(16.dp),
    
    // Extra large - Para modals e telas completas
    extraLarge = RoundedCornerShape(24.dp)
)

/**
 * Formas customizadas para componentes específicos
 */
object CustomShapes {
    // Para cards de canal com visual premium
    val ChannelCard = RoundedCornerShape(16.dp)
    
    // Para player controls com cantos arredondados
    val PlayerControls = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = 0.dp,
        bottomEnd = 0.dp
    )
    
    // Para bottom navigation bar
    val BottomNavigation = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = 0.dp,
        bottomEnd = 0.dp
    )
    
    // Para search bar flutuante
    val SearchBar = RoundedCornerShape(28.dp)
    
    // Para chips de categoria
    val CategoryChip = RoundedCornerShape(20.dp)
    
    // Para botões FAB (Floating Action Button)
    val FAB = RoundedCornerShape(16.dp)
    
    // Para cards de playlist
    val PlaylistCard = RoundedCornerShape(12.dp)
    
    // Para dialogs premium
    val Dialog = RoundedCornerShape(24.dp)
    
    // Para bottom sheets
    val BottomSheet = RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomStart = 0.dp,
        bottomEnd = 0.dp
    )
    
    // Para imagens de thumbnail
    val Thumbnail = RoundedCornerShape(8.dp)
    
    // Para progress indicators
    val ProgressIndicator = RoundedCornerShape(8.dp)
}
