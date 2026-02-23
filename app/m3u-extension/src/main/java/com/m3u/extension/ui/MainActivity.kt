package com.m3u.extension.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.m3u.extension.preferences.ExtensionPreferences
import com.m3u.extension.worker.LinkExtractionWorker
import com.m3u.extension.logic.YouTubeInteractor
import com.m3u.extension.util.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private val intentState = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intentState.value = intent
        val preferences = ExtensionPreferences(this)
        val interactor = YouTubeInteractor(applicationContext)

        setContent {
            // Premium Theme Definition
            val premiumColorScheme = darkColorScheme(
                primary = Color(0xFF00E5FF), // Cyber Cyan
                onPrimary = Color.Black,
                secondary = Color(0xFF1B1B3A), // Deep Navy
                tertiary = Color(0xFFFF00E5), // Neon Magenta
                background = Color(0xFF050505), // Rich Black
                surface = Color(0xFF121212),
                onSurface = Color.White,
                surfaceVariant = Color(0xFF1E1E1E),
                outline = Color(0xFF333333)
            )

            MaterialTheme(
                colorScheme = premiumColorScheme,
                typography = Typography().copy(
                    headlineMedium = Typography().headlineMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
                    titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.Bold),
                    bodyMedium = Typography().bodyMedium.copy(lineHeight = 20.sp)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val currentIntent = intentState.value
                    if (currentIntent?.action == Intent.ACTION_SEND && currentIntent.type == "text/plain") {
                         val sharedText = currentIntent.getStringExtra(Intent.EXTRA_TEXT)
                         if (sharedText != null) {
                             ExtensionExtractionScreen(sharedText, interactor)
                         } else {
                             DashboardScreen(preferences)
                         }
                    } else {
                        DashboardScreen(preferences)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intentState.value = intent
    }
}

@Composable
fun DashboardScreen(preferences: ExtensionPreferences) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // State
    val lastRunStatus by preferences.lastRunStatus.collectAsState(initial = "Carregando...")
    val lastRunTime by preferences.lastRunTimestamp.collectAsState(initial = 0L)
    val successCount by preferences.successCount.collectAsState(initial = 0)
    val failureCount by preferences.failureCount.collectAsState(initial = 0)
    val format by preferences.format.collectAsState(initial = ExtensionPreferences.DEFAULT_FORMAT)
    
    val successNames by preferences.successChannels.collectAsState(initial = emptyList())
    val failNames by preferences.failedChannels.collectAsState(initial = emptyList())
    
    var timeToNextRun by remember { mutableStateOf("Calculando...") }
    var showDetails by remember { mutableStateOf(false) }
    
    val hazeState = remember { dev.chrisbanes.haze.HazeState() }

    LaunchedEffect(Unit) {
        LogManager.info("Interface do Dashboard carregada", "UI")
    }
    
    LaunchedEffect(lastRunTime) {
        while(true) {
            if (lastRunTime == 0L) {
                timeToNextRun = "Aguardando primeira execução"
            } else {
                val nextRunMap = lastRunTime + TimeUnit.HOURS.toMillis(4)
                val diff = nextRunMap - System.currentTimeMillis()
                if (diff <= 0) {
                    timeToNextRun = "Sincronização pendente"
                } else {
                    val hours = TimeUnit.MILLISECONDS.toHours(diff)
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60
                    timeToNextRun = String.format("%02dh %01dm", hours, minutes)
                }
            }
            delay(1000)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val request = OneTimeWorkRequestBuilder<LinkExtractionWorker>().build()
                    WorkManager.getInstance(context).enqueue(request)
                    scope.launch { 
                        LogManager.info("Iniciando extração forçada via botão Dashboard")
                        preferences.updateStatus("Execução manual em curso...") 
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.Black,
                shape = CircleShape,
                modifier = Modifier.padding(16.dp).size(64.dp)
            ) {
                Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(32.dp))
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Premium Deep Gradient Background
            Box(modifier = Modifier
                .fillMaxSize()
                .haze(hazeState, style = dev.chrisbanes.haze.HazeDefaults.style(blurRadius = 8.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF000B18), Color(0xFF001F3F), Color(0xFF003566))
                    )
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Spacer(Modifier.height(40.dp))

                // Top Bar - Estilo Pro
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "EXTRATOR PRO",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp
                            ),
                            color = Color(0xFF00E5FF)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(Color(0xFF00FF88), CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "ENGINE V2.1 ACTIVE",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF00FF88),
                                letterSpacing = 1.sp
                            )
                        }
                    }
                    
                    Surface(
                        color = Color.White.copy(alpha = 0.1f),
                        shape = CircleShape,
                        onClick = { /* Settings */ }
                    ) {
                        Icon(
                            Icons.Default.Tune, 
                            contentDescription = null, 
                            modifier = Modifier.padding(12.dp).size(20.dp),
                            tint = Color.White
                        )
                    }
                }

                // Main Status Card with Glass Effect
                StatusCardPremium(
                    status = lastRunStatus,
                    nextRun = timeToNextRun,
                    hazeState = hazeState
                )

                // Compact Stats & Logs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatsCardPremium(
                        title = "OK",
                        value = successCount.toString(),
                        icon = Icons.Filled.Verified,
                        color = Color(0xFF00FF88),
                        hazeState = hazeState,
                        modifier = Modifier.weight(1f)
                    )
                    StatsCardPremium(
                        title = "FAIL",
                        value = failureCount.toString(),
                        icon = Icons.Filled.ErrorOutline,
                        color = Color(0xFFFF3D00),
                        hazeState = hazeState,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Botão para ver relatório detalhado
                var showExtractionReport by remember { mutableStateOf(false) }
                var reportContent by remember { mutableStateOf("") }
                
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val extractor = com.m3u.extension.youtube.YouTubeExtractorV2(context)
                            reportContent = extractor.generateReport()
                            showExtractionReport = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF00E5FF)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Analytics, null)
                    Spacer(Modifier.width(12.dp))
                    Text("RELATÓRIO DE EXTRAÇÃO", fontWeight = FontWeight.Bold)
                }

                if (showExtractionReport) {
                    AlertDialog(
                        onDismissRequest = { showExtractionReport = false },
                        title = { Text("Relatório de Extração", color = Color(0xFF00E5FF)) },
                        text = {
                            Box(modifier = Modifier.heightIn(max = 400.dp)) {
                                 val scroll = rememberScrollState()
                                 Text(
                                     text = reportContent,
                                     modifier = Modifier
                                         .verticalScroll(scroll)
                                         .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                         .padding(12.dp),
                                     style = MaterialTheme.typography.bodySmall.copy(
                                         fontFamily = FontFamily.Monospace,
                                         fontSize = 11.sp,
                                         lineHeight = 16.sp
                                     ),
                                     color = Color(0xFF00FF88).copy(alpha = 0.9f)
                                 )
                             }
                        },
                        confirmButton = {
                            Button(
                                onClick = { showExtractionReport = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("COMPREENDIDO", fontWeight = FontWeight.Black)
                            }
                        },
                        containerColor = Color(0xFF0A0A0A),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f), RoundedCornerShape(28.dp))
                    )
                }

                // Config Details
                ConfigurationCardPremium(
                    currentFormat = format,
                    hazeState = hazeState,
                    onFormatChange = { scope.launch { preferences.setFormat(it) } }
                )

                // Recent Channels Preview (Feedback Fluid)
                if (successNames.isNotEmpty() || failNames.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(16.dp)
                    ) {
                        Text(
                            "PROCESSAMENTOS RECENTES", 
                            style = MaterialTheme.typography.labelSmall, 
                            color = Color(0xFF00FF88),
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        
                        val combined = (successNames.take(3) + failNames.take(3)).take(5)
                        combined.forEach { channel ->
                             val isSuccess = successNames.contains(channel)
                             ChannelResultItemPremium(channel, isSuccess)
                        }
                        
                        if (successNames.size + failNames.size > 5) {
                            Text(
                                "+ ${successNames.size + failNames.size - 5} canais ocultos",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(100.dp))
            }
        }
    }
}

