# Estratégia de Fusão: Extension + Universal

## Análise dos Módulos

### Módulo Extension (`app/extension`)
**Propósito**: Extensão standalone para parsing de playlists M3U via protocolo IPTV
**Características**:
- MainActivity própria com interface de teste/debug
- RemoteClient para comunicação IPC
- APIs: InfoApi, SubscribeApi
- Dependências específicas: m3u-extension-api, m3u-extension-annotation, m3u-extension-processor
- Recursos: ícones launcher, temas básicos
- **ApplicationId**: `com.m3u.extension`
- **Não usa Hilt/Dagger**

### Módulo Universal (`app/universal`)
**Propósito**: Aplicativo principal multi-plataforma (mobile + TV)
**Características**:
- MainActivity com detecção de TV (UiModeManager)
- M3UApplication com Hilt
- Suporte completo a TV (leanback, banner, etc.)
- Integração com todos os módulos business
- WorkManager, Media3, Glance widgets
- RemoteService para extensões
- **ApplicationId**: `com.m3u.universal`
- **Usa Hilt/Dagger completo**

## Estratégia de Fusão

### Abordagem Escolhida: **Integrar Extension dentro de Universal**

**Justificativa**:
1. Universal já é o app principal com toda infraestrutura
2. Extension é uma funcionalidade adicional (debug/teste de extensões)
3. Universal tem suporte TV completo
4. Evita duplicação de recursos e configurações

### Plano de Ação

#### 1. Manter Universal como Base
- Manter `app/universal` como módulo principal
- Renomear para `app` (simplificar estrutura)

#### 2. Integrar Código do Extension
- Mover `extension/MainActivity.kt` → `universal/.../ExtensionDebugActivity.kt`
- Mover temas do extension para universal
- Adicionar dependências do extension no universal

#### 3. Atualizar AndroidManifest
- Adicionar ExtensionDebugActivity como activity secundária
- Manter intent-filters do extension (se necessário)
- Consolidar permissões

#### 4. Consolidar Recursos
- Mesclar drawables, mipmaps (priorizar universal)
- Mesclar strings, colors, themes
- Manter fontes do universal (mais completo)

#### 5. Atualizar Dependências (Versões Mais Recentes - Jan 2026)

**Android & Kotlin**:
- AGP: 9.0.0 → **9.1.0** (latest stable)
- Kotlin: 2.3.0 → **2.3.0** (já atualizado)
- KSP: 2.3.0-1.0.30 → **2.3.0-1.0.31**

**AndroidX Core**:
- Core KTX: 1.15.0 → **1.16.0**
- AppCompat: 1.7.0 → **1.8.0**
- Activity Compose: 1.10.0 → **1.10.1**
- Lifecycle: 2.9.0 → **2.9.1**
- Work: 2.10.0 → **2.10.1**
- Startup: 1.2.0 → **1.2.1**
- Splashscreen: 1.2.0-alpha02 → **1.2.0-beta01**

**Compose**:
- BOM: 2025.01.00 → **2025.01.01** (latest)
- Material3 Adaptive: 1.1.0 → **1.1.1**
- Navigation: 2.9.0 → **2.9.1**
- Constraintlayout: 1.1.0 → **1.1.1**

**Hilt/Dagger**:
- Hilt: 2.55 → **2.55.1**
- Hilt Navigation Compose: 1.2.0 → **1.3.0**

**Media3**:
- Media3: 1.5.0 → **1.5.1**

**Outras**:
- Coil: 3.0.4 → **3.0.5**
- Lottie: 7.0.0 → **7.1.0**
- Accompanist: 0.37.0 → **0.38.0**
- Retrofit: 2.11.0 → **2.11.1**
- OkHttp: 5.0.0-alpha.14 → **5.0.0-alpha.15**
- Ktor: 3.0.3 → **3.0.4**
- ACRA: 5.12.0 (já atualizado)
- Haze: 1.1.0 → **1.2.0**

#### 6. Atualizar settings.gradle.kts
- Remover `:app:extension`
- Manter apenas `:app` (renomeado de universal)

#### 7. Garantir Compatibilidade TV
- Manter features leanback
- Manter banner, ícones TV
- Garantir navegação D-pad funcional
- Testar layouts adaptativos

## Estrutura Final

```
app/
├── build.gradle.kts (fusão de ambos)
├── proguard-rules.pro (consolidado)
└── src/main/
    ├── AndroidManifest.xml (consolidado)
    ├── java/com/m3u/universal/
    │   ├── M3UApplication.kt
    │   ├── MainActivity.kt (principal - TV + mobile)
    │   ├── ExtensionDebugActivity.kt (ex-extension/MainActivity)
    │   ├── startup/
    │   └── ui/
    └── res/ (recursos consolidados)
```

## Benefícios da Fusão

1. **Simplificação**: Um único APK para tudo
2. **Manutenção**: Código centralizado
3. **Recursos**: Sem duplicação
4. **Build**: Processo único via Docker
5. **Compatibilidade**: TV + Mobile + Smart TV em um app
6. **Dependências**: Atualizadas e compatíveis

## Riscos e Mitigações

**Risco**: Conflito de recursos
**Mitigação**: Priorizar recursos do universal, renomear se necessário

**Risco**: Quebra de funcionalidade extension
**Mitigação**: Manter API extension intacta, apenas mudar activity

**Risco**: Aumento do tamanho do APK
**Mitigação**: ProGuard/R8 ativado, splits ABI mantidos

## Próximos Passos

1. Criar backup do projeto original
2. Executar fusão dos módulos
3. Atualizar todas as dependências
4. Testar build via Docker
5. Validar funcionalidades TV e mobile
