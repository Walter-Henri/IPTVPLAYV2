# YouTube M3U8 Extension - 100% Android

## 📋 Visão Geral

Esta extensão é **100% focada em Android** e responsável por extrair links M3U8 de transmissões ao vivo do YouTube usando **exclusivamente** o binário `yt-dlp` (Python pré-compilado).

### 🎯 Plataformas Suportadas
- ✅ **Smartphones Android** (API 26+)
- ✅ **Android TV**
- ✅ **Smart TVs Android**
- ✅ **TV Box Android**

### 🛠️ Stack Tecnológica
- **App Universal**: 100% Kotlin
- **App Extensão**: Kotlin + yt-dlp (binário Python pré-compilado)
- **Comunicação**: AIDL (Android Interface Definition Language)

## 🏗️ Arquitetura

```
┌─────────────────────────────────────────────────────────┐
│                   App Universal (Kotlin)                 │
│              (Android TV, Smartphones, Boxes)            │
└────────────────────┬────────────────────────────────────┘
                     │ AIDL (IExtension)
                     ▼
┌─────────────────────────────────────────────────────────┐
│              ExtensionService (Orquestrador)             │
│  - Recebe channels.json                                  │
│  - Processa canais em paralelo (semáforo: 3)            │
│  - Notifica progresso via callback                       │
│  - Retorna JSON com links M3U8 resolvidos               │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│          YtDlpInteractor (Camada de Negócio)            │
│  - Valida URLs de entrada                               │
│  - Delega execução ao ProcessRunner                     │
│  - Trata erros de forma amigável                        │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│        YtDlpProcessRunner (Executor de Processo)        │
│  - Localiza binário yt-dlp na raiz do projeto           │
│  - Garante permissões de execução (chmod +x)            │
│  - Executa: yt-dlp -g -f "format" "url"                 │
│  - Captura stdout e valida URLs                         │
│  - Prioriza URLs .m3u8                                  │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│           yt-dlp (Binário Python Pré-compilado)         │
│  Localização: /yt-dlp (raiz do projeto)                 │
│  Fonte: github.com/yt-dlp/yt-dlp/releases/latest        │
└─────────────────────────────────────────────────────────┘
```

## 📁 Estrutura de Arquivos

```
app/m3u-extension/
├── src/main/java/com/m3u/extension/
│   ├── ExtensionService.kt          # Orquestrador principal (AIDL)
│   ├── ExtensionApplication.kt      # Aplicação Android
│   ├── youtubedl/
│   │   ├── YtDlpInteractor.kt       # Camada de negócio
│   │   ├── YtDlpProcessRunner.kt    # Executor de processo
│   │   └── YtDlpConfig.kt           # (data class em YtDlpInteractor.kt)
│   ├── preferences/
│   │   └── ExtensionPreferences.kt  # Configurações (DataStore)
│   ├── dropbox/
│   │   └── DropboxRepository.kt     # Download de channels.json
│   ├── worker/
│   │   └── LinkExtractionWorker.kt  # WorkManager para sync periódico
│   └── ui/
│       └── MainActivity.kt          # Interface de configuração
└── build.gradle.kts                 # Dependências limpas (sem youtubedl-android)
```

## 🔧 Componentes Principais

### 1. **ExtensionService.kt**
- **Responsabilidade**: Orquestração e comunicação AIDL
- **Métodos AIDL**:
  - `resolve(url: String): String?` - Resolve uma única URL
  - `extractLinksAsync(jsonContent: String, callback: IExtensionCallback)` - Processa channels.json
  - `syncChannels(callback: IExtensionCallback)` - Baixa do Dropbox e processa
- **Concorrência**: Semáforo com limite de 3 execuções paralelas
- **Deduplicação**: Baseada em `name|url` (case-insensitive)

### 2. **YtDlpInteractor.kt**
- **Responsabilidade**: Validação e lógica de negócio
- **Validações**:
  - URL não vazia
  - Esquema HTTP/HTTPS válido
- **Delegação**: Toda execução é delegada ao `YtDlpProcessRunner`

### 3. **YtDlpProcessRunner.kt** (100% Android)
- **Responsabilidade**: Execução do binário yt-dlp em ambiente Android
- **Localização do Binário** (Android-first):
  1. `context.filesDir/yt-dlp` (armazenamento interno do app)
  2. `assets/yt-dlp` (empacotado no APK)
  3. `context.getExternalFilesDir(null)/yt-dlp` (storage externo)
- **Comando Executado**:
  ```bash
  /data/data/com.m3u.extension/files/yt-dlp -g -f "bestvideo+bestaudio/best" "<URL>"
  ```
- **Parsing de Saída** (otimizado para ExoPlayer):
  - Prioridade 1: URLs `.m3u8` (HLS - ideal para Android)
  - Prioridade 2: URLs `.mpd` (DASH)
  - Prioridade 3: Primeira URL HTTP/HTTPS válida
