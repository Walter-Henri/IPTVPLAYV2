package com.m3u.extension.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.google.gson.Gson
import com.m3u.extension.logic.YouTubeInteractor
import com.m3u.extension.util.LogManager
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Worker na extensão responsável por extrair links m3u8 a cada 4h.
 */
class LinkExtractionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val youtubeInteractor = YouTubeInteractor(applicationContext)

    private data class ChannelData(
        val name: String,
        val url: String,
        val logo: String? = null,
        val group: String? = null
    )
    
    private data class ChannelsData(
        val channels: List<ChannelData>
    )

    override suspend fun doWork(): ListenableWorker.Result {
        val prefs = com.m3u.extension.preferences.ExtensionPreferences(applicationContext)
        prefs.updateStatus("Em execução...")
        
        return try {
            LogManager.info("Iniciando extração recorrente de 4h...", "WORKER")
            
            val cacheFile = File(applicationContext.cacheDir, "channels_cache.json")
            if (!cacheFile.exists()) {
                LogManager.warn("Cache de canais não encontrado.", "WORKER")
                prefs.updateStatus("Aguardando sincronização (Cache vazio)")
                return ListenableWorker.Result.success()
            }

            val jsonContent = cacheFile.readText()
            val gson = Gson()
            val channelsData = try {
                gson.fromJson(jsonContent, ChannelsData::class.java)
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

            // Resolve links sequentially
            val resolvedChannels = channels.map { channel ->
                try {
                    val result = youtubeInteractor.resolve(channel.url)
                    val resolvedUrl = result.getOrNull()
                    if (resolvedUrl != null) {
                        successCount++
                        channel.copy(url = resolvedUrl)
                    } else {
                        failCount++
                        channel
                    }
                } catch (e: Exception) {
                    LogManager.warn("Falha ao resolver canal ${channel.name}: ${e.message}", "WORKER")
                    failCount++
                    channel
                }
            }

            // Save resolved list
            val resolvedJson = gson.toJson(ChannelsData(resolvedChannels))
            val resolvedFile = File(applicationContext.cacheDir, "resolved_channels_cache.json")
            resolvedFile.writeText(resolvedJson)
            
            val msg = "Extração concluída: $successCount sucessos, $failCount falhas"
            LogManager.info(msg, "WORKER")
            
            prefs.recordRun(System.currentTimeMillis(), successCount, failCount)
            prefs.updateStatus("Aguardando próxima execução (Última: Sucesso)")
            
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
