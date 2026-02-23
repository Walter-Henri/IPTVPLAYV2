# 🔧 REPLICAÇÃO COMPLETA — IPTVPLAYV2
### Documento técnico para IDE ANTIGRAVITY
> **Repositório:** `https://github.com/Walter-Henri/IPTVPLAYV2`  
> **Objetivo:** Corrigir erros **404**, **403** e **falhas de compilação do GitHub Actions** nos links M3U8 do YouTube  
> **Última atualização:** Fevereiro 2026 — Build v3 (correção de compilação aplicada)

---

## 📋 ÍNDICE

1. [Diagnóstico dos Problemas](#1-diagnóstico-dos-problemas)
2. [Arquitetura da Solução](#2-arquitetura-da-solução)
3. [Arquivo 1 — NOVO: `YouTubeWebViewTokenManager.kt`](#3-arquivo-1--novo-youtubewebviewtokenmanagerkt)
4. [Arquivo 2 — SUBSTITUIR: `YouTubeExtractorV2.kt`](#4-arquivo-2--substituir-youtubeextractorv2kt)
5. [Arquivo 3 — SUBSTITUIR: `extractor_v2.py`](#5-arquivo-3--substituir-extractor_v2py)
6. [APK Signing — Keystore e GitHub Secrets](#6-apk-signing--keystore-e-github-secrets)
7. [Checklist de Implantação](#7-checklist-de-implantação)
8. [Fluxo de Dados Completo](#8-fluxo-de-dados-completo)
9. [Cache e Validade dos Tokens](#9-cache-e-validade-dos-tokens)
10. [Troubleshooting](#10-troubleshooting)

---

> ## ⚡ ATENÇÃO — CORREÇÃO DE BUILD (GitHub Actions)
>
> **Data da falha:** 2026-02-23 — commit `f171275a38f5481761b79fd1985c32d054412694`  
> **Módulo que falhou:** `:app:m3u-extension:compileReleaseKotlin`  
> **Arquivo corrigido neste documento:** Seção 4 contém o `YouTubeExtractorV2.kt` **já atualizado**.
>
> ### Erros que ocorreram e como foram resolvidos:
>
> | # | Arquivo | Linha | Erro | Correção aplicada |
> |---|---|---|---|---|
> | 1 | `YouTubeExtractorV2.kt` | 211 | `Suspend function 'retryWithFreshTokens' should be called only from a coroutine` | `extractWithPython` mudou de `private fun` para **`private suspend fun`** |
> | 2 | `YouTubeExtractorV2.kt` | — | `Unresolved reference 'clearOldCache'` chamado de `ExtensionService.kt:58` | Método **`clearOldCache()`** adicionado à classe |
> | 3 | `YouTubeExtractorV2.kt` | — | `Unresolved reference 'generateReport'` chamado de `ExtensionService.kt:182` e `MainActivity.kt:289` | Método **`generateReport(): String`** adicionado à classe |
> | 4 | `YouTubeExtractorV2.kt` | — | `Unresolved reference 'saveReport'` chamado de `ExtensionService.kt:183` | Método **`saveReport(ctx: Context)`** adicionado à classe |
> | 5 | `YouTubeExtractorV2.kt` | — | `Unresolved reference 'getQuickSummary'` chamado de `ExtensionService.kt:192` | Método **`getQuickSummary(): String`** adicionado à classe |
>
> **Ação necessária:** substituir apenas `YouTubeExtractorV2.kt` pelo código da Seção 4.  
> `ExtensionService.kt` e `MainActivity.kt` **não precisam ser alterados** — eles já fazem as chamadas corretas.

---

## 1. Diagnóstico dos Problemas

### Problema 1 — Erro 404 (origem)
Os links M3U8 do YouTube são **signed URLs** — assinadas para o par `IP + User-Agent` usados no momento da extração. O ExoPlayer usava o UA real do dispositivo Android, mas o extrator Python usava um UA fixo hardcoded (`Mozilla/5.0 (Windows NT 10.0...)`). Incompatibilidade de UA → YouTube rejeitava com 404.

### Problema 2 — Erro 403 (subsequente)
Após corrigir o UA, surgiu o 403. Causa: desde meados de 2024, o YouTube exige o **PO Token (Proof of Origin Token)** para autenticar qualquer requisição de stream HLS. Sem ele, 100% das requisições retornam 403 independente do User-Agent ou cookies.

O PO Token é gerado internamente pelo `botguard` do Google dentro de um contexto de browser real. O WebView do Android fornece exatamente esse contexto.

### Problema 3 — Falha de compilação (GitHub Actions)
Ao adicionar chamadas de diagnóstico em `ExtensionService.kt` e `MainActivity.kt`, os métodos correspondentes não foram implementados em `YouTubeExtractorV2.kt`. Além disso, `extractWithPython` não era `suspend` mas chamava `retryWithFreshTokens` que é `suspend`. Resultado: 6 erros de compilação, build falhou.

---

## 2. Arquitetura da Solução

```
┌─────────────────────────────────────────────────────────────┐
│                    FLUXO COMPLETO                           │
│                                                             │
│  App chama extractChannel(name, url)                        │
│           │                                                 │
│           ▼                                                 │
│  YouTubeExtractorV2                                         │
│    • Verifica cache de stream (TTL: 5h)                     │
│           │ cache miss                                      │
│           ▼                                                 │
│  YouTubeWebViewTokenManager                                 │
│    • Abre WebView OCULTO em background                      │
│    • Navega para youtube.com                                │
│    • Aguarda JS do YouTube carregar (~3s)                   │
│    • Injeta FORCE_PO_TOKEN_JS → dispara fetch InnerTube     │
│    • Aguarda resposta do fetch (~2.5s)                      │
│    • Injeta EXTRACT_TOKENS_JS → coleta ytcfg interno        │
│    • Injeta COLLECT_PO_TOKEN_JS → coleta PO Token           │
│    • CookieManager.getCookie() → cookies de sessão          │
│    • Cache por 45 minutos                                   │
│           │                                                 │
│           ▼                                                 │
│  Kotlin monta JSON + converte cookies para Netscape         │
│    • input: { channels, tokens: { UA, visitorData, ... } }  │
│           │                                                 │
│           ▼                                                 │
│  extractor_v2.py chamado via Chaquopy                       │
│    • Com PO Token → player_client: web                      │
│    • Com visitorData → player_client: tv_embedded / ios     │
│    • Sem tokens → tv_embedded / ios / android_embedded      │
│    • yt-dlp extrai URL M3U8                                 │
│    • Valida com HEAD request                                │
│    • Se 403/404 → retry com tokens frescos automático       │
│           │                                                 │
│           ▼                                                 │
│  ExoPlayer recebe M3U8 + headers idênticos → ✅ OK          │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. Arquivo 1 — NOVO: `YouTubeWebViewTokenManager.kt`

**Ação:** Criar arquivo **novo** (não existia antes)  
**Caminho no repositório:**
```
app/m3u-extension/src/main/java/com/m3u/extension/youtube/YouTubeWebViewTokenManager.kt
```

```kotlin
package com.m3u.extension.youtube

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/**
 * YouTubeWebViewTokenManager
 *
 * Usa um WebView oculto para extrair automaticamente:
 *  - User-Agent real do dispositivo
 *  - Cookies de sessão do YouTube
 *  - visitor_data (necessário para streams autenticados)
 *  - PO Token (Proof of Origin Token) via JavaScript do botguard
 *
 * USO:
 *   val manager = YouTubeWebViewTokenManager(context)
 *   val tokens = manager.fetchTokens()
 */
class YouTubeWebViewTokenManager(private val context: Context) {

    companion object {
        private const val TAG = "YTWebViewTokenManager"
        private const val YOUTUBE_URL = "https://www.youtube.com"
        private const val WEBVIEW_TIMEOUT_MS = 20_000L
        private const val PAGE_SETTLE_DELAY_MS = 3_000L

        private val EXTRACT_TOKENS_JS = """
            (function() {
                try {
                    var result = {};
                    result.userAgent = navigator.userAgent;
                    if (window.ytcfg && window.ytcfg.get) {
                        result.visitorData      = window.ytcfg.get('VISITOR_DATA')     || '';
                        result.visitorInfoLive  = window.ytcfg.get('VISITOR_INFO1_LIVE')|| '';
                        result.clientVersion    = window.ytcfg.get('INNERTUBE_CLIENT_VERSION') || '';
                        result.clientName       = window.ytcfg.get('INNERTUBE_CLIENT_NAME') || '';
                        result.apiKey           = window.ytcfg.get('INNERTUBE_API_KEY') || '';
                        result.hl               = window.ytcfg.get('HL') || 'pt';
                        result.gl               = window.ytcfg.get('GL') || 'BR';
                    }
                    if (window.botguard && window.botguard.invoke) {
                        try {
                            window.botguard.invoke(function(token) {
                                result.poToken = token || '';
                            });
                        } catch(e) {}
                    }
                    if (!result.poToken && window.yt && window.yt.config_ && window.yt.config_.WEB_PLAYER_CONTEXT_CONFIGS) {
                        try {
                            var configs = window.yt.config_.WEB_PLAYER_CONTEXT_CONFIGS;
                            var firstKey = Object.keys(configs)[0];
                            if (firstKey && configs[firstKey].poToken) {
                                result.poToken = configs[firstKey].poToken;
                            }
                        } catch(e) {}
                    }
                    try {
                        result.sessionToken = localStorage.getItem('yt-player-session-token') || '';
                    } catch(e) {}
                    return JSON.stringify(result);
                } catch(e) {
                    return JSON.stringify({ error: e.toString() });
                }
            })();
        """.trimIndent()

        private val FORCE_PO_TOKEN_JS = """
            (function() {
                try {
                    if (window.ytcfg) {
                        var visitorData = window.ytcfg.get('VISITOR_DATA') || '';
                        fetch('https://www.youtube.com/youtubei/v1/player?prettyPrint=false', {
                            method: 'POST',
                            headers: {
                                'Content-Type': 'application/json',
                                'X-Goog-Visitor-Id': visitorData,
                                'X-YouTube-Client-Name': '1',
                                'X-YouTube-Client-Version': window.ytcfg.get('INNERTUBE_CLIENT_VERSION') || '2.20240101.00.00'
                            },
                            body: JSON.stringify({
                                context: {
                                    client: {
                                        clientName: 'WEB',
                                        clientVersion: window.ytcfg.get('INNERTUBE_CLIENT_VERSION') || '2.20240101.00.00',
                                        visitorData: visitorData
                                    }
                                },
                                videoId: 'dQw4w9WgXcQ'
                            })
                        })
                        .then(r => r.json())
                        .then(data => {
                            if (data && data.serviceTrackingParams) {
                                window._ytPoToken = data.serviceTrackingParams
                                    .find(p => p.service === 'GFEEDBACK')
                                    ?.params?.find(p => p.key === 'token')?.value || '';
                            }
                        })
                        .catch(e => {});
                    }
                    return 'triggered';
                } catch(e) {
                    return 'error: ' + e.toString();
                }
            })();
        """.trimIndent()

        private val COLLECT_PO_TOKEN_JS = """
            (function() {
                return JSON.stringify({
                    poToken: window._ytPoToken || '',
                    visitorData: window.ytcfg ? (window.ytcfg.get('VISITOR_DATA') || '') : ''
                });
            })();
        """.trimIndent()
    }

    data class YouTubeTokens(
        val userAgent: String,
        val cookies: String,
        val visitorData: String,
        val visitorInfoLive: String,
        val poToken: String,
        val clientVersion: String,
        val apiKey: String,
        val hl: String,
        val gl: String
    ) {
        val hasPoToken: Boolean get() = poToken.isNotBlank()
        val hasCookies: Boolean get() = cookies.isNotBlank()
        val hasVisitorData: Boolean get() = visitorData.isNotBlank()

        override fun toString(): String = buildString {
            appendLine("=== YouTubeTokens ===")
            appendLine("UA: ${userAgent.take(60)}...")
            appendLine("Cookies: ${if (hasCookies) "${cookies.length} chars" else "AUSENTE"}")
            appendLine("visitorData: ${if (hasVisitorData) "${visitorData.take(20)}..." else "AUSENTE"}")
            appendLine("poToken: ${if (hasPoToken) "${poToken.take(20)}..." else "AUSENTE"}")
            appendLine("clientVersion: $clientVersion")
            appendLine("hl/gl: $hl/$gl")
        }
    }

    suspend fun fetchTokens(forceRefresh: Boolean = false): YouTubeTokens {
        if (!forceRefresh) {
            getCachedTokens()?.let {
                Log.d(TAG, "✓ Usando tokens em cache")
                return it
            }
        }

        Log.d(TAG, "Iniciando captura de tokens via WebView...")
        val deferred = CompletableDeferred<YouTubeTokens>()

        withContext(Dispatchers.Main) {
            startWebViewCapture(deferred)
        }

        return try {
            withTimeout(WEBVIEW_TIMEOUT_MS + 5_000L) {
                deferred.await()
            }.also { tokens ->
                Log.d(TAG, "Tokens capturados:\n$tokens")
                cacheTokens(tokens)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Timeout ou erro na captura de tokens: ${e.message}")
            fallbackTokens()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun startWebViewCapture(deferred: CompletableDeferred<YouTubeTokens>) {
        val handler = Handler(Looper.getMainLooper())

        try {
            val webView = WebView(context)
            val cookieManager = CookieManager.getInstance()

            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, true)

            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                useWideViewPort = true
                loadWithOverviewMode = true
            }

            webView.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(msg: ConsoleMessage?): Boolean = true
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, pageUrl: String?) {
                    Log.d(TAG, "Página carregada: $pageUrl")

                    handler.postDelayed({
                        if (deferred.isCompleted) return@postDelayed

                        view?.evaluateJavascript(FORCE_PO_TOKEN_JS) { _ ->
                            handler.postDelayed({
                                if (deferred.isCompleted) return@postDelayed

                                view?.evaluateJavascript(EXTRACT_TOKENS_JS) { rawResult ->
                                    view?.evaluateJavascript(COLLECT_PO_TOKEN_JS) { rawPoToken ->

                                        val userAgent = WebSettings.getDefaultUserAgent(context)
                                        val cookies = cookieManager.getCookie(YOUTUBE_URL) ?: ""

                                        val tokens = parseTokenResults(
                                            rawResult = rawResult,
                                            rawPoToken = rawPoToken,
                                            userAgent = userAgent,
                                            cookies = cookies
                                        )

                                        if (!deferred.isCompleted) {
                                            deferred.complete(tokens)
                                            view?.destroy()
                                        }
                                    }
                                }
                            }, 2_500L)
                        }
                    }, PAGE_SETTLE_DELAY_MS)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        Log.w(TAG, "Erro na página principal: ${error?.description}")
                    }
                }
            }

            webView.loadUrl(YOUTUBE_URL)

            handler.postDelayed({
                if (!deferred.isCompleted) {
                    Log.w(TAG, "Timeout forçado - coletando o que tiver")
                    val userAgent = WebSettings.getDefaultUserAgent(context)
                    val cookies = cookieManager.getCookie(YOUTUBE_URL) ?: ""

                    webView.evaluateJavascript(EXTRACT_TOKENS_JS) { rawResult ->
                        val tokens = parseTokenResults(
                            rawResult = rawResult,
                            rawPoToken = "null",
                            userAgent = userAgent,
                            cookies = cookies
                        )
                        if (!deferred.isCompleted) {
                            deferred.complete(tokens)
                            webView.destroy()
                        }
                    }
                }
            }, WEBVIEW_TIMEOUT_MS)

        } catch (e: Exception) {
            Log.e(TAG, "Erro crítico no WebView: ${e.message}", e)
            if (!deferred.isCompleted) {
                deferred.complete(fallbackTokens())
            }
        }
    }

    private fun parseTokenResults(
        rawResult: String?,
        rawPoToken: String?,
        userAgent: String,
        cookies: String
    ): YouTubeTokens {
        var visitorData = ""
        var visitorInfoLive = ""
        var poToken = ""
        var clientVersion = ""
        var apiKey = ""
        var hl = "pt"
        var gl = "BR"

        rawResult?.let { raw ->
            val cleaned = raw.trim().removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")
            try {
                val json = JSONObject(
                    if (cleaned.startsWith("{")) cleaned
                    else raw.trim().let {
                        it.substring(1, it.length - 1)
                            .replace("\\\"", "\"")
                            .replace("\\n", "\n")
                    }
                )
                visitorData     = json.optString("visitorData")
                visitorInfoLive = json.optString("visitorInfoLive")
                clientVersion   = json.optString("clientVersion")
                apiKey          = json.optString("apiKey")
                hl              = json.optString("hl").ifBlank { "pt" }
                gl              = json.optString("gl").ifBlank { "BR" }
                poToken         = json.optString("poToken")
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao parsear resultado JS: ${e.message}")
            }
        }

        rawPoToken?.let { raw ->
            try {
                val cleaned = raw.trim().removeSurrounding("\"").replace("\\\"", "\"")
                val json = JSONObject(if (cleaned.startsWith("{")) cleaned else "{}")
                val fetchedPoToken = json.optString("poToken")
                if (fetchedPoToken.isNotBlank()) poToken = fetchedPoToken
                val fetchedVisitorData = json.optString("visitorData")
                if (fetchedVisitorData.isNotBlank() && visitorData.isBlank()) {
                    visitorData = fetchedVisitorData
                }
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao parsear PO Token coletado: ${e.message}")
            }
        }

        Log.d(TAG, "  visitorData: ${if (visitorData.isNotBlank()) "✓" else "✗"}")
        Log.d(TAG, "  poToken: ${if (poToken.isNotBlank()) "✓" else "✗"}")
        Log.d(TAG, "  cookies: ${if (cookies.isNotBlank()) "✓" else "✗"}")

        return YouTubeTokens(
            userAgent = userAgent,
            cookies = cookies,
            visitorData = visitorData,
            visitorInfoLive = visitorInfoLive,
            poToken = poToken,
            clientVersion = clientVersion,
            apiKey = apiKey,
            hl = hl,
            gl = gl
        )
    }

    private val CACHE_TTL_MS = 45 * 60 * 1000L
    private val cacheFile by lazy {
        java.io.File(context.cacheDir, "yt_webview_tokens.json")
    }

    private fun cacheTokens(tokens: YouTubeTokens) {
        try {
            val json = JSONObject().apply {
                put("userAgent", tokens.userAgent)
                put("cookies", tokens.cookies)
                put("visitorData", tokens.visitorData)
                put("visitorInfoLive", tokens.visitorInfoLive)
                put("poToken", tokens.poToken)
                put("clientVersion", tokens.clientVersion)
                put("apiKey", tokens.apiKey)
                put("hl", tokens.hl)
                put("gl", tokens.gl)
                put("timestamp", System.currentTimeMillis())
            }
            cacheFile.writeText(json.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao salvar tokens em cache: ${e.message}")
        }
    }

    private fun getCachedTokens(): YouTubeTokens? {
        if (!cacheFile.exists()) return null
        return try {
            val json = JSONObject(cacheFile.readText())
            val age = System.currentTimeMillis() - json.getLong("timestamp")
            if (age > CACHE_TTL_MS) return null
            YouTubeTokens(
                userAgent = json.getString("userAgent"),
                cookies = json.getString("cookies"),
                visitorData = json.getString("visitorData"),
                visitorInfoLive = json.optString("visitorInfoLive"),
                poToken = json.getString("poToken"),
                clientVersion = json.optString("clientVersion"),
                apiKey = json.optString("apiKey"),
                hl = json.optString("hl", "pt"),
                gl = json.optString("gl", "BR")
            )
        } catch (e: Exception) { null }
    }

    fun clearCache() {
        cacheFile.delete()
        Log.d(TAG, "Cache de tokens limpo")
    }

    private fun fallbackTokens() = YouTubeTokens(
        userAgent = WebSettings.getDefaultUserAgent(context),
        cookies = CookieManager.getInstance().getCookie(YOUTUBE_URL) ?: "",
        visitorData = "", visitorInfoLive = "", poToken = "",
        clientVersion = "", apiKey = "", hl = "pt", gl = "BR"
    )
}
```

---

## 4. Arquivo 2 — SUBSTITUIR: `YouTubeExtractorV2.kt`

**Ação:** Substituir TODO o conteúdo do arquivo existente  
**Caminho no repositório:**
```
app/m3u-extension/src/main/java/com/m3u/extension/youtube/YouTubeExtractorV2.kt
```

> ⚠️ O nome da classe permanece `YouTubeExtractorV2` — não renomear.

### 🔴 Diff resumido em relação à versão anterior

```diff
- private fun extractWithPython(          // ERRO: não era suspend
+ private suspend fun extractWithPython(  // FIX: agora é suspend

  // 4 métodos novos adicionados ao final da classe:
+ fun clearOldCache()                     // chamado por ExtensionService.kt:58
+ fun generateReport(): String            // chamado por ExtensionService.kt:182 e MainActivity.kt:289
+ fun saveReport(ctx: Context)            // chamado por ExtensionService.kt:183
+ fun getQuickSummary(): String           // chamado por ExtensionService.kt:192
```

### Código completo (copiar integralmente):

```kotlin
package com.m3u.extension.youtube

import android.content.Context
import android.util.Log
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * YouTubeExtractorV2
 *
 * Versão integrada com YouTubeWebViewTokenManager.
 *
 * A cada extração:
 * 1. WebView oculto navega para youtube.com
 * 2. JavaScript extrai: cookies, visitor_data, PO Token, UA real
 * 3. Tokens passados para extractor_v2.py via Chaquopy
 * 4. yt-dlp usa os mesmos tokens que o YouTube espera → sem 403
 *
 * BUILD FIX (fev/2026):
 *  - extractWithPython é agora `suspend` para poder chamar retryWithFreshTokens
 *  - Adicionados: clearOldCache, generateReport, saveReport, getQuickSummary
 */
class YouTubeExtractorV2(private val context: Context) {

    companion object {
        private const val TAG = "YouTubeExtractorV2"
        private const val STREAM_CACHE_TTL_MS = 5 * 60 * 60 * 1000L  // 5 h
        private const val TOKEN_CACHE_TTL_MS  = 45 * 60 * 1000L       // 45 min
    }

    private val python: Python by lazy { Python.getInstance() }
    private val tokenManager by lazy { YouTubeWebViewTokenManager(context) }
    private val cacheDir: File by lazy {
        File(context.cacheDir, "yt_streams_v3").apply { mkdirs() }
    }

    data class ExtractionResult(
        val success: Boolean,
        val m3u8Url: String?,
        val headers: Map<String, String>,
        val method: String?,
        val error: String?
    )

    // ──────────────────────────────────────────────────────────────
    // MÉTODO PRINCIPAL
    // ──────────────────────────────────────────────────────────────

    suspend fun extractChannel(
        name: String,
        url: String,
        logo: String? = null,
        group: String? = null,
        forceRefresh: Boolean = false
    ): ExtractionResult = withContext(Dispatchers.IO) {

        Log.d(TAG, "\n════════════════════════════════")
        Log.d(TAG, "Extraindo: $name | URL: $url")

        if (!forceRefresh) {
            getCachedStream(url)?.let {
                Log.d(TAG, "✓ Stream em cache válido")
                return@withContext it
            }
        }

        Log.d(TAG, "Capturando tokens via WebView...")
        val tokens = withContext(Dispatchers.Main) {
            tokenManager.fetchTokens(forceRefresh)
        }

        Log.d(TAG, "  UA: ${tokens.userAgent.take(60)}...")
        Log.d(TAG, "  Cookies: ${if (tokens.hasCookies) "✓ ${tokens.cookies.length} chars" else "✗"}")
        Log.d(TAG, "  visitorData: ${if (tokens.hasVisitorData) "✓" else "✗"}")
        Log.d(TAG, "  poToken: ${if (tokens.hasPoToken) "✓" else "✗"}")

        val result = extractWithPython(name, url, logo, group, tokens)

        if (result.success) cacheStream(url, result)

        result
    }

    // ──────────────────────────────────────────────────────────────
    // EXTRAÇÃO PYTHON
    // FIX BUILD: `suspend` para poder chamar retryWithFreshTokens
    // ──────────────────────────────────────────────────────────────

    private suspend fun extractWithPython(
        name: String,
        url: String,
        logo: String?,
        group: String?,
        tokens: YouTubeWebViewTokenManager.YouTubeTokens
    ): ExtractionResult {

        val ts = System.currentTimeMillis()
        val inputFile   = File(cacheDir, "input_$ts.json")
        val outputFile  = File(cacheDir, "output_$ts.json")
        val cookiesFile = File(cacheDir, "cookies_$ts.txt")

        try {
            val inputJson = JSONObject().apply {
                put("channels", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("name", name)
                        put("url", url)
                        put("logo", logo ?: "")
                        put("group", group ?: "YouTube")
                    })
                })
                put("tokens", JSONObject().apply {
                    put("userAgent",       tokens.userAgent)
                    put("visitorData",     tokens.visitorData)
                    put("visitorInfoLive", tokens.visitorInfoLive)
                    put("poToken",         tokens.poToken)
                    put("clientVersion",   tokens.clientVersion)
                    put("apiKey",          tokens.apiKey)
                    put("hl",              tokens.hl)
                    put("gl",              tokens.gl)
                })
            }
            inputFile.writeText(inputJson.toString())

            if (tokens.hasCookies) {
                cookiesFile.writeText(buildNetscapeCookies(tokens.cookies))
            }

            val module = python.getModule("extractor_v2")
            module.callAttr(
                "extract",
                inputFile.absolutePath,
                outputFile.absolutePath,
                if (tokens.hasCookies) cookiesFile.absolutePath else null,
                tokens.userAgent
            )

            if (!outputFile.exists()) {
                return ExtractionResult(false, null, emptyMap(), null,
                    "Python não gerou arquivo de saída")
            }

            val outputJson = JSONObject(outputFile.readText())
            val channels = outputJson.getJSONArray("channels")

            if (channels.length() == 0) {
                return ExtractionResult(false, null, emptyMap(), null, "Sem resultados")
            }

            val ch = channels.getJSONObject(0)
            if (!ch.getBoolean("success")) {
                return ExtractionResult(
                    success = false, m3u8Url = null, headers = emptyMap(),
                    method = null, error = ch.optString("error", "Falha desconhecida")
                )
            }

            val headers = mutableMapOf<String, String>()
            ch.optJSONObject("headers")?.let { h ->
                h.keys().forEach { key -> headers[key] = h.getString(key) }
            }

            if (!headers.containsKey("User-Agent")) headers["User-Agent"] = tokens.userAgent
            if (!headers.containsKey("Referer"))    headers["Referer"]    = "https://www.youtube.com/"
            if (!headers.containsKey("Origin"))     headers["Origin"]     = "https://www.youtube.com"
            if (tokens.hasCookies && !headers.containsKey("Cookie")) {
                headers["Cookie"] = tokens.cookies
            }

            val m3u8   = ch.getString("m3u8")
            val method = ch.optString("extraction_method", "unknown")

            Log.d(TAG, "✅ Extração OK! Método: $method | M3U8: ${m3u8.take(60)}...")

            val valid = validateStream(m3u8, headers)
            if (!valid) {
                Log.w(TAG, "⚠ Stream inválido — tokens desatualizados, tentando novamente...")
                // retryWithFreshTokens é suspend → extractWithPython precisa ser suspend também
                return retryWithFreshTokens(name, url, logo, group)
            }

            return ExtractionResult(
                success = true, m3u8Url = m3u8, headers = headers,
                method = method, error = null
            )

        } catch (e: Exception) {
            Log.e(TAG, "Erro Python: ${e.message}", e)
            return ExtractionResult(false, null, emptyMap(), null, e.message)
        } finally {
            inputFile.delete()
            outputFile.delete()
            cookiesFile.delete()
        }
    }

    private suspend fun retryWithFreshTokens(
        name: String, url: String, logo: String?, group: String?
    ): ExtractionResult = withContext(Dispatchers.Main) {
        Log.d(TAG, "↩ Recapturando tokens frescos...")
        tokenManager.clearCache()
        val freshTokens = tokenManager.fetchTokens(forceRefresh = true)
        withContext(Dispatchers.IO) {
            extractWithPython(name, url, logo, group, freshTokens)
        }
    }

    // ──────────────────────────────────────────────────────────────
    // VALIDAÇÃO
    // ──────────────────────────────────────────────────────────────

    private fun validateStream(url: String, headers: Map<String, String>): Boolean {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()

            val req = Request.Builder().url(url).head()
            headers.forEach { (k, v) ->
                try { req.header(k, v) } catch (_: Exception) { }
            }

            val resp = client.newCall(req.build()).execute()
            val code = resp.code
            resp.close()

            Log.d(TAG, "Validação HTTP $code")
            code in 200..399 || code == 206

        } catch (e: Exception) {
            Log.w(TAG, "Erro na validação: ${e.message}")
            url.contains("googlevideo.com") || url.contains(".m3u8")
        }
    }

    // ──────────────────────────────────────────────────────────────
    // CACHE DE STREAM
    // ──────────────────────────────────────────────────────────────

    private fun cacheStream(url: String, result: ExtractionResult) {
        val file = File(cacheDir, "stream_${url.hashCode()}.json")
        try {
            file.writeText(JSONObject().apply {
                put("success",   result.success)
                put("m3u8Url",   result.m3u8Url ?: "")
                put("headers",   JSONObject(result.headers as Map<*, *>))
                put("method",    result.method ?: "")
                put("error",     result.error ?: "")
                put("timestamp", System.currentTimeMillis())
            }.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao salvar cache de stream: ${e.message}")
        }
    }

    private fun getCachedStream(url: String): ExtractionResult? {
        val file = File(cacheDir, "stream_${url.hashCode()}.json")
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            val age  = System.currentTimeMillis() - json.getLong("timestamp")
            if (age > STREAM_CACHE_TTL_MS) return null

            val headers = mutableMapOf<String, String>()
            json.optJSONObject("headers")?.let { h ->
                h.keys().forEach { k -> headers[k] = h.getString(k) }
            }

            ExtractionResult(
                success  = json.getBoolean("success"),
                m3u8Url  = json.optString("m3u8Url").takeIf { it.isNotEmpty() },
                headers  = headers,
                method   = json.optString("method"),
                error    = json.optString("error").takeIf { it.isNotEmpty() }
            )
        } catch (e: Exception) { null }
    }

    // ──────────────────────────────────────────────────────────────
    // LIMPEZA DE CACHE
    // FIX BUILD: clearOldCache() adicionado — chamado por ExtensionService.kt:58
    // ──────────────────────────────────────────────────────────────

    /**
     * Remove apenas os streams expirados do cache de arquivos.
     * Chamado periodicamente pelo ExtensionService.
     */
    fun clearOldCache() {
        val now = System.currentTimeMillis()
        var removidos = 0
        cacheDir.listFiles()?.forEach { file ->
            if (!file.name.startsWith("stream_")) return@forEach
            try {
                val age = now - JSONObject(file.readText()).getLong("timestamp")
                if (age > STREAM_CACHE_TTL_MS) { file.delete(); removidos++ }
            } catch (e: Exception) { file.delete(); removidos++ }
        }
        Log.d(TAG, "clearOldCache: $removidos arquivo(s) removido(s)")
    }

    /** Remove todo o cache: streams + tokens WebView. */
    fun clearAllCache() {
        tokenManager.clearCache()
        cacheDir.listFiles()?.forEach { it.delete() }
        Log.d(TAG, "Cache completo limpo")
    }

    // ──────────────────────────────────────────────────────────────
    // RELATÓRIO / DIAGNÓSTICO
    // FIX BUILD: 3 métodos adicionados
    //   generateReport → ExtensionService.kt:182 + MainActivity.kt:289
    //   saveReport     → ExtensionService.kt:183
    //   getQuickSummary→ ExtensionService.kt:192
    // ──────────────────────────────────────────────────────────────

    /**
     * Gera relatório completo do estado atual do extrator.
     * Retorna string multi-linha com token status, streams em cache, timestamps.
     */
    fun generateReport(): String {
        val now         = System.currentTimeMillis()
        val allFiles    = cacheDir.listFiles() ?: emptyArray()
        val streamFiles = allFiles.filter { it.name.startsWith("stream_") }
        val activeStreams = streamFiles.count { f ->
            try {
                val age = now - JSONObject(f.readText()).getLong("timestamp")
                age <= STREAM_CACHE_TTL_MS
            } catch (e: Exception) { false }
        }

        val tokenFile   = File(context.cacheDir, "yt_webview_tokens.json")
        val tokenStatus = if (tokenFile.exists()) {
            try {
                val age = now - JSONObject(tokenFile.readText()).getLong("timestamp")
                if (age <= TOKEN_CACHE_TTL_MS) {
                    val minLeft = ((TOKEN_CACHE_TTL_MS - age) / 60_000).toInt()
                    "✅ Válido (~$minLeft min restantes)"
                } else "⚠️ Expirado"
            } catch (e: Exception) { "⚠️ Inválido" }
        } else "❌ Não capturado"

        val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

        return buildString {
            appendLine("══════════════════════════════════")
            appendLine("  YouTubeExtractorV2 — Relatório")
            appendLine("══════════════════════════════════")
            appendLine("Data/Hora   : ${fmt.format(Date(now))}")
            appendLine("PO Token    : $tokenStatus")
            appendLine("Streams     : $activeStreams ativos / ${streamFiles.size} total em cache")
            appendLine("Cache Dir   : ${cacheDir.absolutePath}")
            appendLine("Dir tamanho : ${cacheDir.listFiles()?.size ?: 0} arquivos")
            appendLine("══════════════════════════════════")
        }
    }

    /**
     * Salva o relatório gerado por generateReport() em arquivo.
     * Salva em: context.cacheDir/youtube_extractor_report.txt
     */
    fun saveReport(ctx: Context) {
        try {
            val outFile = File(ctx.cacheDir, "youtube_extractor_report.txt")
            outFile.writeText(generateReport())
            Log.d(TAG, "Relatório salvo em: ${outFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao salvar relatório: ${e.message}")
        }
    }

    /**
     * Resumo de uma linha: "Token:OK | Streams ativos:3/5"
     * Usado em logs rápidos e na UI (MainActivity).
     */
    fun getQuickSummary(): String {
        val now         = System.currentTimeMillis()
        val streamFiles = cacheDir.listFiles()
            ?.filter { it.name.startsWith("stream_") } ?: emptyList()
        val active = streamFiles.count { f ->
            try {
                val age = now - JSONObject(f.readText()).getLong("timestamp")
                age <= STREAM_CACHE_TTL_MS
            } catch (e: Exception) { false }
        }
        val tokenFile = File(context.cacheDir, "yt_webview_tokens.json")
        val tokenOk   = tokenFile.exists() && try {
            val age = now - JSONObject(tokenFile.readText()).getLong("timestamp")
            age <= TOKEN_CACHE_TTL_MS
        } catch (e: Exception) { false }

        return "Token:${if (tokenOk) "OK" else "NO"} | Streams ativos:$active/${streamFiles.size}"
    }

    // ──────────────────────────────────────────────────────────────
    // HELPERS
    // ──────────────────────────────────────────────────────────────

    /**
     * Converte cookies do CookieManager para formato Netscape.
     * Formato: domain TAB subdomain TAB path TAB secure TAB expiry TAB name TAB value
     */
    private fun buildNetscapeCookies(rawCookies: String): String {
        val sb = StringBuilder("# Netscape HTTP Cookie File\n")
        sb.appendLine("# Gerado automaticamente pelo YouTubeExtractorV2")
        sb.appendLine()
        rawCookies.split(";").forEach { pair ->
            val trimmed = pair.trim()
            val eqIdx   = trimmed.indexOf('=')
            if (eqIdx < 0) return@forEach
            val name  = trimmed.substring(0, eqIdx).trim()
            val value = trimmed.substring(eqIdx + 1).trim()
            sb.appendLine(".youtube.com\tTRUE\t/\tTRUE\t2147483647\t$name\t$value")
        }
        return sb.toString()
    }
}
```

---

## 5. Arquivo 3 — SUBSTITUIR: `extractor_v2.py`

**Ação:** Substituir TODO o conteúdo do arquivo existente  
**Caminho no repositório:**
```
app/m3u-extension/src/main/python/extractor_v2.py
```

```python
"""
YouTube HLS Stream Extractor - Integrado com WebView Android

Recebe tokens extraídos automaticamente pelo YouTubeWebViewTokenManager:
- User-Agent real do dispositivo
- Cookies de sessão (.youtube.com)
- visitor_data (do ytcfg interno do YouTube)
- PO Token (Proof of Origin Token via botguard)
- clientVersion, apiKey, hl, gl

Com esses tokens, o yt-dlp autentica como se fosse o browser real → sem 403.
"""

import json
import sys
import time
import os
import tempfile
from urllib.parse import urlparse
import http.client
import ssl

print("=== EXTRACTOR V2 (WebView Tokens) ===", file=sys.stderr)

try:
    import yt_dlp
    print(f"✓ yt_dlp: {yt_dlp.version.__version__}", file=sys.stderr)
except ImportError:
    print("❌ yt_dlp não encontrado!", file=sys.stderr)
    sys.exit(1)


def build_opts_with_webview_tokens(tokens: dict, cookies_file: str = None) -> dict:
    ua = tokens.get("userAgent", "")
    visitor_data = tokens.get("visitorData", "")
    po_token = tokens.get("poToken", "")
    client_version = tokens.get("clientVersion", "")
    hl = tokens.get("hl", "pt")

    has_po_token = bool(po_token and po_token.strip())
    has_visitor_data = bool(visitor_data and visitor_data.strip())

    print(f"Tokens recebidos do WebView:", file=sys.stderr)
    print(f"  UA: {ua[:60]}...", file=sys.stderr)
    print(f"  visitorData: {'✓' if has_visitor_data else '✗'}", file=sys.stderr)
    print(f"  poToken: {'✓' if has_po_token else '✗'}", file=sys.stderr)
    print(f"  clientVersion: {client_version or '?'}", file=sys.stderr)

    if has_po_token:
        player_clients = ["web"]
        print("→ player_client: web (com PO Token)", file=sys.stderr)
    elif has_visitor_data:
        player_clients = ["tv_embedded", "ios"]
        print("→ player_client: tv_embedded/ios (com visitorData)", file=sys.stderr)
    else:
        player_clients = ["tv_embedded", "ios", "android_embedded"]
        print("→ player_client: tv_embedded/ios/android (sem tokens)", file=sys.stderr)

    opts = {
        "quiet": True,
        "no_warnings": True,
        "format": "best[protocol^=m3u8]/best",
        "socket_timeout": 30,
        "nocheckcertificate": True,
        "geo_bypass": True,
        "user_agent": ua,
        "http_headers": {
            "User-Agent": ua,
            "Accept": "*/*",
            "Accept-Language": f"{hl},{hl[:2]};q=0.9,en-US;q=0.7",
            "Referer": "https://www.youtube.com/",
            "Origin": "https://www.youtube.com",
        },
        "extractor_args": {
            "youtube": {
                "player_client": player_clients,
                "skip": ["dash"],
            }
        },
        "noplaylist": True,
    }

    if has_po_token:
        opts["extractor_args"]["youtube"]["po_token"] = [f"web+{po_token}"]
    if has_visitor_data:
        opts["extractor_args"]["youtube"]["visitor_data"] = [visitor_data]
    if cookies_file and os.path.exists(cookies_file):
        opts["cookiefile"] = cookies_file
        print(f"  cookies: ✓", file=sys.stderr)

    return opts


def extract_best_hls(info: dict) -> tuple:
    manifest = info.get("hls_manifest_url") or info.get("hlsManifestUrl")
    if manifest:
        return manifest, "hls_manifest"

    formats = info.get("formats", [])
    hls = [
        f for f in formats
        if f.get("protocol") in ("m3u8", "m3u8_native")
        or ".m3u8" in f.get("url", "")
    ]

    if hls:
        hls.sort(key=lambda f: (f.get("height") or 0, f.get("tbr") or 0), reverse=True)
        combined = [f for f in hls if f.get("acodec") != "none" and f.get("vcodec") != "none"]
        best = (combined or hls)[0]
        return best["url"], "hls_format"

    return info.get("url", ""), "direct"


def validate_url(url: str, headers: dict, timeout: int = 10) -> tuple:
    if not url:
        return False, 0
    try:
        parsed = urlparse(url)
        ctx = ssl._create_unverified_context()
        conn = http.client.HTTPSConnection(parsed.netloc, timeout=timeout, context=ctx)
        path = parsed.path + ("?" + parsed.query if parsed.query else "")
        conn.request("HEAD", path, headers=headers)
        resp = conn.getresponse()
        conn.close()
        ok = resp.status in (200, 206, 301, 302, 307, 308)
        print(f"  Validação HTTP {resp.status} → {'✓' if ok else '✗'}", file=sys.stderr)
        return ok, resp.status
    except Exception as e:
        print(f"  Erro na validação: {e}", file=sys.stderr)
        return False, 0


def extract_channel(channel: dict, tokens: dict, cookies_file: str = None) -> dict:
    url = channel.get("url", "")
    name = channel.get("name", url)

    print(f"\n{'─'*50}", file=sys.stderr)
    print(f"Canal: {name}", file=sys.stderr)

    if not url:
        return _fail(channel, name, "URL vazia")

    ua = tokens.get("userAgent", "Mozilla/5.0")
    attempts = []

    # Tentativa 1: tokens do WebView
    print(f"\n[1/2] Usando tokens do WebView Android...", file=sys.stderr)
    try:
        t0 = time.time()
        opts = build_opts_with_webview_tokens(tokens, cookies_file)

        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=False)

        m3u8_url, url_type = extract_best_hls(info)
        ms = int((time.time() - t0) * 1000)

        if m3u8_url:
            headers = {
                "User-Agent": ua,
                "Referer": "https://www.youtube.com/",
                "Origin": "https://www.youtube.com",
            }
            cookies_raw = tokens.get("cookies", "")
            if cookies_raw:
                headers["Cookie"] = cookies_raw

            ok, status = validate_url(m3u8_url, headers)
            attempts.append({"method": "webview_tokens", "ok": ok, "status": status, "ms": ms})

            if ok:
                print(f"✅ SUCESSO com tokens WebView ({url_type}, {ms}ms)", file=sys.stderr)
                return _ok(channel, name, m3u8_url, headers, "webview_tokens", url_type, attempts)
            else:
                print(f"✗ HTTP {status} — tokens podem ter expirado", file=sys.stderr)

    except Exception as e:
        ms = int((time.time() - t0) * 1000)
        print(f"✗ Falha: {str(e)[:120]}", file=sys.stderr)
        attempts.append({"method": "webview_tokens", "ok": False, "error": str(e)[:120], "ms": ms})

    # Tentativa 2: fallback tv_embedded
    print(f"\n[2/2] Fallback: tv_embedded sem tokens...", file=sys.stderr)
    try:
        t0 = time.time()
        fallback_ua = (
            "Mozilla/5.0 (SMART-TV; Linux; Tizen 6.0) "
            "AppleWebKit/537.36 (KHTML, like Gecko) "
            "SamsungBrowser/4.0 Chrome/76.0.3809.146 TV Safari/537.36"
        )
        opts = {
            "quiet": True, "no_warnings": True,
            "format": "best[protocol^=m3u8]/best",
            "socket_timeout": 30, "nocheckcertificate": True,
            "http_headers": {
                "User-Agent": fallback_ua,
                "Referer": "https://www.youtube.com/",
            },
            "extractor_args": {
                "youtube": {"player_client": ["tv_embedded"], "skip": ["dash"]}
            },
            "noplaylist": True,
        }
        if cookies_file and os.path.exists(cookies_file):
            opts["cookiefile"] = cookies_file

        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=False)

        m3u8_url, url_type = extract_best_hls(info)
        ms = int((time.time() - t0) * 1000)

        if m3u8_url:
            headers = {"User-Agent": fallback_ua, "Referer": "https://www.youtube.com/"}
            ok, status = validate_url(m3u8_url, headers)
            attempts.append({"method": "tv_embedded_fallback", "ok": ok, "status": status, "ms": ms})

            if ok:
                print(f"✅ SUCESSO com tv_embedded fallback ({ms}ms)", file=sys.stderr)
                return _ok(channel, name, m3u8_url, headers, "tv_embedded_fallback", url_type, attempts)

    except Exception as e:
        print(f"✗ Fallback também falhou: {str(e)[:80]}", file=sys.stderr)
        attempts.append({"method": "tv_embedded_fallback", "ok": False, "error": str(e)[:80]})

    print(f"\n❌ FALHA TOTAL: {name}", file=sys.stderr)
    return _fail(channel, name, "Todos os métodos falharam", attempts)


def _ok(ch, name, url, headers, method, url_type, attempts):
    return {
        "name": name, "logo": ch.get("logo", ""), "group": ch.get("group", "YouTube"),
        "success": True, "m3u8": url, "headers": headers,
        "extraction_method": method, "url_type": url_type,
        "attempts": attempts, "error": None,
    }

def _fail(ch, name, error, attempts=None):
    return {
        "name": name, "logo": ch.get("logo", "") if ch else "",
        "group": ch.get("group", "YouTube") if ch else "YouTube",
        "success": False, "m3u8": None, "headers": {},
        "attempts": attempts or [], "error": error,
    }


def extract(input_path: str, output_path: str,
            cookies_file: str = None,
            device_user_agent: str = None) -> None:
    """
    Ponto de entrada chamado pelo YouTubeExtractorV2.kt via Chaquopy.

    input_path contém:
    {
        "channels": [...],
        "tokens": {
            "userAgent": "...",
            "visitorData": "...",
            "poToken": "...",
            ...
        }
    }
    """
    if not os.path.exists(input_path):
        print(f"❌ Arquivo não encontrado: {input_path}", file=sys.stderr)
        return

    with open(input_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    channels = data.get("channels", [])
    tokens = data.get("tokens", {})

    if device_user_agent and not tokens.get("userAgent"):
        tokens["userAgent"] = device_user_agent

    yt_channels = [
        ch for ch in channels
        if "youtube.com" in ch.get("url", "") or "youtu.be" in ch.get("url", "")
    ]

    print(f"\n{'='*50}", file=sys.stderr)
    print(f"EXTRAÇÃO: {len(yt_channels)} canais", file=sys.stderr)
    print(f"poToken: {'✓' if tokens.get('poToken') else '✗'}", file=sys.stderr)
    print(f"visitorData: {'✓' if tokens.get('visitorData') else '✗'}", file=sys.stderr)
    print(f"cookies: {'✓' if tokens.get('cookies') else '✗'}", file=sys.stderr)
    print(f"{'='*50}\n", file=sys.stderr)

    results = []
    success_count = 0

    for i, channel in enumerate(yt_channels, 1):
        print(f"[{i}/{len(yt_channels)}]", file=sys.stderr, end=" ")
        result = extract_channel(channel, tokens, cookies_file)
        results.append(result)
        if result.get("success"):
            success_count += 1

    output_data = {
        "channels": results,
        "stats": {
            "total": len(yt_channels),
            "success": success_count,
            "failed": len(yt_channels) - success_count,
            "timestamp": time.time(),
            "had_po_token": bool(tokens.get("poToken")),
            "had_visitor_data": bool(tokens.get("visitorData")),
        }
    }

    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(output_data, f, ensure_ascii=False, indent=2)

    print(f"\n{'='*50}", file=sys.stderr)
    print(f"CONCLUÍDO: {success_count}/{len(yt_channels)} ✅", file=sys.stderr)
    print(f"{'='*50}", file=sys.stderr)
```

---

## 6. APK Signing — Keystore e GitHub Secrets

### Keystore gerado

| Campo | Valor |
|---|---|
| Arquivo | `meu-app.keystore` |
| Formato | PKCS12 |
| Alias | `meu_alias` |
| Senha | `Wa97951211` |
| Algoritmo | RSA 2048-bit |
| Validade | 10.000 dias |
| SHA-256 | `83:91:21:69:62:A2:52:E3:9F:5A:11:F4:71:9A:82:C4:03:2B:83:11:32:A1:F2:3D:64:23:6A:64:54:8B:92:FA` |

### GitHub Secrets configurados

Caminho: `Repositório → Settings → Secrets and variables → Actions`

| Secret | Valor |
|---|---|
| `RELEASE_KEY_ALIAS` | `meu_alias` |
| `RELEASE_KEY_PASSWORD` | `Wa97951211` |
| `RELEASE_STORE_PASSWORD` | `Wa97951211` |
| `RELEASE_KEYSTORE_BASE64` | *(conteúdo completo do arquivo `meu-app.keystore.b64`, incluindo os `==` finais)* |

> ⚠️ O valor do `RELEASE_KEYSTORE_BASE64` deve ser copiado **integralmente** do arquivo `.b64`, incluindo os `==` do final. Sem eles o keystore fica corrompido.

---

## 7. Checklist de Implantação

```
ARQUIVOS NO REPOSITÓRIO:
[ ] Criar novo:    app/m3u-extension/src/main/java/com/m3u/extension/youtube/YouTubeWebViewTokenManager.kt
[ ] Substituir:    app/m3u-extension/src/main/java/com/m3u/extension/youtube/YouTubeExtractorV2.kt
[ ] Substituir:    app/m3u-extension/src/main/python/extractor_v2.py

⚠️  ExtensionService.kt e MainActivity.kt NÃO precisam ser alterados.
    O build falhava porque os métodos chamados não existiam em
    YouTubeExtractorV2.kt. A versão da Seção 4 já os implementa todos.

GITHUB SECRETS:
[ ] RELEASE_KEY_ALIAS         = meu_alias
[ ] RELEASE_KEY_PASSWORD      = Wa97951211
[ ] RELEASE_STORE_PASSWORD    = Wa97951211
[ ] RELEASE_KEYSTORE_BASE64   = (conteúdo do .b64 com == no final)

VERIFICAÇÃO PÓS-PUSH:
[ ] Build passa sem erros em :app:m3u-extension:compileReleaseKotlin
[ ] Limpar cache do app no dispositivo antes de testar
[ ] Verificar Logcat com tags: "YTWebViewTokenManager" e "YouTubeExtractorV2"
```

---

## 8. Fluxo de Dados Completo

```
1. App solicita canal YouTube
        │
        ▼
2. YouTubeExtractorV2.extractChannel()
   └─ Verifica cache de stream (TTL: 5h)
        │ cache miss
        ▼
3. YouTubeWebViewTokenManager.fetchTokens()
   └─ Verifica cache de tokens (TTL: 45min)
        │ cache miss
        ▼
4. WebView oculto abre youtube.com
   └─ Aguarda JS carregar (3s)
   └─ FORCE_PO_TOKEN_JS → fetch para InnerTube API
   └─ Aguarda resposta do fetch (2.5s)
   └─ EXTRACT_TOKENS_JS → coleta ytcfg
   └─ COLLECT_PO_TOKEN_JS → coleta PO Token
   └─ CookieManager.getCookie("youtube.com") → cookies
   └─ WebSettings.getDefaultUserAgent() → UA real
        │
        ▼
5. Tokens retornados ao Kotlin:
   { userAgent, cookies, visitorData, poToken, clientVersion, ... }
        │
        ▼
6. Kotlin monta input JSON + converte cookies para Netscape
        │
        ▼
7. extractor_v2.py via Chaquopy
   └─ Com PO Token → player_client: ["web"], po_token: "web+TOKEN"
   └─ Com visitorData → player_client: ["tv_embedded", "ios"]
   └─ Sem tokens → player_client: ["tv_embedded", "ios", "android_embedded"]
        │
        ▼
8. yt-dlp extrai M3U8
   └─ Prioridade: hls_manifest_url > m3u8_native > m3u8 > direct
        │
        ▼
9. Validação com HEAD request
   └─ HTTP 200/206/3xx → OK
   └─ HTTP 403/404 → retryWithFreshTokens() → volta ao passo 4
        │
        ▼
10. ExoPlayer recebe M3U8 + headers idênticos → reprodução ✅
```

---

## 9. Cache e Validade dos Tokens

| Dado | TTL | Armazenado em |
|---|---|---|
| Tokens WebView (UA, cookies, PO Token, visitorData) | **45 minutos** | `context.cacheDir/yt_webview_tokens.json` |
| Stream M3U8 + headers | **5 horas** | `context.cacheDir/yt_streams_v3/stream_HASH.json` |
| Relatório de diagnóstico (saveReport) | Sem TTL | `context.cacheDir/youtube_extractor_report.txt` |

Se a validação do stream falhar, o sistema **automaticamente limpa o cache de tokens e recaptura tudo** via WebView — sem intervenção do usuário.

---

## 10. Troubleshooting

### Logs para verificar no Logcat

```
tag:YTWebViewTokenManager   → captura de tokens
tag:YouTubeExtractorV2      → extração e validação
```

### Sinais de funcionamento correto
```
✓ Usando tokens em cache
Iniciando captura de tokens via WebView...
  visitorData: ✓
  poToken: ✓
  cookies: ✓ (XXX chars)
→ player_client: web (com PO Token)
✅ Extração OK! Método: webview_tokens
```

### Erros de compilação — status após correção
```
✅ CORRIGIDO: Suspend function 'retryWithFreshTokens' should be called only from a coroutine
✅ CORRIGIDO: Unresolved reference 'clearOldCache'
✅ CORRIGIDO: Unresolved reference 'generateReport'
✅ CORRIGIDO: Unresolved reference 'saveReport'
✅ CORRIGIDO: Unresolved reference 'getQuickSummary'
```

### Problemas comuns em runtime

| Sintoma | Causa | Solução |
|---|---|---|
| `poToken: ✗` sempre | YouTube não carregou JS completo | Aumentar `PAGE_SETTLE_DELAY_MS` de 3000 para 5000 ms |
| HTTP 403 persiste | Tokens expirados antes do cache | Reduzir `CACHE_TTL_MS` de 45min para 20min |
| WebView timeout | Conexão lenta no dispositivo | Aumentar `WEBVIEW_TIMEOUT_MS` de 20000 para 30000 ms |
| `Python não gerou saída` | yt-dlp não instalado / versão antiga | `pip install yt-dlp --upgrade` |
| `visitorData: ✗` | ytcfg não carregou | YouTube mudou estrutura JS — revisar `EXTRACT_TOKENS_JS` |
| Build falha com `compileReleaseKotlin` | Métodos ausentes ou suspend incorreto | Substituir `YouTubeExtractorV2.kt` pela versão da Seção 4 |
