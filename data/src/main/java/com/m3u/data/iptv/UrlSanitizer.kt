package com.m3u.data.iptv
 
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
 
object UrlSanitizer {
    /**
     * Sanitiza URLs removendo espaços, caracteres de controle
     * e decodificando apenas percent encodings válidos (%XX)
     * 
     * @param url URL a ser sanitizada
     * @return URL sanitizada e segura para uso
     */
    fun sanitize(url: String): String {
        var input = url.trim()
        val index = input.indexOf('|')
        
        val urlPart = if (index != -1) input.substring(0, index) else input
        val optionsPart = if (index != -1) input.substring(index) else ""

        // Remover espaços e caracteres de controle APENAS na parte da URL
        var sanitizedUrl = urlPart.replace("\\s".toRegex(), "")
        
        // URL Decode apenas se houver percent encoding válido (%XX) na parte da URL
        if (hasValidPercentEncoding(sanitizedUrl)) {
            try {
                sanitizedUrl = URLDecoder.decode(sanitizedUrl, StandardCharsets.UTF_8.name())
            } catch (e: Exception) {
                // Se falhar, mantém a original
            }
        }
        
        return sanitizedUrl + optionsPart
    }
    
    /**
     * Verifica se a URL tem encoding de percentual válido
     * Retorna true apenas se encontrar padrões %XX onde XX são hexadecimais válidos (0-9, A-F, a-f)
     * 
     * @param url URL a ser verificada
     * @return true se a URL contém percent encoding válido
     */
    private fun hasValidPercentEncoding(url: String): Boolean {
        return "%[0-9A-Fa-f]{2}".toRegex().containsMatchIn(url)
    }
}
