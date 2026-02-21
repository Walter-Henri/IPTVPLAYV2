package com.m3u.core.architecture.logger

import android.util.Log

interface Logger {
    fun log(message: String, level: Int = Log.DEBUG, tag: String = "M3U")
}

inline fun Logger.execute(block: () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        log(e.message ?: "Unknown error", Log.ERROR)
    }
}

inline fun <T> Logger.sandBox(block: () -> T): T? {
    return try {
        block()
    } catch (e: Exception) {
        log(e.message ?: "Unknown error", Log.ERROR)
        null
    }
}
