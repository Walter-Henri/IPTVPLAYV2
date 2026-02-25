package com.m3u.plugin

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * notifyChannelDataReady — Broadcasts a notification to the host app
 * indicating that the channel list has been successfully resolved and cached.
 *
 * It uses the "com.m3u.CHANNEL_DATA_READY" action.
 */
fun notifyChannelDataReady(context: Context, file: File) {
    val intent = Intent("com.m3u.CHANNEL_DATA_READY").apply {
        // Many IPTV apps listen for this to refresh the playlist
        // We provide the file URI so the host can read the result
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        setDataAndType(uri, "application/json")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.sendBroadcast(intent)
}
