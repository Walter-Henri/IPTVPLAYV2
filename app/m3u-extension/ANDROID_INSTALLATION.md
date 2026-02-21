# Instalação do Binário yt-dlp para Android

## 📱 Guia de Instalação - 100% Android

Este guia explica como preparar o binário `yt-dlp` para uso em dispositivos Android (Smartphones, Android TV, Smart TVs e TV Box).

---

## 🎯 Opções de Instalação

### **Opção 1: Empacotar no APK (Recomendado)**

Esta é a opção mais simples para distribuição. O binário é empacotado dentro do APK e copiado automaticamente para o dispositivo.

#### Passos:

1. **Baixar o binário yt-dlp**:
   ```bash
   cd app/m3u-extension/src/main/assets
   wget https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp
   ```

2. **Criar diretório assets se não existir**:
   ```bash
   mkdir -p app/m3u-extension/src/main/assets
   ```

3. **Mover binário para assets**:
   ```bash
   mv yt-dlp app/m3u-extension/src/main/assets/
   ```

4. **Build do APK**:
   ```bash
   ./gradlew :app:m3u-extension:assembleDebug
   ```

5. **Instalação automática**: 
   - Ao instalar o APK, o binário será copiado automaticamente para `context.filesDir`
   - Permissões de execução serão aplicadas automaticamente

**Vantagens**:
- ✅ Distribuição simples
- ✅ Não requer configuração manual
- ✅ Funciona em todos os dispositivos Android

**Desvantagens**:
- ❌ Aumenta o tamanho do APK (~3MB)

---

### **Opção 2: Download Manual no Dispositivo**

Para desenvolvimento ou testes, você pode instalar manualmente via ADB.

#### Passos:

1. **Baixar yt-dlp no computador**:
   ```bash
   wget https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp
   ```

2. **Enviar para o dispositivo via ADB**:
   ```bash
   # Enviar para storage temporário
   adb push yt-dlp /sdcard/Download/
   
   # Mover para diretório da aplicação
   adb shell run-as com.m3u.extension cp /sdcard/Download/yt-dlp /data/data/com.m3u.extension/files/
   
   # Aplicar permissões
   adb shell run-as com.m3u.extension chmod 755 /data/data/com.m3u.extension/files/yt-dlp
   ```

3. **Verificar instalação**:
   ```bash
   adb shell run-as com.m3u.extension ls -l /data/data/com.m3u.extension/files/yt-dlp
   ```

**Vantagens**:
- ✅ APK menor
- ✅ Fácil atualização do binário

**Desvantagens**:
- ❌ Requer ADB
- ❌ Configuração manual por dispositivo

---

### **Opção 3: Download Automático (Futuro)**

Implementação futura: baixar o binário na primeira execução.

```kotlin
// Exemplo de implementação futura
suspend fun downloadYtDlpIfNeeded(context: Context) {
    val binary = File(context.filesDir, "yt-dlp")
    if (!binary.exists()) {
        val url = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp"
        // Download e instalação automática
    }
}
```

---

## 🔍 Verificação da Instalação

### Via ADB

```bash
# Verificar se o binário existe
adb shell run-as com.m3u.extension ls -l /data/data/com.m3u.extension/files/yt-dlp

# Verificar permissões (deve mostrar -rwxr-xr-x)
adb shell run-as com.m3u.extension stat /data/data/com.m3u.extension/files/yt-dlp

# Testar execução
adb shell run-as com.m3u.extension /data/data/com.m3u.extension/files/yt-dlp --version
```

### Via Logs da Aplicação

```bash
# Monitorar logs durante inicialização
adb logcat -s YtDlpProcessRunner:D

# Procurar por mensagens como:
# "Binário encontrado em filesDir: /data/data/com.m3u.extension/files/yt-dlp"
# "Binário copiado de assets para: /data/data/com.m3u.extension/files/yt-dlp"
```

---