@Composable
fun StatusCardPremium(status: String, nextRun: String, hazeState: dev.chrisbanes.haze.HazeState) {
    val isError = status.contains("Erro", ignoreCase = true) || status.contains("Falha", ignoreCase = true)
    val isRunning = status.contains("curso", ignoreCase = true) || status.contains("Sincronizando", ignoreCase = true) || status.contains("execução", ignoreCase = true)
    
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(32.dp))
            .hazeChild(state = hazeState, shape = RoundedCornerShape(32.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(32.dp))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isRunning) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha * 0.1f), CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha * 0.5f), CircleShape)
                    )
                }
                Icon(
                    imageVector = when {
                        isError -> Icons.Filled.Error
                        isRunning -> Icons.Filled.SettingsInputAntenna
                        else -> Icons.Filled.Security
                    },
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = when {
                        isError -> Color(0xFFFF3D00)
                        isRunning -> MaterialTheme.colorScheme.primary
                        else -> Color(0xFF00FF88)
                    }
                )
            }
            
            Spacer(Modifier.height(16.dp))
            
            Text(
                text = status.uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                ),
                textAlign = TextAlign.Center,
                color = if (isError) Color(0xFFFF3D00) else Color.White
            )
            
            Spacer(Modifier.height(12.dp))
            
            Surface(
                color = Color.Black.copy(alpha = 0.3f),
                shape = CircleShape
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Update, null, modifier = Modifier.size(12.dp), tint = Color.Gray)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "NEXT AUTO-RUN: $nextRun",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun StatsCardPremium(title: String, value: String, icon: ImageVector, color: Color, hazeState: dev.chrisbanes.haze.HazeState, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(130.dp)
            .clip(RoundedCornerShape(24.dp))
            .hazeChild(state = hazeState, shape = RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Column {
                Text(
                    text = value, 
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black), 
                    color = Color.White
                )
                Text(
                    text = title, 
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp), 
                    color = Color.Gray, 
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}



@Composable
fun ConfigurationCardPremium(currentFormat: String, hazeState: dev.chrisbanes.haze.HazeState, onFormatChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .hazeChild(state = hazeState, shape = RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Tune, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(
                        "CONFIGURAÇÕES", 
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, 
                        null, 
                        tint = Color.Gray
                    )
                }
            }
            
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 16.dp)) {
                    Text(
                        "FORMATO DE EXTRAÇÃO (YT-DLP)", 
                        style = MaterialTheme.typography.labelSmall, 
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = currentFormat,
                        onValueChange = onFormatChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedContainerColor = Color.Black.copy(alpha = 0.4f),
                            unfocusedContainerColor = Color.Black.copy(alpha = 0.2f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ExtensionExtractionScreen(url: String, interactor: YouTubeInteractor) {
    var status by remember { mutableStateOf("Analisando Protocolo...") }
    val context = LocalContext.current

    LaunchedEffect(url) {
        LogManager.info("Requisição externa recebida: $url")
        val result = withContext(Dispatchers.IO) { interactor.resolve(url) }
        result.onSuccess { streamUrl ->
            status = "AUTENTICAÇÃO OK. ABRINDO PLAYER..."
            LogManager.info("Link resolvido com sucesso via Intent externo")
            delay(800)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, streamUrl)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(shareIntent, "Abrir com M3U Player"))
            (context as? ComponentActivity)?.finish()
        }
        result.onFailure {
            LogManager.error("Falha na resolução externa: ${it.message}")
            status = "ERRO CRÍTICO: ${it.message}"
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (status.startsWith("ERRO")) {
            Icon(Icons.Filled.Report, null, tint = Color.Red, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(24.dp))
            Text(status, color = Color.Red, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp), fontWeight = FontWeight.Bold)
        } else {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(24.dp))
            Text(status, color = Color.White, letterSpacing = 2.sp, fontWeight = FontWeight.Light)
        }
    }
}

@Composable
fun ChannelResultItemPremium(name: String, isSuccess: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isSuccess) Icons.Default.Circle else Icons.Default.RemoveCircleOutline,
            contentDescription = null,
            tint = if (isSuccess) Color(0xFF00FF88) else Color(0xFFFF3D00).copy(alpha = 0.5f),
            modifier = Modifier.size(10.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = name.uppercase(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isSuccess) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSuccess) Color.White else Color.Gray
        )
    }
}
