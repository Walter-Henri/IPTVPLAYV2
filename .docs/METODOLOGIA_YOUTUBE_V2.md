# Metodologia Robusta para Extração de Streams YouTube HLS

## 🎯 Objetivo
Garantir extração e reprodução 100% confiável de streams M3U8 do YouTube, eliminando erros 403 e bloqueios.

## 🏗️ Arquitetura da Solução

### Camada 1: Extração Multi-Client (Python)
**Arquivo**: `extractor_v2.py`

#### Estratégia de Extração
```
┌─────────────────────────────────────────┐
│  Tentativa 1: yt-dlp (Android TV)      │
│  User-Agent: BRAVIA 4K                  │
└──────────────┬──────────────────────────┘
               │ Falhou?
               ↓
┌─────────────────────────────────────────┐
│  Tentativa 2: yt-dlp (Android App)     │
│  User-Agent: YouTube Android App        │
└──────────────┬──────────────────────────┘
               │ Falhou?
               ↓
┌─────────────────────────────────────────┐
│  Tentativa 3: yt-dlp (iOS)             │
│  User-Agent: iPhone Safari              │
└──────────────┬──────────────────────────┘
               │ Falhou?
               ↓
┌─────────────────────────────────────────┐
│  Tentativa 4: yt-dlp (Web)             │
│  User-Agent: Chrome Desktop             │
└──────────────┬──────────────────────────┘
               │ Falhou? (Fallback Crítico)
               ↓
┌─────────────────────────────────────────┐
│  Tentativa 5: Streamlink (Python API)  │
│  Melhor Qualidade (HLS/HTTP)            │
└──────────────┬──────────────────────────┘
               │ Sucesso!
               ↓
┌─────────────────────────────────────────┐
│  Validação do Stream                    │
│  - HEAD request (Python & Kotlin)       │
│  - Injeção de Headers Kodi em URL       │
└─────────────────────────────────────────┘
```

#### Configuração yt-dlp Otimizada
```python
ydl_opts = {
    'quiet': True,
    'no_warnings': True,
    'format': 'best[ext=mp4]/best',
    'socket_timeout': 20,
    'nocheckcertificate': True,
    'geo_bypass': True,
    'user_agent': USER_AGENT_DINAMICO,
    'http_headers': {
        'User-Agent': USER_AGENT_DINAMICO,
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        'Accept-Language': 'pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7',
        'Accept-Encoding': 'gzip, deflate',
        'DNT': '1',
        'Connection': 'keep-alive',
        'Upgrade-Insecure-Requests': '1'
    },
    'extractor_args': {
        'youtube': {
            'player_client': ['android', 'ios', 'web', 'tv_embedded'],
            'skip': ['dash', 'hls_manifest_video_only'],
            'player_skip': ['webpage', 'configs']
        }
    }
}
```

#### Motores de Extração
1. **yt-dlp**: Motor principal com rotação de User-Agents.
2. **Streamlink**: Motor de fallback poderoso quando o yt-dlp falha.
3. **Injeção Kodi**: Headers injetados na URL (`url|Header=Value`) para persistência entre processos (Extension -> Main App).

### Camada 2: Validação de Streams (Python)
**Função**: `validate_m3u8_url()`

#### Processo de Validação
```python
def validate_m3u8_url(url, headers=None):
    """
    1. Parse da URL
    2. Conexão HTTPS com SSL context
    3. HEAD request (rápido, sem download)
    4. Verificação de status code (200 ou 206)
    5. Verificação de Content-Type
    6. Validação de extensão .m3u8
    """
```

#### Critérios de Aceitação
- ✅ Status HTTP: 200 OK ou 206 Partial Content
- ✅ Content-Type: `application/vnd.apple.mpegurl` ou `application/x-mpegURL`
- ✅ URL termina com `.m3u8` ou contém `/manifest/`

### Camada 3: Wrapper Kotlin (Android)
**Arquivo**: `YouTubeExtractorV2.kt`

#### Funcionalidades
1. **Cache Inteligente**
   - Validade: 6 horas
   - Armazenamento: `context.cacheDir/yt_streams/`
   - Chave: Hash da URL original

