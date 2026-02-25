package com.m3u.core.foundation

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import android.util.Log

/**
 * IdentityRegistry
 *
 * Thread-safe global registry for all session tokens shared between
 * the Plugin app (com.m3u.plugin) and the Universal app (com.m3u.universal).
 *
 * Tokens are kept in-memory for speed AND persisted to SharedPreferences so they
 * survive process restarts (important when the Plugin restarts before the main app).
 *
 * Data flow:
 *   Plugin → broadcasts com.m3u.IDENTITY_UPDATE
 *   → ChannelDataReceiver.onReceive
 *   → IdentityRegistry.applyBroadcast(intent)
 *   → PlayerManagerImpl picks them up via IdentityRegistry.applyTo(headers, url)
 */
object IdentityRegistry {

    private const val PREFS_NAME    = "m3u_identity_registry_v2"
    private const val KEY_UA        = "user_agent"
    private const val KEY_COOKIES   = "cookies_yt"
    private const val KEY_PO_TOKEN  = "po_token"
    private const val KEY_VISITOR   = "visitor_data"
    private const val KEY_CLIENT_VER= "client_version"

    // ---------- in-memory (fast path) ----------
    private val userAgent    = AtomicReference<String?>(null)
    private val poToken      = AtomicReference<String?>(null)
    private val visitorData  = AtomicReference<String?>(null)
    private val clientVersion= AtomicReference<String?>(null)
    private val cookies      = ConcurrentHashMap<String, String>() // domain → cookie string

    // SharedPreferences handle (set on first call to init)
    private val prefs = AtomicReference<SharedPreferences?>(null)

    // ---------- init ----------

    /** Call once from Application.onCreate() to load persisted tokens. */
    fun init(context: Context) {
        val sp = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.set(sp)

        // 1. Carregar do SharedPreferences (cache local do app)
        sp.getString(KEY_UA, null)?.let       { userAgent.set(it) }
        sp.getString(KEY_COOKIES, null)?.let  { cookies["youtube.com"] = it; cookies["googlevideo.com"] = it }
        sp.getString(KEY_PO_TOKEN, null)?.let { poToken.set(it) }
        sp.getString(KEY_VISITOR, null)?.let  { visitorData.set(it) }
        sp.getString(KEY_CLIENT_VER, null)?.let { clientVersion.set(it) }

        // 2. Tentar carregar do arquivo de sessão compartilhado (Identidade Única)
        loadFromSessionFile()
    }

    private fun loadFromSessionFile() {
        SessionPersistenceManager.loadSession()?.let { session: SessionPersistenceManager.SessionData ->
            if (session.ua.isNotBlank()) userAgent.set(session.ua)
            if (session.cookies.isNotBlank()) {
                cookies["youtube.com"] = session.cookies
                cookies["googlevideo.com"] = session.cookies
            }
            if (session.poToken.isNotBlank()) poToken.set(session.poToken)
            if (session.visitorData.isNotBlank()) visitorData.set(session.visitorData)
            
            Log.d("IdentityRegistry", "Sessão carregada do arquivo id.txt (GenTime: ${session.genTime})")
        }
    }

    // ---------- setters ----------

    fun setUserAgent(ua: String) {
        if (ua.isBlank()) return
        userAgent.set(ua)
        save(KEY_UA, ua)
    }

    fun setCookie(domain: String, cookie: String) {
        if (cookie.isBlank()) return
        cookies[domain] = cookie
        // Persist YouTube cookies (covers both googlevideo subdomains)
        if (domain.contains("youtube") || domain.contains("googlevideo")) {
            save(KEY_COOKIES, cookie)
        }
    }

    fun setPoToken(token: String) {
        if (token.isBlank()) return
        poToken.set(token)
        save(KEY_PO_TOKEN, token)
    }

    fun setVisitorData(data: String) {
        if (data.isBlank()) return
        visitorData.set(data)
        save(KEY_VISITOR, data)
    }

    fun setClientVersion(version: String) {
        if (version.isBlank()) return
        clientVersion.set(version)
        save(KEY_CLIENT_VER, version)
    }

    // ---------- getters ----------

    fun getUserAgent(): String?    = userAgent.get()
    fun getPoToken(): String?      = poToken.get()
    fun getVisitorData(): String?  = visitorData.get()
    fun getCookie(domain: String): String? = cookies[domain]

    fun hasValidIdentity(): Boolean =
        userAgent.get() != null && cookies.containsKey("youtube.com")

    // ---------- apply to headers ----------

