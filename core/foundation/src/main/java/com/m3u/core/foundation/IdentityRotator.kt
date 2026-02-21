package com.m3u.core.foundation

/**
 * IdentityRotator - Gerencia a rotação de identidades (User-Agents) para bypass de 403.
 * Mantém sincronia com a lógica do extrator Python.
 */
object IdentityRotator {
    private val USER_AGENTS = listOf(
        // 1. Padrão Smart TV (Bravia)
        "Mozilla/5.0 (Linux; Android 10; BRAVIA 4K UR2 Build/PTT1.190515.001.S52) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
        // 2. Chrome Desktop (Windows)
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
        // 3. Safari (macOS)
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15",
        // 4. Firefox (Linux)
        "Mozilla/5.0 (X11; Linux x86_64; rv:122.0) Gecko/20100101 Firefox/122.0"
    )

    fun getUA(index: Int): String {
        return USER_AGENTS[index.coerceIn(0, USER_AGENTS.size - 1)]
    }

    fun getNextUA(currentUA: String): String {
        val currentIndex = USER_AGENTS.indexOf(currentUA)
        val nextIndex = (currentIndex + 1) % USER_AGENTS.size
        return USER_AGENTS[nextIndex]
    }
    
    val count: Int get() = USER_AGENTS.size
}
