# YouTube M3U8 Extension - Modern Android Extraction

## 📋 Visão Geral

Esta extensão é responsável por extrair links M3U8 de transmissões ao vivo do YouTube usando uma abordagem híbrida moderna:
1. **WebView Sniffing**: Interceptação de manifestos HLS em tempo real.
2. **Chaquopy (Python p/ Android)**: Integração nativa do `yt-dlp` via pip, com suporte total a tokens de sessão.

### 🎯 Plataformas Suportadas
- ✅ **Smartphones Android** (API 26+)
- ✅ **Android TV / Google TV**
- ✅ **Smart TVs Android / TV Box**

### 🛠️ Stack Tecnológica
- **App Universal**: Kotlin + Media3/ExoPlayer.
- **App Extensão**: Kotlin + Chaquopy (Python 3.11) + yt-dlp.
- **Identity Support**: Injeção de PO Token e Visitor Data para bypass de 403.
- **Comunicação**: AIDL (Android Interface Definition Language).

## 🏗️ Arquitetura Moderna

```
┌─────────────────────────────────────────────────────────┐
│                   App Universal (Kotlin)                 │
│              (Android TV, Smartphones, Boxes)            │
└────────────────────┬────────────────────────────────────┘
                     │ AIDL (IExtension)
                     ▼
┌─────────────────────────────────────────────────────────┐
│              ExtensionService (Orquestrador)             │
│  - YouTubeWebViewTokenManager: Extrai cookies/PO Tokens  │
│  - YouTubeExtractorV2: Sniffing HLS + Fallback yt-dlp    │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│          Chaquopy Runtime (Python 3.11)                 │
│  - Executa yt-dlp nativamente dentro do processo Android │
│  - Utiliza os tokens injetados pelo WebView              │
└─────────────────────────────────────────────────────────┘
```

## 🔧 Componentes Principais

### 1. **YouTubeWebViewTokenManager**
- **Extração Dinâmica**: Utiliza um WebView oculto para capturar `VISITOR_DATA`, `PO_TOKEN` e `Identity Cookies`.
- **Sincronização**: Notifica o App Universal via Broadcast (`IDENTITY_UPDATE`) para manter os headers do player sincronizados.

### 2. **YouTubeExtractorV2**
- **Estratégia Híbrida**: Tenta capturar o manifesto `.m3u8` diretamente do WebView (mais rápido).
- **Fallback yt-dlp**: Se o sniffing falhar, utiliza o `yt-dlp` via Python enviando todos os cookies e tokens extraídos.

## 📁 Estrutura de Arquivos

```
app/m3u-extension/
├── src/main/java/com/m3u/extension/
│   ├── youtube/
│   │   ├── YouTubeWebViewTokenManager.kt  # Extração de tokens JS
│   │   └── YouTubeExtractorV2.kt         # Orquestrador de extração
│   ├── logic/
│   │   └── resolvers/                     # Resolvedores modulares
│   └── python/
│       └── extractor_v2.py                # Script Python p/ yt-dlp
└── build.gradle                           # Configuração Chaquopy
```

## 🛡️ Estabilidade YouTube (Anti-403)

O sistema conta com proteção avançada contra bloqueios:
- **PO Token Support**: Injeção de `X-YouTube-Po-Token` nos headers.
- **UA Stability**: User-Agent fixo e atrelado à sessão do WebView para evitar invalidação de tokens.
- **Session Refresh**: Em caso de erro 403, o cache é limpo e uma nova extração completa é disparada.

## 🚀 Build e Instalação

### Compilação
```bash
# Gerar APK de Extensão
./gradlew :app:m3u-extension:assembleRelease
```

### Requisitos de Build
O projeto utiliza **Chaquopy**. Certifique-se de que seu ambiente suporta a execução de scripts Python durante o build para o download das dependências pip (`yt-dlp`, `streamlink`).

## 📄 Licença
Este projeto é parte do IPTV Player e segue a mesma licença.
