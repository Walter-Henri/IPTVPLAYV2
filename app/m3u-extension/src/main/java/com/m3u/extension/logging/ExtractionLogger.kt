package com.m3u.extension.logging

import android.content.Context
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Sistema de Logs e Relatórios de Extração
 * 
 * Características:
 * - Logs detalhados de cada tentativa
 * - Relatório consolidado por sessão
 * - Estatísticas de sucesso/falha
 * - Identificação do motor usado
 * - Exportação para arquivo
 */
class ExtractionLogger(private val context: Context) {
    
    companion object {
        private const val TAG = "ExtractionLogger"
        private const val LOG_DIR = "extraction_logs"
        private const val MAX_LOG_FILES = 10
    }
    
    private val mutex = Mutex()
    private val sessionId = System.currentTimeMillis()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    data class ChannelLog(
        val name: String,
        val url: String,
        val success: Boolean,
        val extractionMethod: String?,
        val error: String?,
        val attempts: List<AttemptLog>,
        val timestamp: Long
    )
    
    data class AttemptLog(
        val method: String,
        val userAgent: String?,
        val success: Boolean,
        val error: String?,
        val duration: Long
    )
    
    data class SessionReport(
        val sessionId: Long,
        val startTime: Long,
        val endTime: Long,
        val totalChannels: Int,
        val successCount: Int,
        val failCount: Int,
        val channels: List<ChannelLog>,
        val methodStats: Map<String, Int>
    )
    
    private val channelLogs = mutableListOf<ChannelLog>()
    private var sessionStartTime = System.currentTimeMillis()
    
    /**
     * Registra uma tentativa de extração
     */
    suspend fun logAttempt(
        channelName: String,
        channelUrl: String,
        method: String,
        userAgent: String?,
        success: Boolean,
        error: String?,
        duration: Long
    ) = mutex.withLock {
        val attempt = AttemptLog(
            method = method,
            userAgent = userAgent,
            success = success,
            error = error,
            duration = duration
        )
        
        Log.d(TAG, "[$method] $channelName: ${if (success) "✓" else "✗"} (${duration}ms)")
        if (error != null) {
            Log.d(TAG, "  Erro: $error")
        }
        
        // Enviar para o LogManager da UI
        val statusIcon = if (success) "✓" else "✗"
        val logLevel = if (success) com.m3u.extension.util.LogManager.LogLevel.INFO else com.m3u.extension.util.LogManager.LogLevel.WARN
        com.m3u.extension.util.LogManager.log(
            message = "[$method] $statusIcon $channelName (${duration}ms)",
            level = logLevel,
            tag = "EXTRACTION"
        )
        if (!success && error != null) {
            com.m3u.extension.util.LogManager.warn("  → Motivo: $error", "EXTRACTION")
        }
    }
    
    /**
     * Registra o resultado final de um canal
     */
    suspend fun logChannel(
        name: String,
        url: String,
        success: Boolean,
        extractionMethod: String?,
        error: String?,
        attempts: List<AttemptLog> = emptyList()
    ) = mutex.withLock {
        val channelLog = ChannelLog(
            name = name,
            url = url,
            success = success,
            extractionMethod = extractionMethod,
            error = error,
            attempts = attempts,
            timestamp = System.currentTimeMillis()
        )
        
        channelLogs.add(channelLog)
        
        val status = if (success) "✅ SUCESSO" else "❌ FALHA"
        val methodInfo = extractionMethod?.let { " ($it)" } ?: ""
        Log.i(TAG, "$status: $name$methodInfo")
        
        // Enviar resumo final do canal para o LogManager da UI
        val uiLevel = if (success) com.m3u.extension.util.LogManager.LogLevel.INFO else com.m3u.extension.util.LogManager.LogLevel.ERROR
        com.m3u.extension.util.LogManager.log(
            message = "$status: $name",
            level = uiLevel,
            tag = "FINAL"
        )
    }
    
    /**
     * Gera relatório da sessão
     */
    suspend fun generateReport(): SessionReport = mutex.withLock {
        val endTime = System.currentTimeMillis()
        val successCount = channelLogs.count { it.success }
        val failCount = channelLogs.count { !it.success }
        
        // Estatísticas por método
        val methodStats = channelLogs
            .filter { it.success && it.extractionMethod != null }
            .groupBy { it.extractionMethod!! }
            .mapValues { it.value.size }
        
        SessionReport(
            sessionId = sessionId,
            startTime = sessionStartTime,
            endTime = endTime,
            totalChannels = channelLogs.size,
            successCount = successCount,
            failCount = failCount,
            channels = channelLogs.toList(),
            methodStats = methodStats
        )
    }
    
