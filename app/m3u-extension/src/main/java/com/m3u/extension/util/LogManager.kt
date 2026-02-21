package com.m3u.extension.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.text.SimpleDateFormat
import java.util.*

/**
 * LogManager - Captura e gerencia logs para exibição na UI
 */
object LogManager {
    private val _logs = MutableSharedFlow<LogEntry>(
        replay = 100, 
        extraBufferCapacity = 50,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val logs = _logs.asSharedFlow()
    
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    init {
        info("Sistema de Log Inicializado", "SYSTEM")
    }

    data class LogEntry(
        val timestamp: String,
        val level: LogLevel,
        val message: String,
        val tag: String
    )

    enum class LogLevel {
        INFO, WARN, ERROR, DEBUG
    }

    fun log(message: String, level: LogLevel = LogLevel.INFO, tag: String = "EXTENSION") {
        val entry = LogEntry(
            timestamp = dateFormat.format(Date()),
            level = level,
            message = message,
            tag = tag
        )
        _logs.tryEmit(entry)
    }

    fun info(message: String, tag: String = "EXTENSION") = log(message, LogLevel.INFO, tag)
    fun warn(message: String, tag: String = "EXTENSION") = log(message, LogLevel.WARN, tag)
    fun error(message: String, tag: String = "EXTENSION") = log(message, LogLevel.ERROR, tag)
    fun debug(message: String, tag: String = "EXTENSION") = log(message, LogLevel.DEBUG, tag)
}