2. **Validação Dupla**
   - Validação Python (durante extração)
   - Validação Kotlin (antes de usar)
   - HEAD request com OkHttp

3. **Integração com Sistema**
   ```kotlin
   suspend fun extractChannel(
       name: String,
       url: String,
       logo: String? = null,
       group: String? = null
   ): ExtractionResult
   ```

### Camada 4: Reprodução (PlayerManager)
**Integração com fluxo existente**

#### Headers Garantidos
```kotlin
val headers = mapOf(
    "User-Agent" to "Android TV UA usado na extração",
    "Referer" to "https://www.youtube.com/",
    "Origin" to "https://www.youtube.com"
)
```

## 🔧 Implementação

### Passo 1: Integrar no ExtensionService
```kotlin
class ExtensionService : Service() {
    
    private val extractorV2 by lazy { YouTubeExtractorV2(this) }
    
    private suspend fun processChannel(channel: ChannelData): Map<String, Any?> {
        val url = channel.url ?: return errorResult("URL vazia")
        
        // Usar novo extrator para YouTube
        if (url.contains("youtube.com") || url.contains("youtu.be")) {
            val result = extractorV2.extractChannel(
                name = channel.name ?: "Unknown",
                url = url,
                logo = channel.logo,
                group = channel.group
            )
            
            if (result.success && result.m3u8Url != null) {
                return mapOf(
                    "name" to channel.name,
                    "m3u8" to result.m3u8Url,
                    "headers" to result.headers,
                    "logo" to channel.logo,
                    "group" to channel.group,
                    "success" to true,
                    "extraction_method" to result.method
                )
            }
        }
        
        // Fallback para extrator antigo
        return processChannelLegacy(channel)
    }
}
```

### Passo 2: Garantir Headers no PlayerManager
```kotlin
// PlayerManagerImpl.kt - tryPlay()

// Prioridade absoluta para headers do Registry
val registryHeaders = JsonHeaderRegistry.getHeadersForUrl(sanitizedUrl)

if (registryHeaders != null) {
    timber.d("✓ Usando headers validados do YouTube")
    
    // Garantir headers críticos
    val finalHeaders = registryHeaders.toMutableMap().apply {
        if (!containsKey("Referer")) {
            put("Referer", "https://www.youtube.com/")
        }
        if (!containsKey("Origin")) {
            put("Origin", "https://www.youtube.com")
        }
    }
    
    // Usar esses headers na criação do DataSource
    createDataSourceWithHeaders(sanitizedUrl, finalHeaders)
}
```

## 📊 Fluxo Completo

