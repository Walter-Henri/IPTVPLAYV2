<h1 align="center">M3Uplay Native</h1>
<p align="center">
  Um reprodutor de mídia de código aberto para Android, focado em desempenho e usabilidade.
</p>

<p align="center">
  <img src="https://img.shields.io/github/v/release/Walter-Henri/IPTV-PLAYER-BETA?color=blue&label=Versão">
  <img src="https://img.shields.io/badge/Android-8.0%2B-brightgreen?logo=android">
  <img src="https://img.shields.io/github/license/Walter-Henri/IPTV-PLAYER-BETA?color=orange">
</p>

**M3Uplay Native** é um player de mídia de alto desempenho desenvolvido para proporcionar a melhor experiência de streaming no Android. Construído do zero com foco em simplicidade, velocidade e design moderno, utilizando as tecnologias mais recentes do ecossistema Android.

---

## ✨ Funcionalidades

- 📱 **Interface Moderna:** Construído 100% com Jetpack Compose, seguindo as diretrizes do Material Design.
- ⚡ **Performance:** Carregamento otimizado de listas e navegação fluida.
- 📺 **Suporte a Android TV:** Interface totalmente adaptada para navegação com controle remoto (D-PAD).
- 🛠 **Arquitetura Robusta:** Projeto multi-módulo seguindo o padrão MVVM.
- 📦 **Minimalista:** Código limpo, focado nos recursos essenciais para uma experiência de qualidade.

## 📸 Screenshots

<!-- 
TODO: Adicionar screenshots do aplicativo.
Crie uma pasta `.github/images/` no seu repositório e adicione as imagens aqui.
-->

| Tela Inicial | Player |
|--------------|--------|
| *adicione a imagem `home.png`* | *adicione a imagem `player.png`* |


## ⬇️ Download

A seção de releases do GitHub ainda não contém arquivos. Após compilar o projeto, você pode fazer o upload dos APKs na seção de "Releases" do seu repositório.

## 🛠 Stack Tecnológica

- **Linguagem:** Kotlin (100%)
- **UI Toolkit:** Jetpack Compose
- **Arquitetura:** MVVM (Model-View-ViewModel) com múltiplos módulos (`core`, `data`, `business`, etc.)
- **Injeção de Dependência:** Hilt
- **Media Engine:** Media3 / ExoPlayer
- **Componentes AndroidX:** Lifecycle, Room, WorkManager, etc.

## 🚀 Como Compilar (Build)

Este projeto utiliza o Gradle. Para compilar o aplicativo, você pode executar o seguinte comando na raiz do projeto:

### Pré-requisitos
- JDK 21 ou superior
- Android SDK

### Comando de Build

```bash
# Para gerar um APK de depuração (debug)
./gradlew :app:universal:assembleDebug
```

O APK gerado estará localizado em `IPTV-PLAYER-BETA/app/universal/build2/outputs/apk/debug/`.

Para um APK de produção (release), você precisará configurar o arquivo `local.properties` com as informações da sua chave de assinatura, conforme especificado no `app/universal/build.gradle.kts`.

## 🤝 Contribuição e Suporte

1. Abra uma **Issue** para relatar bugs ou sugerir melhorias.
2. Deixe uma ⭐️ no projeto para ajudar no crescimento.

## 📜 Licença

Distribuído sob a licença **GPL 3.0**. Veja o arquivo `LICENSE` para detalhes.

---
Desenvolvido por [Walter Henri](https://github.com/Walter-Henri)