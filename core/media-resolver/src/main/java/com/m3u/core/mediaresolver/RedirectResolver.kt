package com.m3u.core.mediaresolver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import java.util.regex.Pattern

/**
 * Resolvedor de redirecionamentos HTTP
 * Segue cadeias de redirecionamento (3xx), meta-refresh e scripts de redirecionamento.
 */
class RedirectResolver @Inject constructor(
    private val config: MediaResolverConfig
) {
    
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(config.connectTimeout, TimeUnit.MILLISECONDS)
            .readTimeout(config.readTimeout, TimeUnit.MILLISECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", config.userAgent)
                    .header("Accept", "*/*")
                    .header("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
                    .build()
                chain.proceed(request)
            }
            .build()
    }
    
    /**
     * Resolve redirecionamentos HTTP seguindo a cadeia completa
     * 
     * @param url URL inicial
     * @return URL final após todos os redirecionamentos
     */
    suspend fun resolveRedirects(url: String): RedirectResult = withContext(Dispatchers.IO) {
        try {
            if (!isValidUrl(url)) {
                return@withContext RedirectResult.Error(
                    ResolveError.INVALID_URL,
                    "URL inválida: $url"
                )
            }
            
            var currentUrl = url
            var redirectCount = 0
            val headers = mutableMapOf<String, String>()
            
            while (redirectCount < config.maxRedirects) {
                // Se a URL já parece ser um stream direto, evitamos HEAD para prevenir erros 403/405
                // Muitos servidores de IPTV bloqueiam HEAD. Expandido para cobrir mais padrões.
                val isDirectStream = currentUrl.contains(".m3u8", ignoreCase = true) || 
                                   currentUrl.contains(".mpd", ignoreCase = true) ||
                                   currentUrl.contains(".ts", ignoreCase = true) ||
                                   currentUrl.contains(".mp4", ignoreCase = true) ||
                                   currentUrl.contains("/manifest", ignoreCase = true) ||
                                   currentUrl.contains("/playlist", ignoreCase = true) ||
                                   currentUrl.contains("/live/", ignoreCase = true) ||
                                   currentUrl.contains("/hls/", ignoreCase = true) ||
                                   currentUrl.contains("/stream/", ignoreCase = true) ||
                                   currentUrl.contains("token=", ignoreCase = true) ||
                                   currentUrl.contains("sig=", ignoreCase = true)

                var request = Request.Builder()
                    .url(currentUrl)
                    .apply {
                        if (isDirectStream) get() else head()
                    }
                    .build()
                
                var response = try {
                    client.newCall(request).execute()
                } catch (e: IOException) {
                    return@withContext RedirectResult.Error(
                        ResolveError.NETWORK_ERROR,
                        "Erro de rede: ${e.message}"
                    )
                }
                
                // Se for 200 OK mas é HTML, pode ser um encurtador com meta-refresh ou JS
                if (response.isSuccessful && isHtml(response.header("Content-Type"))) {
                    response.close() // Fecha a resposta anterior
                    
                    // Faz GET para ler o conteúdo
                    request = Request.Builder()
                        .url(currentUrl)
                        .get()
                        .build()
                        
                    response = try {
                        client.newCall(request).execute()
                    } catch (e: IOException) {
                        return@withContext RedirectResult.Error(
                            ResolveError.NETWORK_ERROR,
                            "Erro de rede ao buscar conteúdo HTML: ${e.message}"
                        )
                    }
                    
                    val htmlContent = response.body?.string()
                    if (!htmlContent.isNullOrBlank()) {
                        // Tenta encontrar Meta Refresh
                        val metaRedirect = extractMetaRefreshUrl(htmlContent)
                        if (metaRedirect != null) {
                            currentUrl = resolveRelativeUrl(currentUrl, metaRedirect)
                            redirectCount++
                            response.close()
                            continue
                        }
                        
                        // Tenta encontrar JS Redirect
                        val jsRedirect = extractJsRedirectUrl(htmlContent)
                        if (jsRedirect != null) {
                            currentUrl = resolveRelativeUrl(currentUrl, jsRedirect)
                            redirectCount++
                            response.close()
                            continue
                        }
                    }
                    // Se não encontrou redirecionamento no HTML, processa como sucesso (pode ser a página final)
                    // Mas vamos recriar o response body wrapper ou usar o que temos se precisarmos headers
                    // Como já lemos o body, ele foi consumido. Usamos os headers do response GET.
                }

                response.use {
                    when (it.code) {
                        in 200..299 -> {
                            // Sucesso - URL final encontrada
                            extractHeaders(it.headers, headers)
                            return@withContext RedirectResult.Success(
                                finalUrl = currentUrl,
                                headers = headers,
                                redirectCount = redirectCount,
                                format = detectStreamFormat(currentUrl, it.header("Content-Type"))
                            )
                        }
                        
                        in 300..399 -> {
                            // Redirecionamento (HTTP Header)
                            val location = it.header("Location")
                            if (location.isNullOrBlank()) {
                                return@withContext RedirectResult.Error(
                                    ResolveError.INVALID_URL,
                                    "Redirecionamento sem Location header"
                                )
                            }
                            currentUrl = resolveRelativeUrl(currentUrl, location)
                            redirectCount++
                        }
                        
                        HttpURLConnection.HTTP_UNAUTHORIZED,
                        HttpURLConnection.HTTP_FORBIDDEN -> {
                            return@withContext RedirectResult.Error(
                                ResolveError.NETWORK_ERROR,
                                "Acesso negado (${it.code})"
                            )
                        }
                        
                        HttpURLConnection.HTTP_NOT_FOUND -> {
                            return@withContext RedirectResult.Error(
                                ResolveError.INVALID_URL,
                                "URL não encontrada (404)"
                            )
                        }
                        
                        else -> {
                            return@withContext RedirectResult.Error(
                                ResolveError.NETWORK_ERROR,
                                "Código HTTP inesperado: ${it.code}"
                            )
                        }
                    }
                }
            }
            
            RedirectResult.Error(
                ResolveError.NETWORK_ERROR,
                "Número máximo de redirecionamentos excedido ($redirectCount)"
            )
            
        } catch (e: Exception) {
            RedirectResult.Error(
                ResolveError.UNKNOWN,
                "Erro inesperado: ${e.message}"
            )
        }
    }
    
    private fun isHtml(contentType: String?): Boolean {
        return contentType?.contains("text/html", ignoreCase = true) == true ||
               contentType?.contains("application/xhtml+xml", ignoreCase = true) == true
    }
    
    private fun extractMetaRefreshUrl(html: String): String? {
        return try {
            val doc = Jsoup.parse(html)
            val meta = doc.select("meta[http-equiv=refresh]").first()
            val content = meta?.attr("content")
            
            if (content != null) {
                // Formato: "5; url=http://example.com/"
                val parts = content.split(";")
                for (part in parts) {
                    val trimmed = part.trim()
                    if (trimmed.startsWith("url=", ignoreCase = true)) {
                        var url = trimmed.substring(4).trim()
                        // Remove aspas se houver
                        if ((url.startsWith("'") && url.endsWith("'")) || 
                            (url.startsWith("\"") && url.endsWith("\""))) {
                            url = url.substring(1, url.length - 1)
                        }
                        return url
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
    
    private fun extractJsRedirectUrl(html: String): String? {
        // Padrões comuns de redirecionamento JS
        val patterns = listOf(
            "window\\.location\\.href\\s*=\\s*['\"]([^'\"]+)['\"]",
            "window\\.location\\s*=\\s*['\"]([^'\"]+)['\"]",
            "location\\.href\\s*=\\s*['\"]([^'\"]+)['\"]",
            "location\\.replace\\(['\"]([^'\"]+)['\"]\\)"
        )
        
        for (pattern in patterns) {
            val matcher = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(html)
            if (matcher.find()) {
                return matcher.group(1)
            }
        }
        return null
    }
    
    /**
     * Valida se a URL é válida
     */
    private fun isValidUrl(url: String): Boolean {
        return try {
            url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("https://", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Resolve URL relativa para absoluta
     */
    private fun resolveRelativeUrl(baseUrl: String, location: String): String {
        return when {
            location.startsWith("http://", ignoreCase = true) ||
            location.startsWith("https://", ignoreCase = true) -> location
            
            location.startsWith("//") -> {
                val protocol = baseUrl.substringBefore("://")
                "$protocol:$location"
            }
            
            location.startsWith("/") -> {
                val protocol = baseUrl.substringBefore("://")
                val domain = baseUrl.substringAfter("://").substringBefore("/")
                "$protocol://$domain$location"
            }
            
            else -> {
                val basePath = baseUrl.substringBeforeLast("/")
                "$basePath/$location"
            }
        }
    }
    
    /**
     * Extrai headers relevantes da resposta
     */
    private fun extractHeaders(
        responseHeaders: okhttp3.Headers,
        targetMap: MutableMap<String, String>
    ) {
        val relevantHeaders = listOf(
            "Content-Type",
            "Content-Length",
            "Accept-Ranges",
            "Cache-Control",
            "ETag",
            "Last-Modified"
        )
        
        relevantHeaders.forEach { headerName ->
            responseHeaders[headerName]?.let { value ->
                targetMap[headerName] = value
            }
        }
    }
    
    /**
     * Detecta o formato do stream baseado na URL e Content-Type
     */
    private fun detectStreamFormat(url: String, contentType: String?): StreamFormat {
        return when {
            url.contains(".m3u8", ignoreCase = true) ||
            contentType?.contains("application/vnd.apple.mpegurl") == true ||
            contentType?.contains("application/x-mpegURL") == true -> StreamFormat.HLS
            
            url.contains(".mpd", ignoreCase = true) ||
            contentType?.contains("application/dash+xml") == true -> StreamFormat.DASH
            
            url.contains(".mp4", ignoreCase = true) ||
            contentType?.contains("video/mp4") == true -> StreamFormat.MP4
            
            url.startsWith("rtmp://", ignoreCase = true) -> StreamFormat.RTMP
            
            url.startsWith("rtsp://", ignoreCase = true) -> StreamFormat.RTSP
            
            else -> StreamFormat.UNKNOWN
        }
    }
}

/**
 * Resultado da resolução de redirecionamentos
 */
sealed class RedirectResult {
    data class Success(
        val finalUrl: String,
        val headers: Map<String, String>,
        val redirectCount: Int,
        val format: StreamFormat
    ) : RedirectResult()
    
    data class Error(
        val error: ResolveError,
        val message: String
    ) : RedirectResult()
}
