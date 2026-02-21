package com.m3u.data.service

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ErrorReporter {
    private const val DIR_NAME = "M3UPLAYERROS"
    private const val FILE_NAME = "reportbug.txt"

    fun log(context: Context, tag: String, message: String, throwable: Throwable? = null) {
        runCatching {
            // Tenta salvar na raiz do armazenamento interno (requer permissão)
            var dir = File(Environment.getExternalStorageDirectory(), DIR_NAME)
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    // Fallback para diretório de arquivos externos do app
                    val base = context.getExternalFilesDir(null) ?: context.filesDir
                    dir = File(base, DIR_NAME)
                    if (!dir.exists()) dir.mkdirs()
                }
            }
            
            val file = File(dir, FILE_NAME)
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val sb = StringBuilder()
            sb.appendLine("[$timestamp][$tag] $message")
            if (throwable != null) {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                sb.appendLine(sw.toString())
            }
            file.appendText(sb.toString())
        }
    }
}
