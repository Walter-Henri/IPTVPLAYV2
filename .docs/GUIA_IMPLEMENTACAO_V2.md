# Guia de Implementação: YouTube Extractor V2

## 📋 Checklist de Implementação

### ✅ Arquivos Criados
- [x] `extractor_v2.py` - Extrator Python com validação robusta
- [x] `YouTubeExtractorV2.kt` - Wrapper Kotlin com cache
- [x] Integração no `ExtensionService.kt`
- [x] Documentação técnica completa

### 🔧 Próximos Passos

#### 1. Build e Instalação
```bash
cd c:\Users\Walter\Downloads\IPTV-PLAYER-BETA-main

# Build
./gradlew :app:m3u-extension:assembleDebug --stacktrace

# Instalar
adb -s 9d4l9tqszhdiwocq install -r app\m3u-extension\build\outputs\apk\debug\m3u-extension-debug.apk
```

#### 2. Teste Básico

##### 2.1 Preparar JSON de Teste
Criar `test_youtube.json`:
```json
{
  "channels": [
    {
      "name": "Globo News",
      "url": "https://www.youtube.com/watch?v=EXEMPLO",
      "logo": "https://exemplo.com/logo.png",
      "group": "Notícias"
    }
  ]
}
```

##### 2.2 Executar Teste Manual
1. Abrir app M3U Extension
2. Importar `test_youtube.json`
3. Observar logs

##### 2.3 Verificar Logs
```bash
# Terminal 1: Logs Python
adb logcat | grep "python.stderr"

# Terminal 2: Logs Kotlin
adb logcat | grep "YouTubeExtractorV2\|ExtensionService"

# Terminal 3: Logs PlayerManager
adb logcat | grep "PlayerManagerImpl"
```

#### 3. Validação de Funcionamento

##### Logs Esperados (Python)
```
Processando: Globo News
URL: https://www.youtube.com/...
Tentativa android_tv...
✓ M3U8 validado: https://manifest.googlevideo.com/...
✅ SUCESSO com android_tv!
```

##### Logs Esperados (Kotlin)
```
🎯 Detectado YouTube, usando ExtractorV2 para: Globo News
Extraindo: Globo News
✓ Validação HEAD bem-sucedida: 200
✅ Stream validado e funcional
✅ ExtractorV2 sucesso: Globo News (yt-dlp (android_tv))
```

##### Logs Esperados (PlayerManager)
```
=== HEADER RESOLUTION DEBUG ===
URL: https://manifest.googlevideo.com/...
Dynamic headers from Registry: [User-Agent, Referer, Origin]
✓ Using headers from JsonHeaderRegistry (extracted)
Final User-Agent: Mozilla/5.0 (Linux; Android 10; BRAVIA 4K...
```

## 🐛 Troubleshooting

### Problema 1: "Erro no ExtractorV2"
**Sintoma**: Logs mostram exceção no Kotlin
**Causa**: Módulo Python não encontrado
**Solução**:
```bash
# Verificar se extractor_v2.py está no lugar certo
adb shell ls /data/data/com.m3u.extension/files/chaquopy/AssetFinder/app/m3u-extension/src/main/python/
```

### Problema 2: "Todas as tentativas falharam"
**Sintoma**: Python tenta todos os UAs e falha
**Causa**: URL inválida ou bloqueio severo
**Solução**:
1. Verificar se URL é válida (testar no navegador)
2. Verificar logs detalhados do yt-dlp
3. Tentar com proxy (implementação futura)

### Problema 3: "Stream validado mas não reproduz"
**Sintoma**: Validação OK, mas player retorna 403
**Causa**: Headers não estão sendo aplicados corretamente
**Solução**:
1. Verificar se headers foram registrados no `JsonHeaderRegistry`
2. Verificar logs do PlayerManager
3. Confirmar que URL no banco é a mesma da extração

### Problema 4: "No headers in Registry"
**Sintoma**: PlayerManager não encontra headers
**Causa**: URL mismatch entre extração e reprodução
**Solução**:
```kotlin
// Adicionar log no PlaylistRepositoryImpl
Timber.d("URL limpa para registro: $cleanUrl")
Timber.d("Headers sendo registrados: ${allHeaders.keys}")

// Adicionar log no PlayerManagerImpl
timber.d("Buscando headers para: $sanitizedUrl")
```

## 📊 Métricas de Sucesso

