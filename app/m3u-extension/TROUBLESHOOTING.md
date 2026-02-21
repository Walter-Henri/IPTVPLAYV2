# Troubleshooting - YouTube M3U8 Extension

## 🔍 Problemas Comuns e Soluções

### 1. "Binário yt-dlp não encontrado"

**Sintoma**: Erro ao tentar resolver URLs do YouTube

**Causa**: O binário `yt-dlp` não está presente na raiz do projeto

**Solução**:
```bash
cd /caminho/para/IPTV-PLAYER-BETA-main
wget https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp
chmod +x yt-dlp
```

**Verificação**:
```bash
./yt-dlp --version
```

---

### 2. "Permissão negada" ao executar yt-dlp

**Sintoma**: Erro de permissão ao tentar executar o binário

**Causa**: O binário não tem permissão de execução

**Solução**:
```bash
chmod +x yt-dlp
```

**Verificação**:
```bash
ls -l yt-dlp
# Deve mostrar: -rwxr-xr-x (com 'x' para execução)
```

---

### 3. "yt-dlp timeout após 45s"

**Sintoma**: Processo é terminado após 45 segundos

**Causas possíveis**:
- Conexão lenta
- URL inválida ou indisponível
- Vídeo/live muito longo para processar

**Soluções**:
1. Verificar conexão de internet
2. Testar URL manualmente:
   ```bash
   ./yt-dlp -g -f "bestvideo+bestaudio/best" "URL_DO_YOUTUBE"
   ```
3. Se necessário, aumentar timeout em `YtDlpProcessRunner.kt`:
   ```kotlin
   private const val TIMEOUT_SECONDS = 60L // Era 45L
   ```

---

### 4. "yt-dlp falhou com código 1"

**Sintoma**: Processo retorna exit code diferente de 0

**Causas possíveis**:
- URL inválida
- Vídeo privado ou removido
- Região bloqueada
- Formato não disponível

**Diagnóstico**:
```bash
# Execute manualmente para ver o erro completo
./yt-dlp -g -f "bestvideo+bestaudio/best" "URL_PROBLEMA" 2>&1
```

**Soluções**:
- Verificar se a URL está correta
- Testar com formato diferente: `"best"`
- Verificar se o vídeo está disponível publicamente

---

### 5. "Nenhuma URL válida retornada pelo yt-dlp"

**Sintoma**: Processo executa mas não retorna URLs

**Causas possíveis**:
- Saída do yt-dlp está vazia
- URLs retornadas não são HTTP/HTTPS
- Problema no parsing da saída

**Diagnóstico**:
```bash
# Verificar saída completa
./yt-dlp -g -f "bestvideo+bestaudio/best" "URL" 2>&1 | cat -A
```

**Solução**:
- Verificar logs do `YtDlpProcessRunner` (tag: `YtDlpProcessRunner`)
- Verificar se o formato está correto

---

### 6. Canais duplicados no resultado

**Sintoma**: Mesmo canal aparece múltiplas vezes

**Causa**: Deduplicação não está funcionando

**Verificação**:
- Verificar se `name` e `url` estão corretos no `channels.json`
- Verificar logs para mensagem "Canal duplicado ignorado"

**Solução**: A deduplicação é automática baseada em `name|url` (case-insensitive)

---

### 7. Alguns canais falham silenciosamente

**Sintoma**: Alguns canais não aparecem no resultado final

**Causa**: Falhas individuais são esperadas e não interrompem o processamento

**Diagnóstico**:
```bash
# Filtrar logs por canal específico
adb logcat -s ExtensionService YtDlpInteractor YtDlpProcessRunner | grep "NOME_DO_CANAL"
```

**Comportamento esperado**:
- Canais com falha são logados mas não interrompem o processamento
- Apenas canais com sucesso aparecem no resultado final

---

### 8. Performance lenta com muitos canais

**Sintoma**: Processamento de 50+ canais demora muito

**Causa**: Concorrência limitada a 3 execuções paralelas

**Explicação**: Isso é intencional para evitar:
- Sobrecarga do dispositivo
- Rate limiting do YouTube
- Consumo excessivo de recursos

