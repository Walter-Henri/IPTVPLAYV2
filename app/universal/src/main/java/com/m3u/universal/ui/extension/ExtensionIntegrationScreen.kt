package com.m3u.universal.ui.extension

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.foundation.ui.PremiumColors
import java.text.SimpleDateFormat
import java.util.*

/**
 * ExtensionIntegrationScreen v2
 *
 * Premium UI for the extension integration hub, fully compatible with
 * the multi-strategy v6 extraction engine.
 *
 * Features:
 * - Live extraction status with animated progress
 * - Token status badge (shows whether the engine has PO Token / Cookies)
 * - Extracted links list with play / add-to-playlist / remove actions
 * - Dropbox sync button with animated state
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionIntegrationScreen(
    onPlay: (String) -> Unit = {},
    viewModel: ExtensionIntegrationViewModel = hiltViewModel()
) {
    val extractedLinks by viewModel.extractedLinks.collectAsStateWithLifecycle()
    val status         by viewModel.status.collectAsStateWithLifecycle()
    val engineStatus   by viewModel.engineStatus.collectAsStateWithLifecycle()

    var urlInput   by remember { mutableStateOf("") }
    var titleInput by remember { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Pulsing engine indicator
                        EngineStatusDot(engineStatus)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Motor de Extração",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = engineStatus.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = engineStatus.labelColor,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.syncFromDropbox() }) {
                        Icon(Icons.Default.Sync, contentDescription = "Sincronizar")
                    }
                    if (extractedLinks.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearAllLinks() }) {
                            Icon(Icons.Default.ClearAll, contentDescription = "Limpar")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // ── Status Banner ──────────────────────────────────────────
            item {
                AnimatedVisibility(
                    visible = status !is ExtensionIntegrationViewModel.ExtractionStatus.Idle,
                    enter  = expandVertically() + fadeIn(),
                    exit   = shrinkVertically() + fadeOut()
                ) {
                    StatusBanner(status)
                }
            }

            // ── Engine Stats Card ──────────────────────────────────────
            item {
                EngineStatsCard(engineStatus)
            }

            // ── URL Input ─────────────────────────────────────────────
            item {
                ExtractionInputCard(
                    urlInput   = urlInput,
                    titleInput = titleInput,
                    onUrlChange   = { urlInput = it },
                    onTitleChange = { titleInput = it },
                    isLoading  = status is ExtensionIntegrationViewModel.ExtractionStatus.Extracting,
                    onExtract  = {
                        if (urlInput.isNotBlank()) {
                            viewModel.extractLink(urlInput, titleInput)
                            urlInput   = ""
                            titleInput = ""
                        }
                    }
                )
            }

            // ── Links List ────────────────────────────────────────────
            if (extractedLinks.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Links Extraídos",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(8.dp))
                        Badge(containerColor = PremiumColors.Accent) {
                            Text("${extractedLinks.size}", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                items(extractedLinks, key = { it.timestamp }) { link ->
                    ExtractedLinkCard(
                        link          = link,
                        onPlay        = { viewModel.playLink(link, onPlay) },
                        onAddPlaylist = { viewModel.addAsVirtualChannel(link) },
                        onRemove      = { viewModel.removeLink(link) }
                    )
                }
            } else if (status is ExtensionIntegrationViewModel.ExtractionStatus.Idle) {
                item { EmptyLinksState() }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// ENGINE STATUS DOT — pulsing indicator
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun EngineStatusDot(status: ExtensionIntegrationViewModel.EngineStatus) {
    val pulse = rememberInfiniteTransition(label = "dot_pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.8f,
        targetValue  = 1.2f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "dot_scale"
    )

    Box(
        modifier = Modifier
            .size(10.dp)
            .scale(if (status.isActive) scale else 1f)
            .clip(CircleShape)
            .background(status.dotColor)
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// ENGINE STATS CARD
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun EngineStatsCard(status: ExtensionIntegrationViewModel.EngineStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Motor v6 — Multi-Strategy",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StrategyChip("WebView HLS",    true,  Icons.Default.Wifi,    Modifier.weight(1f))
                StrategyChip("web+PO Token",   status.hasPoToken,  Icons.Default.VpnKey, Modifier.weight(1f))
                StrategyChip("tv_embedded",    true,  Icons.Default.Tv,      Modifier.weight(1f))
                StrategyChip("yt-dlp iOS",     true,  Icons.Default.PhoneIphone, Modifier.weight(1f))
            }
            if (status.hasCookies || status.hasPoToken) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    TokenBadge("Cookies", status.hasCookies)
                    TokenBadge("PO Token", status.hasPoToken)
                    TokenBadge("Visitor Data", status.hasVisitorData)
                }
            }
        }
    }
}

@Composable
private fun StrategyChip(label: String, active: Boolean, icon: ImageVector, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (active) PremiumColors.Accent.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surface
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (active) PremiumColors.Accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) PremiumColors.Accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            fontSize = 8.sp
        )
    }
}

@Composable
private fun TokenBadge(label: String, present: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            imageVector = if (present) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (present) Color(0xFF34C759) else MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// STATUS BANNER
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatusBanner(status: ExtensionIntegrationViewModel.ExtractionStatus) {
    val (bg, tc, icon, text) = when (status) {
        is ExtensionIntegrationViewModel.ExtractionStatus.Extracting ->
            Quad(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, null as ImageVector?, status.url)
        is ExtensionIntegrationViewModel.ExtractionStatus.Success ->
            Quad(Color(0xFF1A3A2A), Color(0xFF34C759), Icons.Default.CheckCircle as ImageVector?, "Extraído: ${status.resolvedUrl.take(50)}...")
        is ExtensionIntegrationViewModel.ExtractionStatus.Error ->
            Quad(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, Icons.Default.Error as ImageVector?, status.message)
        is ExtensionIntegrationViewModel.ExtractionStatus.AddedToPlaylist ->
            Quad(Color(0xFF1A2D3A), Color(0xFF0A84FF), Icons.Default.PlaylistAddCheck as ImageVector?, "Adicionado: ${status.title}")
        else -> return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (status is ExtensionIntegrationViewModel.ExtractionStatus.Extracting) {
            CircularProgressIndicator(
                modifier    = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color       = tc
            )
        } else {
            icon?.let {
                Icon(it, contentDescription = null, tint = tc, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text     = text,
            style    = MaterialTheme.typography.bodySmall,
            color    = tc,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}


// ──────────────────────────────────────────────────────────────────────────────
// EXTRACTION INPUT
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun ExtractionInputCard(
    urlInput:    String,
    titleInput:  String,
    onUrlChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    isLoading:   Boolean,
    onExtract:   () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Extrair Stream",
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value       = urlInput,
                onValueChange = onUrlChange,
                label       = { Text("URL do vídeo / canal") },
                placeholder = { Text("youtube.com/watch?v=... ou URL de stream") },
                modifier    = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                trailingIcon = {
                    if (urlInput.isNotEmpty()) {
                        IconButton(onClick = { onUrlChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpar")
                        }
                    }
                },
                singleLine = true,
                shape      = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value       = titleInput,
                onValueChange = onTitleChange,
                label       = { Text("Título (opcional)") },
                modifier    = Modifier.fillMaxWidth(),
                singleLine  = true,
                shape       = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick  = onExtract,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled  = urlInput.isNotBlank() && !isLoading,
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = PremiumColors.Accent)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        strokeWidth  = 2.dp,
                        color       = Color.White
                    )
                } else {
                    Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Extrair HLS", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// EXTRACTED LINK CARD
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun ExtractedLinkCard(
    link:          ExtensionIntegrationViewModel.ExtractedLink,
    onPlay:        () -> Unit,
    onAddPlaylist: () -> Unit,
    onRemove:      () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .animateContentSize(),
        shape  = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play icon accent bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(PremiumColors.Accent)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = link.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = link.resolvedUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccessTime, contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    Text(
                        text = formatTimestamp(link.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    if (link.extractionMethod.isNotBlank()) {
                        Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            style = MaterialTheme.typography.labelSmall)
                        Text(
                            text  = link.extractionMethod,
                            style = MaterialTheme.typography.labelSmall,
                            color = PremiumColors.Accent.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            // Action buttons
            Column {
                IconButton(onClick = onPlay, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Reproduzir",
                        tint = PremiumColors.Accent, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = onAddPlaylist, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Adicionar à Playlist",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Remover",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// EMPTY STATE
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyLinksState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val pulse = rememberInfiniteTransition(label = "empty_pulse")
        val alpha by pulse.animateFloat(0.3f, 0.8f,
            infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "empty_alpha")

        Icon(
            Icons.Default.Bolt,
            contentDescription = null,
            modifier = Modifier.size(64.dp).alpha(alpha),
            tint = PremiumColors.Accent
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Motor Pronto",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Cole a URL de um canal YouTube ou stream acima\npara extrair o link HLS direto",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// HELPERS
// ──────────────────────────────────────────────────────────────────────────────

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private operator fun <A, B, C, D> Quad<A, B, C, D>.component1() = first
private operator fun <A, B, C, D> Quad<A, B, C, D>.component2() = second
private operator fun <A, B, C, D> Quad<A, B, C, D>.component3() = third
private operator fun <A, B, C, D> Quad<A, B, C, D>.component4() = fourth

private fun formatTimestamp(ts: Long): String =
    SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(ts))
