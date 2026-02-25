package com.m3u.plugin.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.google.gson.Gson
import com.m3u.plugin.newpipe.NewPipeResolver
import com.m3u.plugin.util.LogManager
import com.m3u.plugin.notifyChannelDataReady
import java.io.File
import java.util.concurrent.TimeUnit
import com.m3u.plugin.dropbox.DropboxRepository

/**
 * Worker na extensão responsável por extrair links m3u8 a cada 4h.
 * Refatorado para usar o motor 100% nativo NewPipe Extractor.
 */
class LinkExtractionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    /**
     * Input model: matches the raw Dropbox `channels.json` format.
     */
    private data class ChannelInput(
        val name: String,
        val url: String,
        val logo: String? = null,
        val group: String? = null
    )

    /**
     * Output model: enriched resolved channel with all fields the host expects.
     * Maps 1:1 to the host's `ChannelJsonItem` (name, url/m3u8, logo, group, success, headers, error).
     */
    private data class ResolvedChannel(
        val name: String,
        val url: String,           // Original YouTube URL (kept for reference)
        val m3u8: String? = null,  // Resolved HLS stream URL
        val logo: String? = null,
        val group: String? = null,
        val success: Boolean = true,
        val headers: Map<String, String>? = null,
        val error: String? = null
    )

    private data class ChannelsInput(
        val channels: List<ChannelInput>
    )

    private data class ResolvedOutput(
        val channels: List<ResolvedChannel>
    )

    override suspend fun doWork(): ListenableWorker.Result {
        val prefs = com.m3u.plugin.preferences.PluginPreferences(applicationContext)
        prefs.updateStatus("Em execução...")
        
        return try {
            LogManager.info("Iniciando extração recorrente...", "WORKER")
            
            val cacheFile = File(applicationContext.filesDir, "channels.json")
            val lastSync = prefs.lastDropboxSync 
            val thirtyDaysMs = TimeUnit.DAYS.toMillis(30)
            
            if (!cacheFile.exists() || (System.currentTimeMillis() - lastSync > thirtyDaysMs)) {
                LogManager.info("Cache expirado ou inexistente. Baixando do Dropbox...", "WORKER")
                val dropboxRepo = DropboxRepository(applicationContext)
                val downloaded = dropboxRepo.downloadChannelsJson()
                if (downloaded != null) {
                    prefs.recordDropboxSync(System.currentTimeMillis())
                } else if (!cacheFile.exists()) {
                    LogManager.warn("Sync falhou e não há cache local.", "WORKER")
                    prefs.updateStatus("Aguardando sincronização (Dropbox Offline)")
                    return ListenableWorker.Result.success()
                }
            }

            val jsonContent = cacheFile.readText()
            val gson = Gson()
            val channelsData = try {
                gson.fromJson(jsonContent, ChannelsInput::class.java)
            } catch (e: Exception) {
                LogManager.error("JSON inválido no cache: ${e.message}", "WORKER")
                prefs.updateStatus("Erro: Cache inválido")
                return ListenableWorker.Result.failure()
            }

            val channels = channelsData?.channels ?: emptyList()
            if (channels.isEmpty()) {
                prefs.updateStatus("Aguardando canais")
                return ListenableWorker.Result.success()
            }

            var successCount = 0
            var failCount = 0
            val successNames = mutableListOf<String>()
            val failNames = mutableListOf<String>()

            // Resolve links sequentially using NewPipe
            val resolvedChannels = channels.map { channel ->
                try {
                    // Clear headers before each extraction to capture per-channel headers
                    com.m3u.plugin.newpipe.NewPipeExDownloader.clearCapturedHeaders()
                    
                    val result = NewPipeResolver.resolve(channel.url)
                    val resolvedUrl = result.getOrNull()
                    
                    if (resolvedUrl != null) {
                        successCount++
                        successNames.add(channel.name)
                        
                        // Capture the anti-403 headers gathered during this extraction
                        val capturedHeaders = com.m3u.plugin.newpipe.NewPipeExDownloader
                            .lastCapturedHeaders.toMap()
                        
                        ResolvedChannel(
                            name = channel.name,
                            url = channel.url,
                            m3u8 = resolvedUrl,
                            logo = channel.logo,
                            group = channel.group,
                            success = true,
                            headers = if (capturedHeaders.isNotEmpty()) capturedHeaders else null
                        )
                    } else {
                        failCount++
                        failNames.add(channel.name)
                        val errorMsg = result.exceptionOrNull()?.message ?: "Falha na extração"
                        ResolvedChannel(
                            name = channel.name,
                            url = channel.url,
                            logo = channel.logo,
                            group = channel.group,
                            success = false,
                            error = errorMsg
                        )
                    }
                } catch (e: Exception) {
                    LogManager.warn("Falha ao resolver canal ${channel.name}: ${e.message}", "WORKER")
                    failCount++
                    failNames.add(channel.name)
                    ResolvedChannel(
                        name = channel.name,
                        url = channel.url,
                        logo = channel.logo,
                        group = channel.group,
                        success = false,
                        error = e.message ?: "Erro desconhecido"
                    )
                }
            }

            // Save resolved list in the format the host expects
            val resolvedJson = gson.toJson(ResolvedOutput(resolvedChannels))
            val resolvedFile = File(applicationContext.cacheDir, "resolved_channels_cache.json")
            resolvedFile.writeText(resolvedJson)
            
            // Notifica o app Universal (via Broadcast) que a nova lista resolvida está pronta
            notifyChannelDataReady(applicationContext, resolvedFile)
            
            val msg = "Extração concluída: $successCount sucessos, $failCount falhas"
            LogManager.info(msg, "WORKER")
            prefs.recordRun(System.currentTimeMillis(), successCount, failCount, successNames, failNames)
            
            ListenableWorker.Result.success()
        } catch (e: Exception) {
            LogManager.error("Erro na extração: ${e.message}", "WORKER")
            prefs.updateStatus("Erro: ${e.message}")
            ListenableWorker.Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "link_extraction_worker"

        fun setupPeriodicWork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<LinkExtractionWorker>(4, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
