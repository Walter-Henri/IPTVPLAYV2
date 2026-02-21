package com.m3u.core.foundation.ui

import androidx.compose.ui.graphics.Color

/**
 * Paleta de cores premium ultra-elegante para o M3U Play
 * Design sofisticado baseado em tons neutros profundos com acentos vibrantes
 * 
 * Filosofia de Design:
 * - Minimalismo Luxuoso: Preto profundo, cinzas sofisticados, brancos puros
 * - Acentos Vibrantes: Azul elétrico e dourado sutil para hierarquia visual
 * - Contraste Perfeito: Garantindo acessibilidade WCAG AAA
 */
object PremiumColors {
    
    // ═══════════════════════════════════════════════════════════════════
    // CORES PRIMÁRIAS - Elegância Minimalista
    // ═══════════════════════════════════════════════════════════════════
    
    // Modo Claro: Preto profundo com toque de azul
    val PrimaryLight = Color(0xFF0A0E27)          // Preto azulado profundo
    val PrimaryDark = Color(0xFFF8F9FA)           // Branco quase puro
    val PrimaryVariantLight = Color(0xFF1A1F3A)   // Azul marinho escuro
    val PrimaryVariantDark = Color(0xFFE8EAED)    // Cinza clarissimo
    
    // ═══════════════════════════════════════════════════════════════════
    // CORES DE DESTAQUE - Vibrância Controlada
    // ═══════════════════════════════════════════════════════════════════
    
    val Accent = Color(0xFF0066FF)                // Azul elétrico vibrante
    val AccentVariant = Color(0xFF0052CC)         // Azul royal profundo
    val AccentGold = Color(0xFFFFB800)            // Dourado premium
    val AccentGoldVariant = Color(0xFFFF9500)     // Âmbar quente
    
    // ═══════════════════════════════════════════════════════════════════
    // CORES SECUNDÁRIAS - Neutralidade Sofisticada
    // ═══════════════════════════════════════════════════════════════════
    
    val SecondaryLight = Color(0xFF3C4043)        // Cinza carvão
    val SecondaryDark = Color(0xFFBDC1C6)         // Cinza prata
    val SecondaryVariantLight = Color(0xFF202124) // Quase preto
    val SecondaryVariantDark = Color(0xFFE8EAED)  // Cinza névoa
    
    // ═══════════════════════════════════════════════════════════════════
    // BACKGROUNDS - Profundidade e Hierarquia
    // ═══════════════════════════════════════════════════════════════════
    
    val BackgroundLight = Color(0xFFFAFBFC)       // Branco neve
    val BackgroundDark = Color(0xFF000000)        // Preto absoluto (OLED)
    val SurfaceLight = Color(0xFFFFFFFF)          // Branco puro
    val SurfaceDark = Color(0xFF0D0D0D)           // Preto suave
    
    // ═══════════════════════════════════════════════════════════════════
    // SURFACES ELEVADAS - Glassmorphism e Profundidade
    // ═══════════════════════════════════════════════════════════════════
    
    val SurfaceElevated1Light = Color(0xFFFFFFFF) // Branco puro
    val SurfaceElevated1Dark = Color(0xFF1A1A1A)  // Cinza grafite
    val SurfaceElevated2Light = Color(0xFFF5F7FA) // Cinza gelo
    val SurfaceElevated2Dark = Color(0xFF242424)  // Cinza chumbo
    val SurfaceElevated3Dark = Color(0xFF2E2E2E)  // Cinza ardósia
    
    // Glassmorphism - Superfícies translúcidas
    val GlassLight = Color(0xCCFFFFFF)            // Vidro branco
    val GlassDark = Color(0xCC1A1A1A)             // Vidro escuro
    
    // ═══════════════════════════════════════════════════════════════════
    // CORES DE STATUS - Comunicação Visual Clara
    // ═══════════════════════════════════════════════════════════════════
    
