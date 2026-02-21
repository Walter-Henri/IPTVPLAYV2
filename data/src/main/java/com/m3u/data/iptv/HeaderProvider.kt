package com.m3u.data.iptv

object HeaderProvider {
    
    // User-Agent realista que simula Chrome em Android - aceito pela maioria dos servidores
    private const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"
    
    fun getDefaultHeaders(): Map<String, String> {
        return mapOf(
            "User-Agent" to DEFAULT_USER_AGENT,
            "Accept" to "*/*",
            "Accept-Language" to "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7",
            "Accept-Encoding" to "gzip, deflate, br",
            "Connection" to "keep-alive",
            "Cache-Control" to "no-cache",
            "Pragma" to "no-cache"
        )
    }

    fun getHeadersForUrl(url: String): Map<String, String> {
        val headers = getDefaultHeaders().toMutableMap()
        
        // Adicionar Referer e Origin baseados na URL se necessário
        try {
            val uri = java.net.URI(url)
            val origin = "${uri.scheme}://${uri.host}"
            headers["Origin"] = origin
            headers["Referer"] = "$origin/"
        } catch (e: Exception) {
            // Ignorar se a URL for inválida
        }
        
        return headers
    }
    
    fun getUserAgent(): String = DEFAULT_USER_AGENT
}
