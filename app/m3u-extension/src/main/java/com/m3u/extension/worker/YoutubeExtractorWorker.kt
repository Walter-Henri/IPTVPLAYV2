package com.m3u.extension.worker

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import androidx.work.*
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.m3u.extension.util.LogManager
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * YoutubeExtractorWorker - Manages background Python execution for YouTube extraction.
 * Communicates with APP 1 via Broadcast Intent and FileProvider.
 */
class YoutubeExtractorWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): ListenableWorker.Result {
        LogManager.info("Starting YoutubeExtractorWorker...", TAG)

        try {
            // 1. Initialize Python
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(applicationContext))
            }
            val py = Python.getInstance()
            val module = py.getModule("extractor")

            // 2. Define paths
            val inputJson = File(applicationContext.cacheDir, "channels_cache.json")
            if (!inputJson.exists()) {
                LogManager.warn("Input channels.json not found in cache. Skipping.", TAG)
                return Result.success()
            }

            val outputJson = File(applicationContext.filesDir, "channels2.json")
            val cookiesFile = File(applicationContext.cacheDir, "cookies.txt")
            val cookiesPath = if (cookiesFile.exists()) cookiesFile.absolutePath else null

            // 3. Execute Python extraction script
            // extract(input_path, output_path, cookies_path=None, video_format='best')
            LogManager.info("Executing Python extractor script...", TAG)
            val preferences = com.m3u.extension.preferences.ExtensionPreferences(applicationContext)
            val format = preferences.format.first()
            
            module.callAttr("extract", inputJson.absolutePath, outputJson.absolutePath, cookiesPath, format)

            if (!outputJson.exists()) {
                LogManager.error("Extraction failed: output file not generated.", TAG)
                return Result.failure()
            }

            // 4. Notify APP 1 via Broadcast Intent
            notifyApp1(outputJson)

            LogManager.info("YoutubeExtractorWorker completed successfully.", TAG)
            return Result.success()

        } catch (e: Exception) {
            LogManager.error("Error in YoutubeExtractorWorker: ${e.message}", TAG)
            return Result.retry()
        }
    }

    private fun notifyApp1(outputFile: File) {
        try {
            val contentUri = FileProvider.getUriForFile(
                applicationContext,
                "${applicationContext.packageName}.provider",
                outputFile
            )

            val intent = Intent("com.m3u.CHANNEL_DATA_READY").apply {
                setDataAndType(contentUri, "application/json")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // Ensure App 1 can receive it - App 1 package is com.m3u.universal (standard)
                setPackage("com.m3u.universal") 
            }
            
            applicationContext.sendBroadcast(intent)
            LogManager.info("Broadcast sent to APP 1 with URI: $contentUri", TAG)
        } catch (e: Exception) {
            LogManager.error("Failed to notify APP 1: ${e.message}", TAG)
        }
    }

    companion object {
        private const val TAG = "YoutubeExtractorWorker"
        private const val WORK_NAME = "youtube_extractor_worker"

        fun setupPeriodicWork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<YoutubeExtractorWorker>(2, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            LogManager.info("Periodic YoutubeExtractorWorker scheduled.", TAG)
        }
    }
}
