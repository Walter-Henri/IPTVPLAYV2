# PROMPT DE EXECUÇÃO: PLUGIN HEADLESS (IPC/AIDL)

## Contexto e Objetivo

Transformar `com.m3u.extension` em um plugin headless que extrai streams de lives do YouTube via **NewPipe Extractor** e entrega os dados de reprodução ao host `com.m3u.android` através de **AIDL/Binder IPC**. A comunicação deve ser segura (verificação de assinatura), e o player (Media3/ExoPlayer ou MPV) deve reproduzir com os mesmos headers do handshake original, evitando erros 403.

---

## Dependências e Versões

```kotlin
// NewPipe Extractor (JitPack)
implementation("com.github.teamnewpipe:NewPipeExtractor:<LATEST_TAG>")
// Desugaring obrigatório se minSdk < 33
coreLibraryDesugaring("com.android.tools.build:desugaring:2.1.+")

// Media3 (ExoPlayer)
implementation("androidx.media3:media3-exoplayer:1.5.+")
implementation("androidx.media3:media3-exoplayer-hls:1.5.+")
implementation("androidx.media3:media3-datasource:1.5.+")

// Kotlin Coroutines (para chamadas AIDL não-bloqueantes no host)
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.+")
```

**ProGuard / R8 (obrigatório para NewPipe Extractor):**
```proguard
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
```

---

## 1. Plugin (`com.m3u.extension`)

### 1.1 AndroidManifest.xml

```xml
<manifest ...>
    <application
        android:label="M3U Extension"
        android:icon="@mipmap/ic_launcher">

        <!-- NÃO declare Activity com LAUNCHER/MAIN — app invisível no launcher -->

        <service
            android:name=".ExtractorService"
            android:exported="true"
            android:permission="com.m3u.permission.BIND_EXTRACTOR">
            <intent-filter>
                <action android:name="com.m3u.action.EXTRACTOR" />
            </intent-filter>
        </service>
    </application>

    <uses-permission android:name="android.permission.INTERNET" />
</manifest>
```

> **Nota:** `android:permission` força o host a declarar `<uses-permission>` com o mesmo nome, impedindo binds não autorizados — primeira linha de defesa antes da verificação de assinatura.

---

### 1.2 Inicialização do NewPipe Extractor

Crie `ExtractorApplication.kt` ou inicialize em `ExtractorService.onCreate()`:

```kotlin
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader

class ExtractorService : Service() {

    override fun onCreate() {
        super.onCreate()
        // Inicialização obrigatória antes de qualquer chamada ao extractor
        NewPipe.init(OkHttpDownloaderImpl())
    }
    // ...
}
```

Implemente `OkHttpDownloaderImpl` estendendo `Downloader` do NewPipe para interceptar e capturar cookies, User-Agent e headers de resposta do handshake.

---

### 1.3 Engine de Extração com Bypass 403 (poToken/Visitor Data)

A versão atual do NewPipe Extractor suporta passagem de **poToken** e **visitorData** para bypasear as verificações de integridade do YouTube (necessário no cliente web). Use as APIs atualizadas:

```kotlin
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

/**
 * Extrai a URL M3U8 de um live do YouTube.
 * @param youtubeUrl URL completa do live (ex: https://www.youtube.com/watch?v=XXXX)
 * @return ExtractionData com m3u8Url, userAgent, cookies e headers capturados
 */
suspend fun extractLiveStream(youtubeUrl: String): ExtractionData {
    return withContext(Dispatchers.IO) {
        // (Opcional) Injetar poToken e visitorData se disponíveis
        // YoutubeParsingHelper.setPoTokenAndVisitorData(poToken, visitorData)

        val streamInfo = StreamInfo.getInfo(
            NewPipe.getService(ServiceList.YouTube.serviceId),
            youtubeUrl
        )

        // Para lives: usar HLS stream (getDashMpdUrl() pode ser nulo em lives)
        val hlsUrl = streamInfo.hlsUrl
            ?: streamInfo.videoStreams
                .firstOrNull { it.isVideoOnly.not() }
                ?.content
            ?: throw ExtractionException("Nenhuma URL de stream disponível")

        // Coletar headers capturados pelo OkHttpDownloaderImpl durante o handshake
        val capturedHeaders = HeaderInterceptor.lastCapturedHeaders
        ExtractionData(
            m3u8Url    = hlsUrl,
            userAgent  = capturedHeaders["User-Agent"] ?: DEFAULT_USER_AGENT,
            cookies    = capturedHeaders["Cookie"] ?: "",
            headers    = capturedHeaders
        )
    }
}
```

