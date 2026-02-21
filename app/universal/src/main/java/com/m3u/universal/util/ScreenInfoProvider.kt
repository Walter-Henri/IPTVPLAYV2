package com.m3u.universal.util

import android.app.Activity
import android.view.WindowInsets
import androidx.window.layout.WindowMetricsCalculator

class ScreenInfoProvider(private val activity: Activity) {
    data class ScreenInfo(
        val widthPx: Int,
        val heightPx: Int
    )

    fun getScreenInfo(): ScreenInfo {
        val metrics = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity)
        val rootInsets = activity.window.decorView.rootWindowInsets
        val insets = rootInsets?.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
        val safeWidth = metrics.bounds.width() - (insets?.left ?: 0) - (insets?.right ?: 0)
        val safeHeight = metrics.bounds.height() - (insets?.top ?: 0) - (insets?.bottom ?: 0)
        return ScreenInfo(safeWidth, safeHeight)
    }
}
