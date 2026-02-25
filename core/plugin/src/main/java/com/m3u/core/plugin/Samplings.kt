package com.m3u.core.plugin

import android.util.Log
import kotlin.system.measureTimeMillis

object Samplings {
    @PublishedApi
    internal const val TAG = "Samplings"
    @PublishedApi
    internal var depth = 0

    inline fun <T> measure(label: String, block: () -> T): T {
        val indent = "  ".repeat(depth)
        Log.d(TAG, "$indent-> $label")
        depth++
        val result: T
        val time = measureTimeMillis {
            result = block()
        }
        depth--
        Log.d(TAG, "$indent<- $label: ${time}ms")
        return result
    }

    fun separate() {
        Log.d(TAG, "---")
    }
}