**Estratégia de captura de headers:**
- Implemente um interceptor no `OkHttpDownloaderImpl` que armazena os headers da **última requisição bem-sucedida** ao `googlevideo.com` (onde o 403 costuma ocorrer).
- Armazene: `Cookie`, `User-Agent`, `Origin`, `Referer`, `X-Goog-Visitor-Id`.

---

### 1.4 Implementação do AIDL Stub

```kotlin
class ExtractorService : Service() {

    private val binder = object : IExtractorService.Stub() {

        override fun extractStream(
            youtubeUrl: String,
            callback: IExtractionCallback
        ) {
            // AIDL: chamadas de entrada NÃO são na main thread — usar coroutine/thread pool
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val data = extractLiveStream(youtubeUrl)
                    callback.onSuccess(data)
                } catch (e: Exception) {
                    callback.onError(e.message ?: "Erro desconhecido")
                }
            }
        }
    }

    override fun onBind(intent: Intent): IBinder = binder
}
```

---

## 2. Contrato AIDL (`módulo :common` compartilhado entre plugin e host)

### 2.1 `ExtractionData.aidl`

```aidl
// com/m3u/common/ExtractionData.aidl
package com.m3u.common;

// Declaração necessária para AIDL reconhecer o Parcelable
parcelable ExtractionData;
```

### 2.2 `ExtractionData.kt` (Parcelable — Android 10+: pode ser declarado direto no AIDL)

```kotlin
package com.m3u.common

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ExtractionData(
    val m3u8Url   : String,
    val userAgent : String,
    val cookies   : String,
    // Map não é suportado diretamente em AIDL; serializar como JSON String
    val headersJson: String = "{}"
) : Parcelable
```

> **Atenção:** `Map` genérico não é estável via Binder. Serializar headers como JSON `String` e deserializar no host com Gson/Moshi/kotlinx.serialization.

### 2.3 `IExtractionCallback.aidl` (callback assíncrono)

```aidl
// com/m3u/common/IExtractionCallback.aidl
package com.m3u.common;

import com.m3u.common.ExtractionData;

oneway interface IExtractionCallback {
    void onSuccess(in ExtractionData data);
    void onError(String message);
}
```

### 2.4 `IExtractorService.aidl`

```aidl
// com/m3u/common/IExtractorService.aidl
package com.m3u.common;

import com.m3u.common.ExtractionData;
import com.m3u.common.IExtractionCallback;

interface IExtractorService {
    /**
     * Extrai o stream de um live do YouTube de forma assíncrona.
     * Resultado entregue via IExtractionCallback.
     */
    void extractStream(
        in String youtubeUrl,
        in IExtractionCallback callback
    );

    /** Retorna a versão do contrato para compatibilidade futura. */
    int getVersion();
}
```

---

## 3. Integração no Host (`com.m3u.android`)

### 3.1 Declaração no Manifest do Host

```xml
<!-- Permissão para poder bindar o serviço do plugin -->
<uses-permission android:name="com.m3u.permission.BIND_EXTRACTOR" />
```

### 3.2 Discovery Dinâmico via PackageManager