**Otimizações possíveis**:
1. Aumentar concorrência (com cuidado):
   ```kotlin
   // Em ExtensionService.kt
   val semaphore = kotlinx.coroutines.sync.Semaphore(5) // Era 3
   ```
2. Filtrar canais inativos antes de processar
3. Usar cache de URLs já resolvidas

---

### 9. Build falha com erro de dependência

**Sintoma**: Gradle não consegue resolver `youtubedl-android`

**Causa**: Dependência foi removida na refatoração

**Solução**:
1. Limpar cache do Gradle:
   ```bash
   ./gradlew clean
   rm -rf .gradle
   ```
2. Sincronizar projeto:
   ```bash
   ./gradlew --refresh-dependencies
   ```

---

### 10. Logs não aparecem no Logcat

**Sintoma**: Não consigo ver logs da extensão

**Solução**:
```bash
# Filtrar por tags específicas
adb logcat -s ExtensionService YtDlpInteractor YtDlpProcessRunner

# Ou filtrar por pacote
adb logcat | grep "com.m3u.extension"
```

---

## 🧪 Testes Manuais

### Testar Resolução de URL Única

```bash
# Via adb shell
adb shell am start -a android.intent.action.SEND \
  -t text/plain \
  --es android.intent.extra.TEXT "https://youtube.com/@canal/live" \
  -n com.m3u.extension/.ui.MainActivity
```

### Testar Processamento de channels.json

```kotlin
// Via código Kotlin no app Universal
val service: IExtension = // bind ao serviço
val callback = object : IExtensionCallback.Stub() {
    override fun onProgress(current: Int, total: Int, name: String) {
        Log.d("Test", "Progresso: $current/$total - $name")
    }
    override fun onResult(jsonResult: String) {
        Log.d("Test", "Resultado: $jsonResult")
    }
    override fun onError(message: String) {
        Log.e("Test", "Erro: $message")
    }
}

val json = """
{
  "channels": [
    {"name": "Test", "url": "https://youtube.com/@test/live", "group": "Test"}
  ]
}
"""

service.extractLinksAsync(json, callback)
```

---

## 📊 Métricas de Performance

### Tempos Esperados (por canal)

| Cenário | Tempo Médio |
|---------|-------------|
| Live ativa | 5-15s |
| Canal sem live | 10-20s |
| URL inválida | 2-5s (falha rápida) |
| Timeout | 45s (limite) |

### Concorrência

- **Padrão**: 3 canais em paralelo
- **Recomendado**: 3-5 (dependendo do dispositivo)
- **Máximo testado**: 10 (pode causar instabilidade)

---

## 🔧 Ferramentas de Diagnóstico

### Script de Verificação

```bash
cd app/m3u-extension
./verify-ytdlp.sh
```

### Teste Manual do Binário

```bash
# Teste básico
./yt-dlp --version

# Teste de extração
./yt-dlp -g -f "bestvideo+bestaudio/best" "https://youtube.com/@canal/live"

# Teste com verbose
./yt-dlp -v -g -f "bestvideo+bestaudio/best" "https://youtube.com/@canal/live"
```

### Análise de Logs

```bash
# Capturar logs em arquivo
adb logcat -s ExtensionService YtDlpInteractor YtDlpProcessRunner > extension.log

# Filtrar apenas erros
adb logcat -s ExtensionService:E YtDlpInteractor:E YtDlpProcessRunner:E

# Filtrar por canal específico
adb logcat | grep "Nome do Canal"
```

---

## 📞 Suporte

Se o problema persistir após seguir este guia:

1. **Coletar informações**:
   - Versão do Android
   - Logs completos (via `adb logcat`)
   - URL problemática (se aplicável)
   - Versão do yt-dlp (`./yt-dlp --version`)

2. **Verificar issues conhecidos**:
   - GitHub do yt-dlp: https://github.com/yt-dlp/yt-dlp/issues
   - Issues do projeto

3. **Criar issue detalhado** com:
   - Descrição do problema
   - Passos para reproduzir
   - Logs relevantes
   - Ambiente (dispositivo, Android version, etc.)
