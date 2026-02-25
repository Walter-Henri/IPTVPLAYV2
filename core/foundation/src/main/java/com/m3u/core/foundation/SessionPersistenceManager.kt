package com.m3u.core.foundation

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.*

/**
 * SessionPersistenceManager
 *
 * Manages the shared 'id.txt' file used to synchronize session identity
 * between the Plugin and the Universal Player.
 */
object SessionPersistenceManager {

    private const val TAG = "SessionPersistence"
    private const val FILE_NAME = "m3u_session.id"
    
    // Caminho personalizado: Pasta IPTVPLAY ID na raiz do Internal Storage
    private val sessionFile: File by lazy {
        val root = Environment.getExternalStorageDirectory()
        File(root, "IPTVPLAY ID/$FILE_NAME")
    }

    data class SessionData(
        val ua: String = "",
        val cookies: String = "",
        val poToken: String = "",
        val visitorData: String = "",
        val referer: String = "https://www.youtube.com/",
        val origin: String = "https://www.youtube.com",
        val genTime: Long = 0L,
        val sourceIp: String = ""
    )

    /** Saves data to id.txt in INI format */
    fun saveSession(data: SessionData) {
        try {
            val content = buildString {
                appendLine("[SESSION]")
                appendLine("UA=${data.ua}")
                appendLine("COOKIES=${data.cookies}")
                appendLine("PO_TOKEN=${data.poToken}")
                appendLine("VISITOR_DATA=${data.visitorData}")
                appendLine("REFERER=${data.referer}")
                appendLine("ORIGIN=${data.origin}")
                appendLine("GEN_TIME=${data.genTime}")
                appendLine("SOURCE_IP=${data.sourceIp}")
            }
            sessionFile.parentFile?.mkdirs()
            sessionFile.writeText(content)
            Log.d(TAG, "Sessão salva em: ${sessionFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao salvar sessão: ${e.message}")
        }
    }

    /** Reads and parses id.txt */
    fun loadSession(): SessionData? {
        if (!sessionFile.exists()) {
            Log.w(TAG, "Arquivo de sessão não encontrado: ${sessionFile.absolutePath}")
            return null
        }
        
        return try {
            val lines = sessionFile.readLines()
            val map = mutableMapOf<String, String>()
            lines.forEach { line ->
                if (line.contains("=")) {
                    val parts = line.split("=", limit = 2)
                    if (parts.size == 2) {
                        map[parts[0].trim()] = parts[1].trim()
                    }
                }
            }
            
            SessionData(
                ua = map["UA"] ?: "",
                cookies = map["COOKIES"] ?: "",
                poToken = map["PO_TOKEN"] ?: "",
                visitorData = map["VISITOR_DATA"] ?: "",
                referer = map["REFERER"] ?: "https://www.youtube.com/",
                origin = map["ORIGIN"] ?: "https://www.youtube.com",
                genTime = map["GEN_TIME"]?.toLongOrNull() ?: 0L,
                sourceIp = map["SOURCE_IP"] ?: ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao ler sessão: ${e.message}")
            null
        }
    }

    /** 
     * Retorna o IP local (fallback) ou pode ser expandido para buscar o IP Público
     * Usado para detectar mudança de rede que invalida a VMP (Virtual Mobile Player)
     */
    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is InetAddress) {
                        val ip = address.hostAddress ?: ""
                        if (!ip.contains(":")) return ip // Prefer IPv4
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao obter IP: ${e.message}")
        }
        return "127.0.0.1"
    }
}