```kotlin
fun discoverPlugin(context: Context): ComponentName? {
    val intent = Intent("com.m3u.action.EXTRACTOR")
    val flags = if (Build.VERSION.SDK_INT >= 33)
        PackageManager.ResolveInfoFlags.of(PackageManager.GET_SERVICES.toLong())
    else
        0

    val resolved = if (Build.VERSION.SDK_INT >= 33)
        context.packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
    else
        @Suppress("DEPRECATION")
        context.packageManager.queryIntentServices(intent, 0)

    return resolved.firstOrNull()?.serviceInfo?.let {
        ComponentName(it.packageName, it.name)
    }
}
```

### 3.3 Verificação de Assinatura (Security)

```kotlin
fun isPluginSignatureTrusted(context: Context, pluginPackage: String): Boolean {
    return try {
        val pm = context.packageManager
        val pluginSignatures = if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageInfo(pluginPackage, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo
                .apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pluginPackage, PackageManager.GET_SIGNATURES)
                .signatures
        }

        val trustedFingerprint = BuildConfig.TRUSTED_PLUGIN_CERT_SHA256
            // Exemplo: "CB:84:06:9B:D6:81:..." (SHA-256 do certificado do plugin)

        pluginSignatures.any { sig ->
            val digest = MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
            digest.toHexString() == trustedFingerprint.replace(":", "").lowercase()
        }
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}

// Chamar antes do bindService
val pluginComponent = discoverPlugin(context) ?: return
require(isPluginSignatureTrusted(context, pluginComponent.packageName)) {
    "Plugin não confiável — assinatura rejeitada"
}
```

> Defina `TRUSTED_PLUGIN_CERT_SHA256` no `BuildConfig` via `buildConfigField` no `build.gradle` do host.

### 3.4 Bind ao Serviço com ServiceConnection

```kotlin
class ExtractorRepository(private val context: Context) {

    private var extractorService: IExtractorService? = null
    private val bindLock = Mutex()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            extractorService = IExtractorService.Stub.asInterface(binder)
        }
        override fun onServiceDisconnected(name: ComponentName) {
            extractorService = null
        }
    }

    suspend fun bind() = withContext(Dispatchers.Main) {
        val component = discoverPlugin(context) ?: throw Exception("Plugin não encontrado")
        val intent = Intent().apply { component(component) }
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    suspend fun extractStream(youtubeUrl: String): ExtractionData =
        suspendCancellableCoroutine { cont ->
            val callback = object : IExtractionCallback.Stub() {
                override fun onSuccess(data: ExtractionData) = cont.resume(data)
                override fun onError(message: String) =
                    cont.resumeWithException(ExtractionException(message))
            }
            extractorService?.extractStream(youtubeUrl, callback)
                ?: cont.resumeWithException(IllegalStateException("Serviço não conectado"))
        }
}
```

---

## 4. Setup do Player (Anti-403)

### 4.1 Media3 / ExoPlayer (HLS com headers injetados)

```kotlin
fun buildExoPlayerWithHeaders(
    context: Context,
    data: ExtractionData
): ExoPlayer {
    val headers = buildMap {
        put("User-Agent", data.userAgent)
        if (data.cookies.isNotEmpty()) put("Cookie", data.cookies)
        // Adicionar headers adicionais deserializados de data.headersJson
        Json.decodeFromString<Map<String, String>>(data.headersJson).forEach { (k, v) ->
            put(k, v)
        }
    }

    val dataSourceFactory = DefaultHttpDataSource.Factory()
        .setDefaultRequestProperties(headers)
        .setUserAgent(data.userAgent)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS)
        .setReadTimeoutMs(DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS)

    val mediaSourceFactory = DefaultMediaSourceFactory(context)
        .setDataSourceFactory(dataSourceFactory)

    val mediaItem = MediaItem.Builder()
        .setUri(data.m3u8Url)
        .setMimeType(MimeTypes.APPLICATION_M3U8) // forçar HLS
        .build()

    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(mediaSourceFactory)
        .build()
        .also { it.setMediaItem(mediaItem) }
}
```

> **Nota:** Use `HlsMediaSource.Factory(dataSourceFactory)` diretamente se precisar de controle granular sobre o parsing HLS.

### 4.2 MPV (flags de headers)

