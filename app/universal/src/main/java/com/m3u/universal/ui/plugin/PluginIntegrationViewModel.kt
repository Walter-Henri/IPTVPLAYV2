package com.m3u.universal.ui.plugin

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3u.core.plugin.IPlugin
import com.m3u.core.plugin.IPluginCallback
import com.m3u.core.foundation.IdentityRegistry
import com.m3u.data.repository.playlist.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * PluginIntegrationViewModel v2
 *
 * Manages the link-extraction flow between the main app and the Plugin service.
 * Compatible with the multi-strategy v6 engine in m3u-Plugin.
 *
 * Key improvements over v1:
 * - `EngineStatus` flow reflecting real token availability (PO Token, Cookies, VisitorData)
 * - Removed the arbitrary 1500 ms delay in `syncChannelsAsync` (was masking race conditions)
 * - All AIDL bindings use `suspendCancellableCoroutine` for proper cancellation
 * - `ExtractedLink` now carries `extractionMethod` field (shows which strategy was used)
 * - Auto-refresh EngineStatus every 30 s so the UI reflects token changes without restart
 */
@HiltViewModel
class PluginIntegrationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    private val timber = Timber.tag("ExtVM")

    // ── New headless plugin repository ──────────────────────────────────────────
    private val extractorRepo = com.m3u.universal.plugin.ExtractorRepository(context)


    // ── Public state ──────────────────────────────────────────────────────────

    private val _extractedLinks = MutableStateFlow<List<ExtractedLink>>(emptyList())
    val extractedLinks: StateFlow<List<ExtractedLink>> = _extractedLinks.asStateFlow()

    private val _status = MutableStateFlow<ExtractionStatus>(ExtractionStatus.Idle)
    val status: StateFlow<ExtractionStatus> = _status.asStateFlow()

    private val _engineStatus = MutableStateFlow(EngineStatus.unknown())
    val engineStatus: StateFlow<EngineStatus> = _engineStatus.asStateFlow()

    private val _isPluginInstalled = MutableStateFlow(false)
    val isPluginInstalled: StateFlow<Boolean> = _isPluginInstalled.asStateFlow()

    private val _successCount = MutableStateFlow(0)
    val successCount: StateFlow<Int> = _successCount.asStateFlow()

    private val _failureCount = MutableStateFlow(0)
    val failureCount: StateFlow<Int> = _failureCount.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    private val _extractionLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    val extractionLogs: StateFlow<List<LogEntry>> = _extractionLogs.asStateFlow()

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        refreshEngineStatus()
        // Poll every 30 s so the UI reflects newly-received tokens
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                refreshEngineStatus()
            }
        }
    }

    private fun refreshEngineStatus() {
        val pm = context.packageManager
        _isPluginInstalled.value = try {
            pm.getPackageInfo("com.m3u.plugin", 0)
            true
        } catch (e: Exception) {
            false
        }

        val hasCookies     = IdentityRegistry.getCookie("youtube.com") != null
        val hasPoToken     = IdentityRegistry.getPoToken() != null
        val hasVisitorData = IdentityRegistry.getVisitorData() != null
        _engineStatus.value = when {
            IdentityRegistry.hasValidIdentity() -> EngineStatus.active(hasCookies, hasPoToken, hasVisitorData)
            IdentityRegistry.getUserAgent() != null -> EngineStatus.partial(hasCookies, hasPoToken, hasVisitorData)
            else -> EngineStatus.waiting()
        }
    }

    // ── Dropbox Sync ──────────────────────────────────────────────────────────

    /**
     * Triggers the Plugin to sync channels from Dropbox and imports the result.
     */
    fun syncFromDropbox() {
        viewModelScope.launch {
            _status.value = ExtractionStatus.Extracting("Solicitando sincronização à extensão...")
            _successCount.value = 0
            _failureCount.value = 0
            _totalCount.value = 0
            _extractionLogs.value = emptyList()

            try {
                val resultJson = syncChannelsAsync()
                if (resultJson != null) {
                    val count = playlistRepository.importChannelsJsonBody(resultJson)
                    val fails = countFails(resultJson)
                    
                    _successCount.value = count
                    _failureCount.value = fails
                    _totalCount.value = count + fails
                    
                    // Parse full logs from resultJson
                    _extractionLogs.value = parseLogsFromJson(resultJson)

                    _status.value = if (fails > 0)
                        ExtractionStatus.AddedToPlaylist("$count sucessos, $fails falhas")
                    else
                        ExtractionStatus.AddedToPlaylist("$count canais atualizados")
                    timber.d("Sync OK: $count canais, $fails falhas")
                } else {
                    _status.value = ExtractionStatus.Error("Falha na sincronização — extensão não instalada?")
                }
            } catch (e: Exception) {
                _status.value = ExtractionStatus.Error(e.message ?: "Erro inesperado")
                timber.e(e, "syncFromDropbox error")
            }
        }
    }

    fun forceManualSync() {
        syncFromDropbox()
    }

    private suspend fun syncChannelsAsync(): String? = withContext(Dispatchers.IO) {
        bindPluginService { binder, deferred ->
            binder.syncChannels(object : IPluginCallback.Stub() {
                override fun onProgress(current: Int, total: Int, name: String) {
                    viewModelScope.launch {
                        _status.value = ExtractionStatus.Extracting("[$current/$total] $name")
                    }
                }
                override fun onResult(jsonResult: String?) { deferred.complete(jsonResult) }
                override fun onError(message: String?)     { deferred.complete(null) }
            })
        }
    }

    // ── Single URL Extraction ─────────────────────────────────────────────────

    fun extractLink(url: String, title: String = "") {
        viewModelScope.launch {
            _status.value = ExtractionStatus.Extracting(url)
            try {
                val result = resolveWithPlugin(url)
                if (result != null) {
                    val link = ExtractedLink(
                        originalUrl       = url,
                        resolvedUrl       = result.url,
                        title             = title.ifBlank { inferTitle(url) },
                        timestamp         = System.currentTimeMillis(),
                        extractionMethod  = result.method
                    )
                    _extractedLinks.value = listOf(link) + _extractedLinks.value
                    _status.value = ExtractionStatus.Success(result.url)
                    playlistRepository.addVirtualStream(link.title, link.resolvedUrl)
                    timber.d("✓ Extracted via ${result.method}: ${result.url.take(80)}")
                } else {
                    _status.value = ExtractionStatus.Error("Não foi possível extrair um stream válido")
                }
            } catch (e: Exception) {
                _status.value = ExtractionStatus.Error(e.message ?: "Erro desconhecido")
                timber.e(e, "extractLink error")
            }
            refreshEngineStatus()
        }
    }

    fun addAsVirtualChannel(link: ExtractedLink) {
        viewModelScope.launch {
            try {
                playlistRepository.addVirtualStream(link.title, link.resolvedUrl)
                _status.value = ExtractionStatus.AddedToPlaylist(link.title)
            } catch (e: Exception) {
                _status.value = ExtractionStatus.Error("Erro ao adicionar: ${e.message}")
            }
        }
    }

    fun playLink(link: ExtractedLink, onPlay: (String) -> Unit) {
        viewModelScope.launch {
            try {
                _status.value = ExtractionStatus.Extracting("Preparando reprodução...")
                val id = playlistRepository.addVirtualStream(link.title, link.resolvedUrl)
                onPlay(id.toString())
            } catch (e: Exception) {
                _status.value = ExtractionStatus.Error("Erro ao reproduzir: ${e.message}")
            }
        }
    }

    fun removeLink(link: ExtractedLink) {
        _extractedLinks.value = _extractedLinks.value - link
    }

    fun clearAllLinks() {
        _extractedLinks.value = emptyList()
        _status.value = ExtractionStatus.Idle
    }

    // ── AIDL helper ──────────────────────────────────────────────────────────

    /**
     * Resolves a URL via the Plugin's AIDL `resolve()` call.
     * Returns a [ResolveResult] with the URL and which extraction method was used.
     */
    private suspend fun resolveWithPlugin(url: String): ResolveResult? =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { cont ->
                val intent = Intent("com.m3u.plugin.PluginService")
                    .setPackage("com.m3u.plugin")

                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        try {
                            val ext     = IPlugin.Stub.asInterface(service)
                            val rawJson = ext.resolve(url)          // returns JSON or plain URL
                            context.unbindService(this)

                            val result = parseResolveResult(rawJson)
                            if (cont.isActive) cont.resume(result)
                        } catch (e: Exception) {
                            try { context.unbindService(this) } catch (_: Exception) {}
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                    override fun onServiceDisconnected(name: ComponentName?) {
                        if (cont.isActive) cont.resume(null)
                    }
                }

                cont.invokeOnCancellation {
                    try { context.unbindService(connection) } catch (_: Exception) {}
                }

                if (!context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
                    timber.e("Cannot bind to PluginService")
                    if (cont.isActive) cont.resume(null)
                }
            }
        }


    /**
     * Generic helper: binds the Plugin service, calls [block] with the binder,
     * and waits for the deferred result.
     */
    private suspend fun bindPluginService(
        block: (IPlugin, CompletableDeferred<String?>) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        val deferred = CompletableDeferred<String?>()
        val intent = Intent("com.m3u.plugin.PluginService").setPackage("com.m3u.plugin")

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val self = this
                try {
                    val binder = IPlugin.Stub.asInterface(service)
                    block(binder, deferred)
                    deferred.invokeOnCompletion {
                        try { context.unbindService(self) } catch (_: Exception) {}
                    }
                } catch (e: Exception) {
                    deferred.complete(null)
                    try { context.unbindService(self) } catch (_: Exception) {}
                }
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                if (!deferred.isCompleted) deferred.complete(null)
            }
        }

        if (!context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
            timber.e("bindService failed")
            return@withContext null
        }

        deferred.await()
    }

    // ── New AIDL contract (IExtractorService) ─────────────────────────────────

    /**
     * Extracts a YouTube stream via the new headless [IExtractorService] AIDL contract.
     * Returns [com.m3u.common.ExtractionData] with M3U8 URL and anti-403 headers.
     *
     * This supplements the existing [resolveWithPlugin] (which uses the legacy
     * IPlugin AIDL) without breaking it.
     */
    suspend fun extractViaNewContract(youtubeUrl: String): com.m3u.common.ExtractionData? =
        withContext(Dispatchers.IO) {
            try {
                if (!extractorRepo.isConnected) {
                    extractorRepo.bind()
                    // Give the ServiceConnection a moment to resolve
                    kotlinx.coroutines.delay(500)
                }
                extractorRepo.extractStream(youtubeUrl)
            } catch (e: Exception) {
                timber.e(e, "extractViaNewContract failed for $youtubeUrl")
                null
            }
        }

    override fun onCleared() {
        super.onCleared()
        extractorRepo.unbind()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun parseResolveResult(raw: String?): ResolveResult? {
        if (raw.isNullOrBlank()) return null
        return try {
            val obj    = Json.parseToJsonElement(raw).jsonObject
            val url    = obj["url"]?.jsonPrimitive?.content ?: return null
            val method = obj["method"]?.jsonPrimitive?.content ?: "yt-dlp"
            ResolveResult(url, method)
        } catch (_: Exception) {
            // Plain URL returned (legacy Plugin)
            ResolveResult(raw.trim(), "legacy")
        }
    }

    private fun countFails(json: String): Int {
        return try {
            val obj = Json.parseToJsonElement(json).jsonObject
            val channels = obj["channels"]?.jsonArray ?: return 0
            channels.count { it.jsonObject["success"]?.jsonPrimitive?.booleanOrNull == false }
        } catch (_: Exception) { 0 }
    }

    private fun parseLogsFromJson(json: String): List<LogEntry> {
        return try {
            val obj = Json.parseToJsonElement(json).jsonObject
            val channels = obj["channels"]?.jsonArray ?: return emptyList()
            channels.map {
                val c = it.jsonObject
                LogEntry(
                    name = c["name"]?.jsonPrimitive?.content ?: "Unknown",
                    success = c["success"]?.jsonPrimitive?.booleanOrNull ?: false,
                    message = c["message"]?.jsonPrimitive?.content ?: ""
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun inferTitle(url: String): String {
        return when {
            url.contains("youtube.com") || url.contains("youtu.be") -> "YouTube Stream"
            url.contains("twitch.tv")                               -> "Twitch Stream"
            url.contains(".m3u8")                                   -> "HLS Stream"
            else                                                    -> "Stream Extraído"
        }
    }

    // ── Data classes ──────────────────────────────────────────────────────────

    data class ExtractedLink(
        val originalUrl:      String,
        val resolvedUrl:      String,
        val title:            String,
        val timestamp:        Long,
        val extractionMethod: String = ""
    )

    private data class ResolveResult(val url: String, val method: String)

    sealed class ExtractionStatus {
        object Idle                                   : ExtractionStatus()
        data class Extracting(val url: String)        : ExtractionStatus()
        data class Success(val resolvedUrl: String)   : ExtractionStatus()
        data class Error(val message: String)         : ExtractionStatus()
        data class AddedToPlaylist(val title: String) : ExtractionStatus()
    }

    /**
     * Represents the operational state of the v6 extraction engine.
     * Uses a data class instead of enum so that token flags can be combined freely.
     */
    data class EngineStatus(
        val label:         String,
        val dotColor:      Color,
        val labelColor:    Color,
        val isActive:      Boolean = false,
        val hasCookies:    Boolean = false,
        val hasPoToken:    Boolean = false,
        val hasVisitorData: Boolean = false
    ) {
        companion object {
            fun active(hasCookies: Boolean, hasPoToken: Boolean, hasVisitorData: Boolean) = EngineStatus(
                label          = "MOTOR ATIVO — HLS nativo + PO Token",
                dotColor       = Color(0xFF34C759),
                labelColor     = Color(0xFF34C759),
                isActive       = true,
                hasCookies     = hasCookies,
                hasPoToken     = hasPoToken,
                hasVisitorData = hasVisitorData
            )
            fun partial(hasCookies: Boolean, hasPoToken: Boolean, hasVisitorData: Boolean) = EngineStatus(
                label          = "PARCIAL — sem cookies / PO Token",
                dotColor       = Color(0xFFFF9500),
                labelColor     = Color(0xFFFF9500),
                hasCookies     = hasCookies,
                hasPoToken     = hasPoToken,
                hasVisitorData = hasVisitorData
            )
            fun waiting() = EngineStatus(
                label      = "AGUARDANDO TOKENS — abra a extensão",
                dotColor   = Color(0xFF8E8E93),
                labelColor = Color(0xFF8E8E93)
            )
            fun unknown() = EngineStatus(
                label      = "VERIFICANDO...",
                dotColor   = Color(0xFF8E8E93),
                labelColor = Color(0xFF8E8E93)
            )
        }
    }

    data class LogEntry(
        val name: String,
        val success: Boolean,
        val message: String = ""
    )
}