```
┌──────────────────────────────────────────────────────────────┐
│  1. USUÁRIO IMPORTA LISTA                                    │
└────────────────┬─────────────────────────────────────────────┘
                 │
                 ↓
┌──────────────────────────────────────────────────────────────┐
│  2. ExtensionService detecta URL YouTube                     │
└────────────────┬─────────────────────────────────────────────┘
                 │
                 ↓
┌──────────────────────────────────────────────────────────────┐
│  3. YouTubeExtractorV2.extractChannel()                      │
│     - Verifica cache (6h)                                    │
│     - Se não, chama Python extractor_v2.py                   │
└────────────────┬─────────────────────────────────────────────┘
                 │
                 ↓
┌──────────────────────────────────────────────────────────────┐
│  4. extractor_v2.py - Tentativas Sequenciais                 │
│     ├─ Android TV UA                                         │
│     ├─ Android App UA                                        │
│     ├─ iOS UA                                                │
│     └─ Web UA                                                │
│     Para cada tentativa:                                     │
│       - Extrai com yt-dlp                                    │
│       - Valida stream (HEAD request)                         │
│       - Se válido, retorna                                   │
└────────────────┬─────────────────────────────────────────────┘
                 │
                 ↓
┌──────────────────────────────────────────────────────────────┐
│  5. YouTubeExtractorV2 recebe resultado                      │
│     - Valida novamente (Kotlin)                              │
│     - Cacheia se válido                                      │
│     - Retorna ExtractionResult                               │
└────────────────┬─────────────────────────────────────────────┘
                 │
                 ↓
┌──────────────────────────────────────────────────────────────┐
│  6. ExtensionService monta JSON                              │
│     {                                                         │
│       "m3u8": "https://manifest...|User-Agent=...&Referer=...",│
│       "headers": { ... },                                     │
│       "extraction_method": "yt-dlp (android_tv) / streamlink" │
│     }                                                         │
└────────────────┬─────────────────────────────────────────────┘
                 │
                 ↓
┌──────────────────────────────────────────────────────────────┐
│  7. PlaylistRepositoryImpl.importChannelsJsonBody()          │
│     - Registra headers no JsonHeaderRegistry                 │
│     - Salva canal no banco                                   │
└────────────────┬─────────────────────────────────────────────┘
                 │
                 ↓
┌──────────────────────────────────────────────────────────────┐
│  8. USUÁRIO CLICA PARA REPRODUZIR                            │
└────────────────┬─────────────────────────────────────────────┘
                 │
                 ↓
┌──────────────────────────────────────────────────────────────┐
│  9. PlayerManagerImpl.tryPlay()                              │
│     - Busca headers no JsonHeaderRegistry                    │
│     - Cria DataSource com headers corretos                   │
│     - Inicia reprodução                                      │
└────────────────┬─────────────────────────────────────────────┘
                 │
                 ↓
┌──────────────────────────────────────────────────────────────┐
│  10. ✅ REPRODUÇÃO BEM-SUCEDIDA                              │
└──────────────────────────────────────────────────────────────┘
```

## 🛡️ Garantias da Metodologia

### 1. Múltiplas Tentativas
- 4 User-Agents diferentes
- Cada um com configuração otimizada
- Fallback automático

### 2. Validação em Duas Camadas
- **Python**: Valida durante extração
- **Kotlin**: Valida antes de usar
- Garante que apenas streams funcionais sejam salvos

### 3. Cache Inteligente
- Evita re-extrações desnecessárias
- Validade de 6 horas
- Limpeza automática de cache antigo

### 4. Headers Consistentes
- Mesmo UA usado na extração e reprodução
- Referer e Origin sempre presentes
- Registrados no JsonHeaderRegistry

### 5. Logs Detalhados
- Cada etapa logada
- Fácil identificação de problemas
- Rastreamento completo do fluxo

## 🔍 Debugging

### Logs Python
```bash
adb logcat | grep "python.stderr"
```

Procurar por:
- `Processando: NOME_CANAL`
- `Tentativa android_tv...`
- `✓ M3U8 validado`
- `✅ SUCESSO com android_tv!`

### Logs Kotlin
```bash
adb logcat | grep "YouTubeExtractorV2"
```

Procurar por:
- `Extraindo: NOME_CANAL`
- `✓ Usando resultado em cache`
- `✓ Validação HEAD bem-sucedida`
- `✅ Stream validado e funcional`

### Logs PlayerManager
```bash
adb logcat | grep "PlayerManagerImpl"
```

Procurar por:
- `=== HEADER RESOLUTION DEBUG ===`
- `Dynamic headers from Registry: [User-Agent, Referer, Origin]`
- `✓ Using headers from JsonHeaderRegistry`

## 📈 Melhorias Futuras

1. **Proxy Rotation**: Adicionar suporte a proxies para bypass de geo-blocking
2. **Streamlink Integration**: Fallback adicional usando streamlink
3. **Direct Innertube API**: Acesso direto à API do YouTube
4. **Quality Selection**: Permitir escolha de qualidade do stream
5. **Live Stream Detection**: Otimizações específicas para streams ao vivo

## 🎓 Referências

- [yt-dlp Documentation](https://github.com/yt-dlp/yt-dlp)
- [YouTube Innertube API](https://github.com/iv-org/invidious/blob/master/docs/API.md)
- [HLS Protocol Specification](https://datatracker.ietf.org/doc/html/rfc8216)
- [ExoPlayer HLS Support](https://exoplayer.dev/hls.html)
