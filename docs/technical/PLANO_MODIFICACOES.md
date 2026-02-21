# Plano de Modificações - M3Uplay-Manus

## 1. Análise da Estrutura Atual

### Arquitetura Identificada
- **Linguagem**: Kotlin 100%
- **UI Framework**: Jetpack Compose
- **Arquitetura**: MVVM (Model-View-ViewModel)
- **Database**: Room
- **Network**: Retrofit / OkHttp
- **Media Engine**: Media3 / ExoPlayer
- **DI**: Hilt
- **Build System**: Gradle KTS

### Módulos Existentes
- `app:universal` - Aplicação principal
- `core` - Módulos core (foundation, extension)
- `data` - Camada de dados
- `business` - Lógica de negócio (foryou, favorite, setting, playlist, channel, extension)
- `i18n` - Internacionalização
- `lint` - Análise de código

## 2. Modificações Planejadas

### 2.1 Nova UI Premium com Tema Escuro/Claro

#### Componentes a Criar/Modificar:
1. **Sistema de Temas Premium**
   - Criar `core/foundation/src/main/java/com/m3u/core/foundation/theme/PremiumTheme.kt`
   - Implementar Material3 com cores premium personalizadas
   - Suporte a tema escuro/claro com transições suaves
   - Paleta de cores premium (gradientes, glassmorphism)

2. **Novos Ícones e Assets**
   - Adicionar ícones premium (Material Symbols)
   - Criar splash screen premium
   - Novos ícones de launcher

3. **Componentes UI Premium**
   - Cards com elevação e sombras premium
   - Botões com animações fluidas
   - Bottom sheets modernos
   - Diálogos com design premium
   - Loading states animados

4. **Telas a Redesenhar**
   - Tela inicial (home) com layout premium
   - Player com controles premium
   - Configurações com design moderno
   - Lista de canais com cards premium

### 2.2 Sistema Inteligente de Resolução de Mídia

#### Novos Módulos a Criar:
1. **`core/media-resolver`** - Sistema de resolução de URLs
   - `MediaResolver.kt` - Interface principal
   - `RedirectResolver.kt` - Resolução de redirecionamentos HTTP
   - `YouTubeResolver.kt` - Extração de streams YouTube
   - `UrlCache.kt` - Cache de URLs resolvidas

2. **Componentes do Media Resolver**:
   ```kotlin
   // RedirectResolver.kt
   - Configurar OkHttpClient com followRedirects
   - Suporte a Cross-Protocol Redirects
   - User-Agent customizável
   - Timeout configurável
   
   // YouTubeResolver.kt
   - Integração com APIs Cobalt/Piped
   - Extração de manifestos HLS (.m3u8)
   - Suporte a múltiplas qualidades
   - Failover automático entre APIs
   
   // UrlCache.kt
   - Timestamp de última atualização
   - Verificação de validade (5 horas)
   - Atualização silenciosa em background
   ```

### 2.3 Persistência de Dados (Anti-Limpeza de Cache)

#### Modificações no Módulo `data`:
1. **Nova Entidade Room**
   ```kotlin
   @Entity(tableName = "resolved_urls")
   data class ResolvedUrl(
       @PrimaryKey val originalUrl: String,
       val resolvedUrl: String,
       val timestamp: Long,
       val expiresAt: Long,
       val quality: String?,
       val headers: Map<String, String>?
   )
   ```

2. **DAO para URLs Resolvidas**
   ```kotlin
   @Dao
   interface ResolvedUrlDao {
       @Query("SELECT * FROM resolved_urls WHERE originalUrl = :url")
       suspend fun getResolvedUrl(url: String): ResolvedUrl?
       
       @Insert(onConflict = OnConflictStrategy.REPLACE)
       suspend fun insertResolvedUrl(url: ResolvedUrl)
       
       @Query("DELETE FROM resolved_urls WHERE expiresAt < :currentTime")
       suspend fun deleteExpiredUrls(currentTime: Long)
   }
   ```

3. **DataStore para Configurações**
   - Armazenar configurações do MediaResolver
   - Lista de APIs de fallback
   - Preferências de qualidade

### 2.4 Otimização de Bateria (Lazy-Update)

#### Implementação:
1. **UrlUpdateManager.kt**
   ```kotlin
   class UrlUpdateManager {
       private val UPDATE_THRESHOLD = 5 * 60 * 60 * 1000L // 5 horas
       
       suspend fun shouldUpdate(url: ResolvedUrl): Boolean {
           val currentTime = System.currentTimeMillis()
           return (currentTime - url.timestamp) >= UPDATE_THRESHOLD
       }
       
       suspend fun updateInBackground(url: String) {
           // Atualização silenciosa sem bloquear UI
       }
   }
   ```