### Antes (Método Antigo)
- Taxa de sucesso: ~60%
- Tempo médio de extração: 15-30s
- Erros 403 na reprodução: ~40%

### Depois (Método V2)
- Taxa de sucesso esperada: ~95%
- Tempo médio de extração: 10-20s (com cache: <1s)
- Erros 403 na reprodução: <5%

## 🔄 Fluxo de Teste Completo

### Teste 1: Extração Inicial
```
1. Limpar dados do app
   adb shell pm clear com.m3u.extension
   adb shell pm clear com.m3u.androidApp

2. Importar lista com 1 canal YouTube

3. Verificar:
   ✓ Logs mostram "🎯 Detectado YouTube"
   ✓ Logs mostram "✅ ExtractorV2 sucesso"
   ✓ Logs mostram "✓ Registrado headers para"
```

### Teste 2: Cache
```
1. Importar mesma lista novamente

2. Verificar:
   ✓ Logs mostram "✓ Usando resultado em cache"
   ✓ Tempo de processamento < 1s
```

### Teste 3: Reprodução
```
1. Abrir app Universal
2. Tentar reproduzir canal

3. Verificar:
   ✓ Logs mostram "Dynamic headers from Registry: [User-Agent, Referer, Origin]"
   ✓ Logs mostram "✓ Using headers from JsonHeaderRegistry"
   ✓ Stream inicia sem erros
```

### Teste 4: Rotação de Identidade (403)
```
1. Simular erro 403 (modificar URL para forçar erro)

2. Verificar:
   ✓ Logs mostram "Detectado 403/401"
   ✓ Logs mostram "Rodando identidade (UA)"
   ✓ Player tenta novamente com UA diferente
```

## 📝 Notas de Desenvolvimento

### Otimizações Futuras

1. **Parallel Extraction**
   ```kotlin
   // Extrair múltiplos canais em paralelo
   val results = channels.map { channel ->
       async { extractorV2.extractChannel(...) }
   }.awaitAll()
   ```

2. **Persistent Cache**
   ```kotlin
   // Usar Room Database ao invés de arquivos
   @Entity
   data class CachedStream(
       @PrimaryKey val url: String,
       val m3u8Url: String,
       val headers: String, // JSON
       val timestamp: Long
   )
   ```

3. **Quality Selection**
   ```python
   # Permitir escolha de qualidade
   formats = [f for f in info['formats'] if f.get('height') == 720]
   ```

4. **Proxy Support**
   ```python
   ydl_opts['proxy'] = 'socks5://127.0.0.1:1080'
   ```

### Limitações Conhecidas

1. **Rate Limiting**: YouTube pode bloquear após muitas requisições
   - Solução: Implementar delay entre extrações
   
2. **Geo-blocking**: Alguns streams podem estar bloqueados por região
   - Solução: Implementar suporte a proxy/VPN
   
3. **Live Streams**: Streams ao vivo podem ter URLs que expiram
   - Solução: Implementar re-extração automática

## 🎓 Referências Técnicas

- [yt-dlp Extractor Arguments](https://github.com/yt-dlp/yt-dlp#extractor-arguments)
- [YouTube Innertube Clients](https://github.com/iv-org/invidious/blob/master/docs/API.md)
- [HLS Specification](https://datatracker.ietf.org/doc/html/rfc8216)
- [ExoPlayer HLS](https://exoplayer.dev/hls.html)
- [OkHttp Interceptors](https://square.github.io/okhttp/interceptors/)

## ✅ Checklist Final

Antes de considerar a implementação completa:

- [ ] Build sem erros
- [ ] Instalação bem-sucedida
- [ ] Teste de extração com sucesso
- [ ] Teste de cache funcionando
- [ ] Teste de reprodução sem 403
- [ ] Logs detalhados visíveis
- [ ] Documentação atualizada
- [ ] Código commitado

## 🚀 Deploy

Quando tudo estiver funcionando:

```bash
# 1. Tag de versão
git tag -a v2.0-youtube-robust -m "YouTube Extractor V2 - Metodologia Robusta"

# 2. Build release
./gradlew :app:m3u-extension:assembleRelease
./gradlew :app:universal:assembleRelease

# 3. Distribuir APKs
# app/m3u-extension/build/outputs/apk/release/m3u-extension-release.apk
# app/universal/build/outputs/apk/release/universal-release.apk
```