- **Timeout**: 45 segundos (adequado para conexões móveis)
- **Tratamento de Erros Android**:
  - Binário não encontrado → Tenta copiar de assets
  - Permissão negada → `chmod 755` via Runtime
  - Timeout → Processo destruído
  - Exit code != 0 → Erro com saída completa
  - Saída vazia → Mensagem específica

## 📊 Fluxo de Dados

### Entrada (channels.json)
```json
{
  "channels": [
    {
      "name": "Nome do Canal",
      "url": "https://youtube.com/@canal/live",
      "logo": "https://exemplo.com/logo.png",
      "group": "Categoria"
    }
  ]
}
```

### Saída (JSON com M3U8)
```json
{
  "channels": [
    {
      "name": "Nome do Canal",
      "group": "Categoria",
      "logo": "https://exemplo.com/logo.png",
      "m3u8": "https://manifest.googlevideo.com/.../index.m3u8"
    }
  ]
}
```

## 🔍 Tratamento de Erros

### Estratégia Geral
- **Falhas isoladas**: Um canal com falha NÃO interrompe o processamento dos demais
- **Logging detalhado**: Todos os erros são logados com contexto
- **Notificação de progresso**: O app Universal é notificado mesmo em caso de falha

### Tipos de Erro

| Erro | Causa | Ação |
|------|-------|------|
| Binário não encontrado | yt-dlp ausente na raiz | Falha com mensagem clara |
| Permissão negada | Sem permissão de execução | Tentativa de `chmod +x` |
| Timeout | Processo > 45s | Processo destruído, falha reportada |
| Exit code != 0 | yt-dlp falhou | Falha com código e saída |
| URL inválida | Formato incorreto | Canal ignorado, próximo processado |
| Saída vazia | yt-dlp não retornou URLs | Falha reportada |

## 🚀 Uso

### Resolução de URL Única
```kotlin
val service: IExtension = // bind ao ExtensionService
val m3u8Url = service.resolve("https://youtube.com/@canal/live")
```

### Processamento em Lote
```kotlin
val service: IExtension = // bind ao ExtensionService
val callback = object : IExtensionCallback.Stub() {
    override fun onProgress(current: Int, total: Int, channelName: String) {
        // Atualizar UI
    }
    
    override fun onResult(jsonResult: String) {
        // Processar resultado final
    }
    
    override fun onError(message: String) {
        // Tratar erro
    }
}

service.extractLinksAsync(channelsJson, callback)
```

## 🔐 Segurança

- **Validação de entrada**: Todas as URLs são validadas antes da execução
- **Timeout**: Processos longos são automaticamente terminados
- **Isolamento**: Falhas em um canal não afetam outros
- **Logging controlado**: Informações sensíveis não são logadas

## 📝 Configurações

As configurações são armazenadas via `DataStore`:

- **format**: Formato de vídeo/áudio (padrão: `"bestvideo+bestaudio/best"`)
- **userAgent**: User-Agent para requisições HTTP

## 🧪 Testes

### Testes Unitários
```bash
./gradlew :app:m3u-extension:testDebugUnitTest
```

### Testes de Integração
```bash
./gradlew :app:m3u-extension:connectedAndroidTest
```

## 📦 Build

### Debug
```bash
./gradlew :app:m3u-extension:assembleDebug
```

### Release
```bash
./gradlew :app:m3u-extension:assembleRelease
```

## 🐛 Debugging

### Logs
Todos os componentes usam tags específicas:
- `ExtensionService`: Orquestração
- `YtDlpInteractor`: Validação e negócio
- `YtDlpProcessRunner`: Execução de processo

### Filtro Logcat
```bash
adb logcat -s ExtensionService YtDlpInteractor YtDlpProcessRunner
```

## 📚 Dependências

### Removidas
- ❌ `youtubedl-android` (substituído por execução direta do binário)
- ❌ `youtubedl-android-ffmpeg`

### Mantidas
- ✅ `okhttp` (requisições HTTP)
- ✅ `gson` (parsing JSON)
- ✅ `kotlinx-coroutines` (concorrência)
- ✅ `androidx.datastore` (preferências)
- ✅ `androidx.work` (sync periódico)
- ✅ `dropbox-core-sdk` (download de channels.json)

## 🎯 Decisões Técnicas

### Por que remover `youtubedl-android`?
1. **Instabilidade**: Biblioteca com falhas recorrentes
2. **Controle**: Execução direta oferece mais controle
3. **Simplicidade**: Menos camadas de abstração
4. **Manutenibilidade**: Código mais fácil de debugar

### Por que remover `LinkUtils.kt`?
1. **Fonte única**: yt-dlp deve ser a única fonte de extração
2. **Fragilidade**: Lógica manual de scraping é frágil
3. **Redundância**: yt-dlp já resolve channel URLs

### Por que concorrência limitada (3)?
1. **Estabilidade**: Evita sobrecarga do dispositivo
2. **Rate limiting**: Evita bloqueios do YouTube
3. **Recursos**: Dispositivos Android têm recursos limitados

## 📄 Licença

Este projeto é parte do IPTV Player e segue a mesma licença.