2. **Lifecycle Observer**
   - Verificação OnResume do app
   - Sem processos em background com tela bloqueada
   - Atualização assíncrona durante navegação

### 2.5 Integração com Players

#### Modificações em `business/channel`:
1. **PlayerViewModel Aprimorado**
   ```kotlin
   class ChannelViewModel {
       private val mediaResolver: MediaResolver
       
       suspend fun playChannel(channel: Channel) {
           val resolvedUrl = mediaResolver.resolve(channel.url)
           when (playerEngine) {
               PlayerEngine.EXOPLAYER -> playWithExoPlayer(resolvedUrl)
               PlayerEngine.VLC -> playWithVLC(resolvedUrl)
           }
       }
   }
   ```

2. **Configurações de Player**
   - ExoPlayer: DefaultHttpDataSource.Factory com headers
   - LibVLC: network-caching=1500
   - Suporte a redirecionamentos

### 2.6 Sistema de Failover

#### Implementação:
1. **ApiFailoverManager.kt**
   ```kotlin
   class ApiFailoverManager {
       private val primaryApis = listOf("cobalt.api", "piped.api")
       private val fallbackApis = loadFromRemote() // Firebase/GitHub
       
       suspend fun resolveWithFailover(url: String): String {
           for (api in primaryApis + fallbackApis) {
               try {
                   return api.resolve(url)
               } catch (e: Exception) {
                   continue
               }
           }
           throw NoAvailableApiException()
       }
   }
   ```

2. **Arquivo JSON Remoto**
   - Lista de instâncias de backup
   - Versionamento de APIs
   - Health check automático

## 3. GitHub Actions - Build Automatizado

### Workflow a Criar:
```yaml
name: Android Build

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
    
    - name: Grant execute permission for gradlew
      run: chmod +x gradlew
    
    - name: Build with Gradle
      run: ./gradlew assembleRelease
    
    - name: Sign APK
      uses: r0adkll/sign-android-release@v1
      with:
        releaseDirectory: app/universal/build/outputs/apk/release
        signingKeyBase64: ${{ secrets.SIGNING_KEY }}
        alias: ${{ secrets.ALIAS }}
        keyStorePassword: ${{ secrets.KEY_STORE_PASSWORD }}
        keyPassword: ${{ secrets.KEY_PASSWORD }}
    
    - name: Upload APK
      uses: actions/upload-artifact@v4
      with:
        name: app-release
        path: app/universal/build/outputs/apk/release/*.apk
```

## 4. Cronograma de Implementação

### Fase 1: UI Premium (Prioridade Alta)
- [ ] Criar sistema de temas premium
- [ ] Redesenhar tela inicial
- [ ] Redesenhar player
- [ ] Adicionar novos ícones e assets

### Fase 2: Media Resolver (Prioridade Alta)
- [ ] Criar módulo core/media-resolver
- [ ] Implementar RedirectResolver
- [ ] Implementar YouTubeResolver
- [ ] Implementar UrlCache

### Fase 3: Persistência e Otimização (Prioridade Alta)
- [ ] Criar entidades Room para URLs
- [ ] Implementar UrlUpdateManager
- [ ] Adicionar Lifecycle Observer

### Fase 4: Integração e Failover (Prioridade Média)
- [ ] Integrar MediaResolver com PlayerViewModel
- [ ] Implementar ApiFailoverManager
- [ ] Criar arquivo JSON de fallback

### Fase 5: GitHub Actions (Prioridade Alta)
- [ ] Criar workflow de build
- [ ] Configurar assinatura de APK
- [ ] Testar build automatizado

## 5. Dependências Adicionais

```kotlin
// build.gradle.kts (projeto)
dependencies {
    // OkHttp para resolução de redirecionamentos
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Retrofit para APIs externas
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    
    // DataStore para persistência
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // Coroutines para operações assíncronas
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

## 6. Considerações de Segurança

- Armazenar tokens e URLs no armazenamento interno privado
- Não usar cache temporário para dados críticos
- Implementar ofuscação de código (ProGuard/R8)
- Validar todas as URLs antes de processar
- Implementar rate limiting para APIs externas

## 7. Testes e Validação

- Testes unitários para MediaResolver
- Testes de integração para persistência
- Testes de UI com Compose Testing
- Testes de performance (bateria e memória)
- Testes de build no GitHub Actions
