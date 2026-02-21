package com.m3u.data.iptv
 
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
 
/**
 * StreamValidator - Valida URLs de stream antes da reprodução
 * 
 * Funcionalidades:
 * - Verifica se a URL é acessível (HTTP HEAD/GET)
 * - Valida o Content-Type retornado pelo servidor
 * - Suporta modo estrito e permissivo para diferentes cenários IPTV
 * - Lida com servidores que bloqueiam requisições HEAD
 */
class StreamValidator(private val okHttpClient: OkHttpClient) {
    
    /**
     * Content-types estritamente válidos para streams
     * Estes são os tipos padrão e mais comuns para IPTV
     */
    private val strictContentTypes = listOf(
        "application/vnd.apple.mpegurl",   // HLS oficial
        "application/x-mpegurl",           // HLS alternativo
        "application/mpegurl",             // HLS variação
        "video/mp2t",                      // MPEG-TS
        "video/MP2T",                      // MPEG-TS (maiúsculo)
        "application/octet-stream",        // Binário genérico
        "video/mp4",                       // MP4
        "video/quicktime",                 // MOV
        "video/x-matroska",                // MKV
        "video/x-flv"                      // FLV
    )
    
    /**
     * Content-types permitidos em modo permissivo
     * Inclui tipos que alguns servidores IPTV não padronizados retornam
     */
    private val permissiveContentTypes = listOf(
        "application/octet-stream",
        "binary/octet-stream",
        "text/plain",          // Alguns servidores retornam isso
        "application/x-mpegURL" // Variações de casing
    )
 
    /**
     * Valida uma URL de stream
     * 
     * @param url URL do stream a ser validada
     * @param permissiveMode Se true, permite Content-types não estritamente válidos
     * @return ValidationResult com o resultado da validação
     */
    suspend fun validate(url: String, permissiveMode: Boolean = true): ValidationResult {
        return try {
            Timber.tag("StreamValidator").d("Validando stream: $url")
            
            // Tenta primeiro com HEAD para economizar banda
            val request = Request.Builder()
                .url(url)
                .head()
                .build()
            
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // Se HEAD falhar (alguns servidores bloqueiam), tenta GET
                    Timber.tag("StreamValidator").d("HEAD falhou, tentando GET...")
                    val getRequest = Request.Builder().url(url).build()
                    okHttpClient.newCall(getRequest).execute().use { getResponse ->
                        return processResponse(getResponse, permissiveMode)
                    }
                }
                processResponse(response, permissiveMode)
            }
        } catch (e: Exception) {
            Timber.tag("StreamValidator").e(e, "Erro ao validar stream: $url")
            ValidationResult.Error(e.message ?: "Erro desconhecido ao acessar URL")
        }
    }
 
    /**
     * Processa a resposta HTTP e determina se é válida
     * 
     * @param response Resposta HTTP do servidor
     * @param permissiveMode Se true, usa validação permissiva
     * @return ValidationResult com o resultado
     */
    private fun processResponse(response: okhttp3.Response, permissiveMode: Boolean): ValidationResult {
        // Verifica código HTTP
        if (!response.isSuccessful) {
            val reason = when (response.code) {
                404 -> "URL não encontrada (404)"
                403 -> "Acesso negado (403)"
                401 -> "Não autorizado (401)"
                in 500..599 -> "Erro do servidor (${response.code})"
                else -> "HTTP ${response.code}"
            }
            return ValidationResult.Invalid(reason)
        }
 
        val contentType = response.header("Content-Type")?.lowercase() ?: "unknown"
        Timber.tag("StreamValidator").d("Content-Type recebido: $contentType")
        
        return when {
            // 1. Verifica se é um content-type estritamente válido
            strictContentTypes.any { it.equals(contentType, ignoreCase = true) } -> {
                ValidationResult.Valid(response.request.url.toString(), contentType)
            }
            
            // 2. Modo permissivo: permite qualquer video/* e tipos comuns em IPTV
            permissiveMode && isValidPermissiveType(contentType) -> {
                Timber.tag("StreamValidator").w(
                    "Content-Type não estritamente válido, mas permitido em modo permissivo: $contentType"
                )
                ValidationResult.Valid(response.request.url.toString(), contentType)
            }
            
            // 3. Content-Type não reconhecido
            else -> {
                Timber.tag("StreamValidator").w("Content-Type não suportado: $contentType")
                ValidationResult.Invalid("Content-Type não suportado: $contentType")
            }
        }
    }
    
    /**
     * Verifica se um content-type é válido em modo permissivo
     * 
     * @param contentType Content-Type a verificar
     * @return true se o tipo é permitido em modo permissivo
     */
    private fun isValidPermissiveType(contentType: String): Boolean {
        // Permite qualquer tipo que comece com video/
        if (contentType.startsWith("video/")) return true
        
        // Permite tipos específicos na lista permissiva
        return permissiveContentTypes.any { 
            contentType.contains(it, ignoreCase = true) 
        }
    }
 
    /**
     * Resultado da validação de um stream
     */
    sealed class ValidationResult {
        /**
         * Stream válido - URL pode ser reproduzida
         * @property finalUrl URL final após redirecionamentos
         * @property contentType Content-Type do stream
         */
        data class Valid(val finalUrl: String, val contentType: String?) : ValidationResult()
        
        /**
         * Stream inválido - problema com URL ou Content-Type
         * @property reason Razão pela qual o stream é inválido
         */
        data class Invalid(val reason: String) : ValidationResult()
        
        /**
         * Erro durante validação - problema de conexão/exceção
         * @property message Mensagem do erro
         */
        data class Error(val message: String) : ValidationResult()
        
        /**
         * Verifica se o resultado indica sucesso
         * @return true se o resultado é Valid
         */
        fun isValid(): Boolean = this is Valid
    }
}