    val ErrorLight = Color(0xFFDC2626)            // Vermelho vibrante
    val ErrorDark = Color(0xFFEF4444)             // Vermelho coral
    val SuccessLight = Color(0xFF059669)          // Verde esmeralda
    val SuccessDark = Color(0xFF10B981)           // Verde menta
    val InfoLight = Color(0xFF0284C7)             // Azul céu
    val InfoDark = Color(0xFF0EA5E9)              // Azul turquesa
    val WarningLight = Color(0xFFEA580C)          // Laranja fogo
    val WarningDark = Color(0xFFF97316)           // Laranja tangerina
    
    // ═══════════════════════════════════════════════════════════════════
    // GRADIENTES - Profundidade e Modernidade
    // ═══════════════════════════════════════════════════════════════════
    
    // Gradiente Principal (Azul profundo → Preto)
    val GradientStart = Color(0xFF0A0E27)
    val GradientMiddle = Color(0xFF1A1F3A)
    val GradientEnd = Color(0xFF000000)
    
    // Gradiente de Acento (Azul elétrico → Roxo)
    val GradientAccentStart = Color(0xFF0066FF)
    val GradientAccentEnd = Color(0xFF8B5CF6)
    
    // Gradiente Dourado (Premium)
    val GradientGoldStart = Color(0xFFFFB800)
    val GradientGoldEnd = Color(0xFFFF6B00)
    
    // ═══════════════════════════════════════════════════════════════════
    // CORES DE TEXTO - Legibilidade Otimizada
    // ═══════════════════════════════════════════════════════════════════
    
    val OnPrimaryLight = Color(0xFFFFFFFF)        // Branco puro
    val OnPrimaryDark = Color(0xFF0A0E27)         // Preto azulado
    val OnSecondaryLight = Color(0xFFFFFFFF)      // Branco puro
    val OnSecondaryDark = Color(0xFF000000)       // Preto absoluto
    val OnBackgroundLight = Color(0xFF0A0E27)     // Preto azulado
    val OnBackgroundDark = Color(0xFFF8F9FA)      // Branco quase puro
    val OnSurfaceLight = Color(0xFF0A0E27)        // Preto azulado
    val OnSurfaceDark = Color(0xFFF8F9FA)         // Branco quase puro
    val OnSurfaceVariantLight = Color(0xFF5F6368) // Cinza médio
    val OnSurfaceVariantDark = Color(0xFF9AA0A6)  // Cinza claro
    
    // ═══════════════════════════════════════════════════════════════════
    // OUTLINES - Bordas e Divisores Sutis
    // ═══════════════════════════════════════════════════════════════════
    
    val OutlineLight = Color(0xFFE0E3E8)          // Cinza pérola
    val OutlineDark = Color(0xFF3C4043)           // Cinza carvão
    val OutlineVariantLight = Color(0xFFF1F3F4)   // Cinza névoa clara
    val OutlineVariantDark = Color(0xFF202124)    // Quase preto
    
    // ═══════════════════════════════════════════════════════════════════
    // SCRIM - Overlays e Modais
    // ═══════════════════════════════════════════════════════════════════
    
    val ScrimLight = Color(0x66000000)            // Preto 40% opacidade
    val ScrimDark = Color(0xB3000000)             // Preto 70% opacidade
    val ScrimHeavy = Color(0xE6000000)            // Preto 90% opacidade
    
    // ═══════════════════════════════════════════════════════════════════
    // CORES ESPECIAIS - Efeitos e Destaques
    // ═══════════════════════════════════════════════════════════════════
    
    // Shimmer effect (para loading states)
    val ShimmerLight = Color(0xFFE8EAED)
    val ShimmerDark = Color(0xFF242424)
    
    // Ripple effect
    val RippleLight = Color(0x1F000000)           // Preto 12% opacidade
    val RippleDark = Color(0x1FFFFFFF)            // Branco 12% opacidade
    
    // Focus indicators
    val FocusLight = Accent
    val FocusDark = Accent
}
