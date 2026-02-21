<h1 align="center">IPTV PLAY V2</h1>
<p align="center">
  Um reprodutor de mídia nativo e profissional para Android, focado em performance, design premium e automação.
</p>

<p align="center">
  <img src="https://img.shields.io/github/v/release/Walter-Henri/IPTVPLAYV2?color=blue&label=Versão">
  <img src="https://img.shields.io/badge/Android-8.0%2B-brightgreen?logo=android">
  <img src="https://img.shields.io/github/actions/workflow/status/Walter-Henri/IPTVPLAYV2/android-unified-build.yml?label=Build&logo=github">
  <img src="https://img.shields.io/github/license/Walter-Henri/IPTVPLAYV2?color=orange">
</p>

**IPTV PLAY V2** é a evolução do player de mídia nativo para Android. Reconstruído para ser escalável, seguro e totalmente automatizado, oferecendo uma experiência de streaming fluida tanto em dispositivos móveis quanto em Android TV.

---

## ✨ Funcionalidades Profissionais

- 📱 **Interface Premium:** Construída com **Jetpack Compose** e **Haze (Glassmorphism)** para um visual moderno e translúcido.
- ⚡ **Performance Nativa:** Motor Media3/ExoPlayer otimizado para baixo buffering e suporte a HLS, DASH, RTSP e RTMP.
- 📺 **Android TV Ready:** Experiência completa de 10 pés com suporte total a D-PAD (controle remoto).
- 🧩 **Extensão M3U:** Módulo separado para processamento avançado de links e integração com Python (Chaquopy).
- 🛠 **Arquitetura Multi-Módulo:** Separação clara de responsabilidades (`core`, `data`, `business`, `i18n`).
- 🚀 **CI/CD Integrado:** Build e assinatura automática via GitHub Actions para cada push na branch principal.

## 🛡️ Segurança e Build Profissional

O projeto segue as melhores práticas de segurança para o GitHub:
- **Zero Secrets no Repo:** Arquivos sensíveis como `meu-app.keystore` e `local.properties` são ignorados via `.gitignore`.
- **Assinatura via GitHub Secrets:** O processo de assinatura de produção é feito de forma segura e automatizada durante o workflow de CI/CD usando secrets encriptados.

## 🛠 Stack Tecnológica

- **Linguagem:** Kotlin 2.1+
- **UI Toolkit:** Jetpack Compose (100%)
- **Arquitetura:** Clean Architecture + MVVM
- **Injeção de Dependências:** Hilt
- **Extração de Mídia:** yt-dlp & Streamlink integration
- **Assinatura:** Automatizada via GitHub Actions (v2)

## 🚀 Como Compilar e Automatizar

### 1. Automação no GitHub (Recomendado)
Sempre que você fizer um `git push`, o GitHub Actions irá:
1. Compilar o app.
2. Assinar os APKs (Universal e Extension).
3. Gerar um artefato pronto para download na aba **Actions**.

### 2. Build Local
Para compilar manualmente na sua máquina:

```bash
# Dar permissão ao wrapper
chmod +x gradlew

# Gerar APK Universal (Smartphone + TV)
./gradlew :app:universal:assembleRelease

# Gerar APK de Extensão
./gradlew :app:m3u-extension:assembleRelease
```

## ⬇️ Download
Você pode baixar os APKs assinados após o término de cada build na aba [Actions](https://github.com/Walter-Henri/IPTVPLAYV2/actions) do seu repositório.

## 📜 Licença

Distribuído sob a licença **GPL 3.0**. Veja o arquivo `LICENSE` para detalhes.

---
Desenvolvido por [Walter Henri](https://github.com/Walter-Henri)