# Diagnóstico: Fluxo de Reprodução de Links Extraídos

## Problema Reportado
O app universal não está reproduzindo os links obtidos pelo extrator.

## Arquitetura do Fluxo

### 1. Extração (APP 2 - m3u-extension)
**Arquivo**: `ExtensionService.kt`

**Processo**:
1. Recebe lista de canais via JSON
2. Para cada canal, chama `resolveAndEnrich(url)`
3. Usa `interactor.resolve(url)` para extrair o link real
4. Captura cookies e User-Agent do navegador
5. Realiza handshake de validação
6. Gera URL no formato Kodi: `http://stream.url|User-Agent=Mozilla&Cookie=xyz`
7. Retorna JSON com:
   ```json
   {
     "name": "Canal",
     "m3u8": "http://stream.url|headers...",
     "headers": {
       "User-Agent": "...",
       "Cookie": "...",
       "Referer": "..."
     }
   }
   ```

### 2. Importação (APP 1 - universal)
**Arquivo**: `PlaylistRepositoryImpl.kt` → `importChannelsJsonBody()`

**Processo CORRIGIDO**:
1. Recebe JSON do extrator
2. Para cada canal:
   - Extrai URL limpa (sem opções Kodi)
   - Faz merge de headers (URL + JSON)
   - **CRÍTICO**: Registra headers no `JsonHeaderRegistry.setHeadersForUrl(cleanUrl, allHeaders)`
   - Reconstrói URL com headers no formato Kodi
   - Salva no banco de dados

**Logs adicionados**:
```kotlin
Timber.d("Registrando ${data.channels.size} canais no JsonHeaderRegistry...")
Timber.d("✓ Registrado headers para $name: ${allHeaders.keys}")
```

### 3. Reprodução (APP 1 - universal)
**Arquivo**: `PlayerManagerImpl.kt` → `tryPlay()`

**Processo CORRIGIDO**:
1. Recebe URL do canal (pode ter headers Kodi)
2. Extrai URL limpa: `sanitizedUrl = url.stripKodiOptions()`
3. **PRIORIDADE 1**: Consulta `JsonHeaderRegistry.getHeadersForUrl(sanitizedUrl)`
4. **PRIORIDADE 2**: Se não encontrar, faz parsing da URL (formato Kodi)
5. Merge final de headers
6. Cria DataSource com headers corretos
7. Inicia reprodução

**Logs adicionados**:
```kotlin
timber.d("=== HEADER RESOLUTION DEBUG ===")
timber.d("URL: ${sanitizedUrl.take(60)}...")
timber.d("Base headers from URL: ${baseHeaders.keys}")
timber.d("Dynamic headers from Registry: ${dynamicHeaders?.keys ?: "NONE"}")
timber.d("✓ Using headers from JsonHeaderRegistry (extracted)")
timber.d("Final headers: ${headers.keys}")
timber.d("Final User-Agent: ${headers["User-Agent"]?.take(40)}...")
```

## Melhorias Implementadas

### 1. Registro Automático de Headers
- `PlaylistRepositoryImpl` agora registra TODOS os headers no `JsonHeaderRegistry`
- Headers são registrados usando a URL limpa (sem parâmetros Kodi)
- Merge inteligente entre headers da URL e do JSON

### 2. Logs Detalhados
- Cada etapa do fluxo agora tem logs específicos
- Possível rastrear exatamente onde os headers são perdidos
- Formato consistente com emojis para facilitar leitura

### 3. Priorização Correta
- `JsonHeaderRegistry` tem prioridade sobre parsing de URL
- Headers extraídos pelo Python sempre sobrescrevem headers da URL
- Fallback gracioso se o registro estiver vazio

## Como Testar

### 1. Limpar Estado
```bash
adb shell pm clear com.m3u.androidApp
adb shell pm clear com.m3u.extension
```

### 2. Executar Extração
1. Abrir m3u-extension
2. Importar lista de canais
3. Verificar logs: `adb logcat | grep "ExtensionService\|JsonHeaderRegistry"`

### 3. Verificar Importação
```bash
adb logcat | grep "PlaylistRepositoryImpl"
```
Procurar por:
- "Registrando X canais no JsonHeaderRegistry..."
- "✓ Registrado headers para NOME_CANAL"

### 4. Testar Reprodução
```bash
adb logcat | grep "PlayerManagerImpl"
```
Procurar por:
- "=== HEADER RESOLUTION DEBUG ==="
- "Dynamic headers from Registry: [User-Agent, Cookie, Referer]"
- "✓ Using headers from JsonHeaderRegistry (extracted)"

## Possíveis Problemas Remanescentes

### 1. URL Mismatch
**Sintoma**: Registry retorna "NONE" mesmo após importação
**Causa**: URL salva no banco é diferente da URL consultada
**Solução**: Verificar se `stripKodiOptions()` está sendo aplicado consistentemente

### 2. Timing de Registro
**Sintoma**: Headers não estão disponíveis na primeira reprodução
**Causa**: Registro acontece após o player já ter iniciado
**Solução**: Garantir que importação completa antes de permitir reprodução

### 3. Limpeza do Registry
**Sintoma**: Headers antigos sendo usados
**Causa**: `JsonHeaderRegistry` não é limpo entre importações
**Solução**: Adicionar `JsonHeaderRegistry.clear()` antes de nova importação

## Próximos Passos

1. **Rebuild e Reinstalar**:
   ```bash
   ./gradlew :app:universal:assembleDebug :app:m3u-extension:assembleDebug
   adb install -r app/universal/build/outputs/apk/debug/universal-debug.apk
   adb install -r app/m3u-extension/build/outputs/apk/debug/m3u-extension-debug.apk
   ```

2. **Capturar Logs Completos**:
   ```bash
   adb logcat -c
   adb logcat > debug_playback.log
   ```

3. **Testar Cenário Completo**:
   - Importar 1 canal de teste
   - Tentar reproduzir
   - Analisar logs

## Código-Chave Modificado

### PlaylistRepositoryImpl.kt (linhas 178-220)
- Adicionado parsing de headers da URL Kodi
- Adicionado merge com headers do JSON
- Adicionado registro no `JsonHeaderRegistry`

### PlayerManagerImpl.kt (linhas 421-453)
- Adicionados logs detalhados de debug
- Priorização do `JsonHeaderRegistry`
- Fallback para headers da URL

### IdentityRotator.kt (novo arquivo)
- Gerenciamento de User-Agents para bypass 403
- Sincronizado com lista do Python

### JsonHeaderRegistry.kt (linhas 58-80)
- Adicionado método `setHeadersForUrl()`
- Melhorado matching de URLs (base URL sem query params)
