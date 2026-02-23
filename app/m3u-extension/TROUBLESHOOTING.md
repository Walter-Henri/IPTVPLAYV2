# Troubleshooting - YouTube M3U8 Extension

## 🔍 Problemas Comuns e Soluções

### 1. "403 Forbidden" no YouTube
**Sintoma**: O vídeo não carrega ou para após alguns segundos com erro de rede no player.

**Causa**: O YouTube detectou a sessão como automatizada ou os tokens (Cookies/PO Token) expiraram.

**Solução**:
- O app possui um protocolo automático de recuperação. Aguarde a primeira tentativa de "retry".
- Se persistir, tente fechar e abrir o canal novamente para forçar o `YouTubeWebViewTokenManager` a gerar uma nova identidade.
- Verifique se a hora do dispositivo está correta (horário de rede).

---

### 2. Falha no Sniffing HLS (WebView)
**Sintoma**: A extração demora mais que o normal.

**Causa**: O ríalo de interceptação falhou em capturar o manifesto `.m3u8` diretamente.

**Comportamento**: O sistema irá disparar automaticamente o **Fallback yt-dlp**. Isso é normal e garante que o link seja extraído mesmo que o sniffing falhe.

---

### 3. Erros de Build (Chaquopy)
**Sintoma**: O Gradle falha ao baixar `yt-dlp` ou `streamlink`.

**Causa**: Problemas de conexão com o PyPI ou ambiente Python local (Gradle) mal configurado.

**Solução**:
- Certifique-se de ter conexão estável com a internet durante o build.
- Limpe o cache do Gradle: `./gradlew clean`.
- Verifique se o `ndkVersion` no `gradle.properties` está correto.

---

### 4. Native Stripping Error (libffmpeg.zip.so)
**Sintoma**: Erro `llvm-strip` durante o assemble do APK de extensão.

**Causa**: Algumas bibliotecas do módulo `youtubedl-android-ffmpeg` não podem ser processadas pelo strip do NDK.

**Solução**: Certifique-se de que o `build.gradle` da extensão contém a opção `doNotStrip "**/libffmpeg.zip.so"`.

---

### 5. Logs e Diagnóstico
Para ver o que está acontecendo por trás das câmeras:

```bash
# Ver extração e tokens
adb logcat -s IdentityRegistry YouTubeExtractorV2 YouTubeWebViewTokenManager PlayerManagerImpl
```

**Tags Importantes**:
- `✓ PO Token injected`: O token foi enviado com sucesso.
- `YouTube 403 detectado`: O protocolo de recuperação foi iniciado.