    /**
     * Formata relatório como texto
     */
    suspend fun formatReportAsText(): String {
        val report = generateReport()
        val duration = (report.endTime - report.startTime) / 1000
        
        return buildString {
            appendLine("═══════════════════════════════════════════════════════")
            appendLine("        RELATÓRIO DE EXTRAÇÃO DE STREAMS")
            appendLine("═══════════════════════════════════════════════════════")
            appendLine()
            appendLine("📅 Sessão: ${dateFormat.format(Date(report.sessionId))}")
            appendLine("⏱️  Duração: ${duration}s")
            appendLine()
            appendLine("📊 ESTATÍSTICAS GERAIS")
            appendLine("───────────────────────────────────────────────────────")
            appendLine("Total de canais: ${report.totalChannels}")
            appendLine("✅ Sucessos: ${report.successCount} (${(report.successCount * 100.0 / report.totalChannels).toInt()}%)")
            appendLine("❌ Falhas: ${report.failCount} (${(report.failCount * 100.0 / report.totalChannels).toInt()}%)")
            appendLine()
            
            if (report.methodStats.isNotEmpty()) {
                appendLine("🔧 MOTORES DE EXTRAÇÃO")
                appendLine("───────────────────────────────────────────────────────")
                report.methodStats.entries
                    .sortedByDescending { it.value }
                    .forEach { (method, count) ->
                        val percentage = (count * 100.0 / report.successCount).toInt()
                        appendLine("  $method: $count canais ($percentage%)")
                    }
                appendLine()
            }
            
            appendLine("✅ CANAIS COM SUCESSO (${report.successCount})")
            appendLine("───────────────────────────────────────────────────────")
            report.channels.filter { it.success }.forEach { channel ->
                appendLine("  • ${channel.name}")
                appendLine("    Motor: ${channel.extractionMethod ?: "desconhecido"}")
                if (channel.attempts.isNotEmpty()) {
                    val totalAttempts = channel.attempts.size
                    appendLine("    Tentativas: $totalAttempts")
                }
            }
            appendLine()
            
            if (report.failCount > 0) {
                appendLine("❌ CANAIS COM FALHA (${report.failCount})")
                appendLine("───────────────────────────────────────────────────────")
                report.channels.filter { !it.success }.forEach { channel ->
                    appendLine("  • ${channel.name}")
                    appendLine("    Erro: ${channel.error ?: "desconhecido"}")
                    if (channel.attempts.isNotEmpty()) {
                        appendLine("    Tentativas realizadas:")
                        channel.attempts.forEach { attempt ->
                            val status = if (attempt.success) "✓" else "✗"
                            appendLine("      $status ${attempt.method} (${attempt.duration}ms)")
                            if (attempt.error != null) {
                                appendLine("        ${attempt.error}")
                            }
                        }
                    }
                }
            }
            
            appendLine()
            appendLine("═══════════════════════════════════════════════════════")
        }
    }
    
    /**
     * Salva relatório em arquivo
     */
    suspend fun saveReportToFile(): File {
        val logDir = File(context.getExternalFilesDir(null), LOG_DIR).apply { mkdirs() }
        val reportFile = File(logDir, "extraction_report_$sessionId.txt")
        
        val reportText = formatReportAsText()
        reportFile.writeText(reportText)
        
        // Limpar logs antigos
        cleanOldLogs(logDir)
        
        Log.i(TAG, "Relatório salvo em: ${reportFile.absolutePath}")
        return reportFile
    }
    
    /**
     * Limpa logs antigos, mantendo apenas os mais recentes
     */
    private fun cleanOldLogs(logDir: File) {
        val logFiles = logDir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        
        if (logFiles.size > MAX_LOG_FILES) {
            logFiles.drop(MAX_LOG_FILES).forEach { file ->
                file.delete()
                Log.d(TAG, "Log antigo removido: ${file.name}")
            }
        }
    }
    
    /**
     * Obtém resumo rápido
     */
    suspend fun getQuickSummary(): String = mutex.withLock {
        val successCount = channelLogs.count { it.success }
        val totalCount = channelLogs.size
        
        if (totalCount == 0) {
            return@withLock "Nenhum canal processado"
        }
        
        val percentage = (successCount * 100.0 / totalCount).toInt()
        "✅ $successCount/$totalCount ($percentage%)"
    }
    
    /**
     * Reseta a sessão
     */
    suspend fun reset() = mutex.withLock {
        channelLogs.clear()
        sessionStartTime = System.currentTimeMillis()
        Log.d(TAG, "Logger resetado para nova sessão")
    }
}