```kotlin
fun buildMpvArgs(data: ExtractionData): List<String> {
    val args = mutableListOf<String>()

    // User-Agent
    args += "--user-agent=${data.userAgent}"

    // Cookies
    if (data.cookies.isNotEmpty()) {
        args += "--http-header-fields=Cookie: ${data.cookies}"
    }

    // Headers adicionais
    val extraHeaders = Json.decodeFromString<Map<String, String>>(data.headersJson)
    extraHeaders.forEach { (key, value) ->
        // Excluir User-Agent e Cookie (já tratados acima)
        if (key !in listOf("User-Agent", "Cookie")) {
            args += "--http-header-fields=$key: $value"
        }
    }

    args += data.m3u8Url
    return args
}

// Uso:
// mpvLib.command(buildMpvArgs(data).toTypedArray())
```

---

## 5. Estrutura de Módulos Sugerida

```
root/
├── app/                    → com.m3u.android (host)
├── extension/              → com.m3u.extension (plugin headless)
└── common/                 → módulo Android Library compartilhado
    └── src/main/
        ├── aidl/com/m3u/common/
        │   ├── ExtractionData.aidl
        │   ├── IExtractorService.aidl
        │   └── IExtractionCallback.aidl
        └── kotlin/com/m3u/common/
            └── ExtractionData.kt
```

Ambos `app/` e `extension/` devem depender de `common/` via `implementation(project(":common"))`.

---

## 6. Checklist de Segurança e Boas Práticas

- [ ] `android:permission` no `<service>` do plugin (controle de bind)
- [ ] Verificação de assinatura SHA-256 no host antes do `bindService`
- [ ] Fingerprint do certificado em `BuildConfig` (nunca hardcoded em código aberto)
- [ ] `oneway` nos callbacks AIDL (assíncrono, evita deadlock)
- [ ] Tratamento de `RemoteException` em todo acesso ao `IExtractorService`
- [ ] `context.unbindService(connection)` no `onDestroy` / `onStop` do host
- [ ] O plugin não deve ter `<activity>` com `LAUNCHER` — invisível ao usuário
- [ ] Chamadas AIDL **nunca** na Main Thread (usar Coroutines ou HandlerThread)
- [ ] ProGuard rules para NewPipe Extractor ativas no build de release
- [ ] `minSdk` do plugin ≥ 21; usar `coreLibraryDesugaring` se minSdk < 33

---

## 7. Verificação de Documentação Atualizada das Dependências

> **Instrução para a IDE / Agente:** Antes de gerar ou revisar qualquer código deste projeto, execute obrigatoriamente os passos abaixo para cada dependência. O objetivo é garantir que nenhuma API depreciada seja usada e que todos os exemplos de código estejam alinhados com a versão mais recente de cada biblioteca.

---

### 7.1 Mapa de Fontes de Documentação

| Dependência | Fonte canônica de documentação | O que verificar |
|---|---|---|
| **NewPipe Extractor** | https://github.com/TeamNewPipe/NewPipeExtractor | Changelog, `StreamInfo` API, `YoutubeParsingHelper`, suporte a `poToken`/`visitorData`, método `getInfo()`, classe `Downloader` |
| **Media3 / ExoPlayer** | https://developer.android.com/media/media3 · https://developer.android.com/reference/androidx/media3/exoplayer/ExoPlayer | `DefaultHttpDataSource.Factory`, `DefaultMediaSourceFactory`, `HlsMediaSource`, `MediaItem.Builder`, `setMimeType` |
| **Android AIDL / Binder** | https://developer.android.com/guide/components/aidl | Suporte a tipos, `oneway`, `Parcelable` no AIDL, `ServiceConnection` |
| **PackageManager (API 33+)** | https://developer.android.com/reference/android/content/pm/PackageManager | `ResolveInfoFlags`, `queryIntentServices`, `GET_SIGNING_CERTIFICATES` |
| **Kotlin Coroutines** | https://kotlinlang.org/docs/coroutines-guide.html | `suspendCancellableCoroutine`, `withContext`, `Dispatchers.IO` |
| **kotlinx-parcelize** | https://kotlinlang.org/docs/whatsnew.html | Plugin `kotlin-parcelize`, `@Parcelize` em `data class` |
| **OkHttp** (usado no Downloader) | https://square.github.io/okhttp/ | `Interceptor`, `Headers`, `Request`, `Response` |
| **MPV-Android** | https://github.com/mpv-android/mpv-android | Flags `--http-header-fields`, `--user-agent`, método de invocação de comandos |