    /**
     * Merges registry tokens into [headers] for the given [url].
     * For YouTube/Google domains, it ensures critical tokens (Cookies, UA) are correctly applied.
     */
    fun applyTo(headers: MutableMap<String, String>, url: String? = null) {
        val ua = userAgent.get()
        if (!ua.isNullOrBlank()) {
            // Requirement 1: UA must be consistent. If it's a google domain, enforce our latest UA.
            val domain = url?.let { extractDomain(it) } ?: ""
            val isG = domain.contains("youtube") || domain.contains("googlevideo")
            
            if (isG || headers["User-Agent"].isNullOrBlank()) {
                headers["User-Agent"] = ua
            }
        }

        if (url != null) {
            val domain = extractDomain(url)
            val cookie = cookies[domain]
            
            val isYt = domain.contains("youtube") || domain.contains("googlevideo")
            
            if (!cookie.isNullOrBlank()) {
                // For YouTube, we ALWAYS want the latest valid session cookie
                if (isYt || headers["Cookie"].isNullOrBlank()) {
                    headers["Cookie"] = cookie
                }
            }

            // YouTube-specific headers
            if (isYt) {
                if (headers["Referer"].isNullOrBlank()) headers["Referer"] = "https://www.youtube.com/"
                if (headers["Origin"].isNullOrBlank())  headers["Origin"]  = "https://www.youtube.com"
                
                visitorData.get()?.takeIf { it.isNotBlank() }?.let {
                    headers["X-Goog-Visitor-Id"] = it
                }
                
                clientVersion.get()?.takeIf { it.isNotBlank() }?.let {
                    headers["X-YouTube-Client-Name"]    = "1"
                    headers["X-YouTube-Client-Version"] = it
                }
                
                // PO Token Support (Critical for modern YouTube playback)
                poToken.get()?.takeIf { it.isNotBlank() }?.let {
                    headers["X-YouTube-Po-Token"] = it
                }
            }
        }
    }

    /**
     * Applies ALL broadcast extras from a com.m3u.IDENTITY_UPDATE intent.
     * Handles all token fields emitted by YouTubeWebViewTokenManager.notifyMainApp().
     */
    fun applyBroadcast(intent: android.content.Intent) {
        intent.getStringExtra("user_agent")?.takeIf { it.isNotBlank() }?.let    { setUserAgent(it) }
        intent.getStringExtra("cookies")?.takeIf { it.isNotBlank() }?.let       {
            setCookie("youtube.com", it)
            setCookie("googlevideo.com", it)
        }
        intent.getStringExtra("po_token")?.takeIf { it.isNotBlank() }?.let      { setPoToken(it) }
        intent.getStringExtra("visitor_data")?.takeIf { it.isNotBlank() }?.let  { setVisitorData(it) }
        intent.getStringExtra("client_version")?.takeIf { it.isNotBlank() }?.let{ setClientVersion(it) }
        
        // Sincronizar com o arquivo após broadcast (garante que Universal também salve se atuar como mestre)
        saveToSessionFile()
    }

    private fun saveToSessionFile() {
        val cookiesStr = cookies["youtube.com"] ?: ""
        if (userAgent.get().isNullOrBlank()) return
        
        SessionPersistenceManager.saveSession(
            SessionPersistenceManager.SessionData(
                ua = userAgent.get() ?: "",
                cookies = cookiesStr,
                poToken = poToken.get() ?: "",
                visitorData = visitorData.get() ?: "",
                genTime = System.currentTimeMillis() / 1000,
                sourceIp = SessionPersistenceManager.getLocalIpAddress()
            )
        )
    }

    /** Returns a debug summary string for logs / settings screen. */
    fun debugSummary(): String = buildString {
        appendLine("=== IdentityRegistry ===")
        appendLine("UA        : ${userAgent.get()?.take(60) ?: "MISSING"}")
        appendLine("Cookies   : ${if (cookies.containsKey("youtube.com")) "${cookies["youtube.com"]?.length} chars" else "MISSING"}")
        appendLine("PO Token  : ${poToken.get()?.take(20)?.plus("...") ?: "MISSING"}")
        appendLine("Visitor   : ${visitorData.get()?.take(20)?.plus("...") ?: "MISSING"}")
        appendLine("CLVersion : ${clientVersion.get() ?: "MISSING"}")
    }

    // ---------- private ----------

    private fun save(key: String, value: String) {
        prefs.get()?.edit()?.putString(key, value)?.apply()
    }

    private fun extractDomain(url: String): String {
        return try {
            val host = java.net.URI(url).host ?: ""
            when {
                host.contains("youtube.com")    -> "youtube.com"
                host.contains("googlevideo.com")-> "youtube.com" // same cookie jar
                else                            -> host
            }
        } catch (_: Exception) { "" }
    }
}