## 📊 Localizações do Binário por Dispositivo

| Tipo de Dispositivo | Caminho Típico |
|---------------------|----------------|
| **Smartphone** | `/data/data/com.m3u.extension/files/yt-dlp` |
| **Android TV** | `/data/data/com.m3u.extension/files/yt-dlp` |
| **Smart TV** | `/data/data/com.m3u.extension/files/yt-dlp` |
| **TV Box** | `/data/data/com.m3u.extension/files/yt-dlp` |

**Nota**: O caminho é sempre o mesmo, independente do tipo de dispositivo Android.

---

## 🛠️ Troubleshooting

### Problema: "Binário não encontrado"

**Solução 1**: Verificar se está empacotado no APK
```bash
# Extrair APK e verificar assets
unzip -l app-debug.apk | grep yt-dlp
```

**Solução 2**: Instalar manualmente via ADB (ver Opção 2)

---

### Problema: "Permissão negada"

**Causa**: Binário sem permissão de execução

**Solução**:
```bash
adb shell run-as com.m3u.extension chmod 755 /data/data/com.m3u.extension/files/yt-dlp
```

---

### Problema: "Binário corrompido"

**Causa**: Download incompleto ou arquivo corrompido

**Verificação**:
```bash
# Tamanho deve ser > 3MB
adb shell run-as com.m3u.extension ls -lh /data/data/com.m3u.extension/files/yt-dlp
```

**Solução**: Baixar novamente e reinstalar

---

## 📱 Considerações por Tipo de Dispositivo

### Smartphones Android
- ✅ Funciona perfeitamente
- ✅ Storage interno suficiente
- ⚠️ Atenção ao consumo de bateria durante extração

### Android TV
- ✅ Funciona perfeitamente
- ✅ Ideal para uso contínuo
- ✅ Conexão estável (Ethernet recomendado)

### Smart TVs Android
- ✅ Funciona perfeitamente
- ⚠️ Algumas TVs podem ter restrições de execução
- 💡 Testar em modo desenvolvedor se necessário

### TV Box Android
- ✅ Funciona perfeitamente
- ✅ Geralmente sem restrições
- ✅ Ideal para IPTV

---

## 🔐 Permissões Necessárias

### AndroidManifest.xml

```xml
<!-- Permissão para internet (obrigatória) -->
<uses-permission android:name="android.permission.INTERNET" />

<!-- Permissão para storage externo (opcional) -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

---

## 📦 Tamanho do Binário

- **yt-dlp**: ~3.0 MB
- **APK sem binário**: ~2.5 MB
- **APK com binário**: ~5.5 MB

---

## 🔄 Atualização do Binário

### Manual (Desenvolvimento)
```bash
# Baixar nova versão
wget https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp

# Substituir no projeto
mv yt-dlp app/m3u-extension/src/main/assets/

# Rebuild
./gradlew :app:m3u-extension:assembleDebug
```

### Automática (Futuro)
- Implementar verificação de versão
- Download automático de atualizações
- Notificação ao usuário

---

## ✅ Checklist de Instalação

- [ ] Binário baixado de fonte oficial
- [ ] Binário copiado para `assets/` ou enviado via ADB
- [ ] Permissões de execução aplicadas (755)
- [ ] Tamanho do arquivo verificado (> 3MB)
- [ ] Teste de execução realizado (`--version`)
- [ ] Logs verificados (sem erros)

---

## 📞 Suporte

Se encontrar problemas:

1. Verificar logs: `adb logcat -s YtDlpProcessRunner`
2. Testar execução manual via ADB
3. Verificar permissões do arquivo
4. Consultar `TROUBLESHOOTING.md`

---

## 📚 Referências

- **yt-dlp GitHub**: https://github.com/yt-dlp/yt-dlp
- **Releases**: https://github.com/yt-dlp/yt-dlp/releases
- **Documentação**: https://github.com/yt-dlp/yt-dlp#readme