---

### 7.2 Protocolo de Consulta Obrigatória

Antes de escrever qualquer linha de código que envolva uma das dependências acima, execute:

**Passo 1 — Verificar a versão mais recente publicada**
```
Para cada dependência, consulte:
- Maven Central / JitPack: versão estável mais recente
- GitHub Releases: tag de release mais recente e CHANGELOG
- Google Maven (para Jetpack/Media3): https://maven.google.com/web/index.html
```

**Passo 2 — Identificar APIs depreciadas no escopo deste projeto**

Verifique se as seguintes chamadas ainda são válidas na versão atual e, se não, substitua pela alternativa indicada na documentação:

```
NewPipe Extractor:
  ✦ StreamInfo.getInfo(service, url)          → confirmar assinatura atual
  ✦ streamInfo.hlsUrl                         → confirmar getter atual
  ✦ YoutubeParsingHelper.setPoTokenAndVisitorData() → confirmar existência e assinatura

Media3:
  ✦ DefaultHttpDataSource.Factory()           → confirmar construtores não depreciados
  ✦ .setDefaultRequestProperties(Map)         → confirmar se ainda aceita Map<String,String>
  ✦ DefaultMediaSourceFactory(context)        → confirmar construtor atual
  ✦ MediaItem.Builder().setMimeType()         → confirmar se MimeTypes.APPLICATION_M3U8 ainda existe

PackageManager:
  ✦ GET_SIGNING_CERTIFICATES                  → confirmar flag em API 28+
  ✦ signingInfo.apkContentsSigners            → confirmar em API 28+
  ✦ ResolveInfoFlags.of()                     → confirmar em API 33+

AIDL / Binder:
  ✦ Parcelable via @Parcelize                 → confirmar compatibilidade com AIDL stub gerado
  ✦ IExtractionCallback.Stub()               → confirmar padrão de callback oneway
```

**Passo 3 — Consultar migration guides se versão major mudou**
```
Media3:  https://developer.android.com/media/media3/exoplayer/migration-guide
NewPipe: https://github.com/TeamNewPipe/NewPipeExtractor/releases
OkHttp:  https://square.github.io/okhttp/changelogs/changelog/
```

**Passo 4 — Confirmar ProGuard rules**
```
Após identificar a versão exata do NewPipe Extractor em uso, verifique se
as regras ProGuard indicadas na Seção "Dependências e Versões" ainda são
suficientes ou se novas classes precisam ser preservadas.
Fonte: README do NewPipe Extractor + R8 full mode considerations.
```

---

### 7.3 Regra de Geração de Código

> Ao gerar qualquer trecho de código deste projeto, aplique as seguintes restrições:

1. **Nunca usar uma API marcada como `@Deprecated`** na versão atual da dependência — sempre substituir pela alternativa indicada no Javadoc/KDoc.
2. **Nunca assumir que uma API existe** sem confirmar na documentação canônica listada em 7.1.
3. **Sempre anotar com `@Suppress("DEPRECATION")`** apenas quando a API depreciada for necessária para compatibilidade com SDK antigo (ex.: `GET_SIGNATURES` em API < 28), com comentário explicando o motivo.
4. **Se uma API consultada não for encontrada** na versão mais recente, reportar explicitamente antes de gerar o código e propor a alternativa mais próxima documentada.
5. **Versões com `+` (ex.: `1.5.+`)** devem ser resolvidas para a versão exata disponível no repositório antes de qualquer geração de código, para evitar comportamento imprevisível de resolução de dependências.
