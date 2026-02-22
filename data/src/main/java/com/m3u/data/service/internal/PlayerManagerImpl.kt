package com.m3u.data.service.internal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.rtmp.RtmpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.MediaExtractorCompat
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.TrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
import androidx.media3.muxer.FragmentedMp4Muxer
import androidx.media3.muxer.Mp4Muxer
import androidx.media3.session.MediaSession
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.InAppFragmentedMp4Muxer
import androidx.media3.transformer.InAppMp4Muxer
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.Muxer
import com.m3u.core.architecture.Publisher
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.ReconnectMode
import com.m3u.core.architecture.preferences.Settings
import com.m3u.core.architecture.preferences.AutoResumePreferences
import com.m3u.core.foundation.IdentityRotator
import com.m3u.core.foundation.JsonHeaderRegistry
import com.m3u.core.mediaresolver.MediaResolver
import com.m3u.core.mediaresolver.ResolveResult
import com.m3u.core.mediaresolver.StreamFormat
import com.m3u.core.wrapper.Resource
import com.m3u.data.SSLs
import com.m3u.data.api.OkhttpClient
import com.m3u.data.database.model.AdjacentChannels
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.copyXtreamEpisode
import com.m3u.data.database.model.copyXtreamSeries
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.service.MediaCommand
import com.m3u.data.service.PlayerManager
import com.m3u.data.service.PlaybackStateManager
import com.m3u.data.iptv.UrlSanitizer
import com.m3u.data.iptv.HeaderProvider
import com.m3u.data.iptv.StreamValidator
import com.m3u.data.security.UrlPolicy
import com.m3u.data.service.internal.player.PlayerController
import com.m3u.data.service.internal.player.ExoPlayerController
import com.m3u.data.service.internal.player.VlcPlayerController
import com.m3u.data.service.internal.player.WebPlayController
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.JavaNetCookieJar
import java.net.CookieManager
import java.net.CookiePolicy
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

class PlayerManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @OkhttpClient(false) private val okHttpClient: OkHttpClient,
    private val playlistRepository: PlaylistRepository,
    private val channelRepository: ChannelRepository,
    private val cache: Cache,
    private val settings: Settings,
    publisher: Publisher,
    private val playbackStateManager: PlaybackStateManager,
    private val mediaResolver: MediaResolver,
) : PlayerManager, Player.Listener, MediaSession.Callback {
    private val timber = Timber.tag("PlayerManagerImpl")
    private val mainCoroutineScope = CoroutineScope(Dispatchers.Main)
    private val ioCoroutineScope = CoroutineScope(Dispatchers.IO)

    private var currentController: PlayerController? = null

    private val channelPreferenceProvider = ChannelPreferenceProvider(
        directory = context.cacheDir.resolve("channel-preferences"),
        appVersion = publisher.versionCode
    )

    private val continueWatchingCondition = ContinueWatchingCondition.getInstance<Player>()

           val loadControl: LoadControl by lazy {
        val bufferMs = settings.bufferMs.value.coerceAtLeast(0)
        val minBufferMs = if (bufferMs > 0) bufferMs else 30_000
        val maxBufferMs = if (bufferMs > 0) bufferMs * 2 else 60_000
        
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                minBufferMs,
                maxBufferMs,
                2000,
                4000
            )
            .setBackBuffer(30_000, false)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

    override val player = MutableStateFlow<Player?>(null)
    override val size = MutableStateFlow(Rect())

    override val playbackPosition = MutableStateFlow(0L)
    override val duration = MutableStateFlow(0L)
    
    private var currentSurface: android.view.Surface? = null
    private val mediaCommand = MutableStateFlow<MediaCommand?>(null)

    override val playlist: StateFlow<Playlist?> = mediaCommand.flatMapLatest { command ->
        when (command) {
            is MediaCommand.Common -> {
                val channel = channelRepository.get(command.channelId)
                channel?.let { playlistRepository.observe(it.playlistUrl) } ?: flow { }
            }

            is MediaCommand.XtreamEpisode -> {
                val channel = channelRepository.get(command.channelId)
                channel?.let {
                    playlistRepository
                        .observe(it.playlistUrl)
                        .map { prev -> prev?.copyXtreamSeries(channel) }
                } ?: flowOf(null)
            }

            null -> flowOf(null)
        }
    }
        .stateIn(
            scope = ioCoroutineScope,
            initialValue = null,
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    override val channel: StateFlow<Channel?> = mediaCommand
        .onEach { timber.d("received media command: $it") }
        .flatMapLatest { command ->
            when (command) {
                is MediaCommand.Common -> channelRepository.observe(command.channelId)
                is MediaCommand.XtreamEpisode -> channelRepository
                    .observe(command.channelId)
                    .map { it?.copyXtreamEpisode(command.episode) }

                else -> flowOf(null)
            }
        }
        .stateIn(
            scope = ioCoroutineScope,
            initialValue = null,
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    override val playbackState = MutableStateFlow<@Player.State Int>(Player.STATE_IDLE)
    override val playerEngine = MutableStateFlow(0)
    override val playbackException = MutableStateFlow<PlaybackException?>(null)
    override val isPlaying = MutableStateFlow(false)
    override val tracksGroups = MutableStateFlow<List<Tracks.Group>>(emptyList())

    private var retryCount = 0
    private var currentPlayerEngine = -1

    init {
        mainCoroutineScope.launch {
            playbackState.collectLatest { state ->
                timber.d("onPlaybackStateChanged: $state")
                when (state) {
                    Player.STATE_IDLE -> onPlaybackIdle()
                    Player.STATE_BUFFERING -> onPlaybackBuffering()
                    Player.STATE_READY -> onPlaybackReady()
                    Player.STATE_ENDED -> onPlaybackEnded()
                }
            }
        }
         mainCoroutineScope.launch {
            playbackException.collect { exception ->
                // SMART FALLBACK: Se o ExoPlayer (0) falhar, tenta VLC (1) AUTOMATICAMENTE
                if (exception != null && currentPlayerEngine == 0 && retryCount < 1) {
                    timber.w("Playback falhou no ExoPlayer. Tentando fallback inteligente para LibVLC...")
                    retryCount++
                    val currentCommand = mediaCommand.value
                    if (currentCommand != null) {
                        mainCoroutineScope.launch {
                            // NÃO mudamos a preferência global, apenas tentamos no motor 1
                            delay(800) 
                            tryPlay(
                                applyContinueWatching = true,
                                forceEngine = 1 // Passamos o motor 1 diretamente
                            )
                        }
                    }
                }
            }
        }
        mainCoroutineScope.launch {
            while (true) {
                ensureActive()
                // Sync position and duration from current controller
                currentController?.let {
                   playbackPosition.value = it.playbackPosition.value
                   duration.value = it.duration.value
                }
                delay(1.seconds)
            }
        }
    }

    override suspend fun play(
        command: MediaCommand,
        applyContinueWatching: Boolean
    ) {
        timber.d("play")
        retryCount = 0 // Reset retry count for new playback
        // NÃO chamar stop() aqui - deixar o controller atual ativo
        // Apenas resetar se mudarmos de engine
        mediaCommand.value = command
        val channel = when (command) {
            is MediaCommand.Common -> channelRepository.get(command.channelId)
            is MediaCommand.XtreamEpisode -> channelRepository
                .get(command.channelId)
                ?.copyXtreamEpisode(command.episode)
        }
        if (channel != null) {
            val channelUrlRaw = channel.url
            
            // Pipeline de tratamento profissional
            val sanitizedUrl = UrlSanitizer.sanitize(channelUrlRaw)
            
            if (!UrlPolicy.isSafe(sanitizedUrl)) {
                timber.e("URL bloqueada por política de segurança: $sanitizedUrl")
                return
            }

            val channelUrl = sanitizedUrl.stripKodiOptions()
            val licenseType = channel.licenseType.orEmpty()
            val licenseKey = channel.licenseKey.orEmpty()

            channelRepository.reportPlayed(channel.id)

            val playlist = playlistRepository.get(channel.playlistUrl)
            val userAgent = getUserAgent(channelUrlRaw, playlist)

            when (val resolved = mediaResolver.resolve(channelUrlRaw, false)) {
                is ResolveResult.Success -> {
                    tryPlay(
                        url = resolved.resolvedUrl,
                        applyContinueWatching = applyContinueWatching,
                        resolvedHeaders = resolved.headers
                    )
                }
                is ResolveResult.Error -> {
                    // Extrai headers Kodi da URL original para fallback
                    val kodiHeaders = if (channelUrlRaw.contains("|")) {
                        channelUrlRaw.substringAfter("|").split("&")
                            .filter { it.contains("=") }
                            .associate { 
                                val parts = it.split("=", limit = 2)
                                parts[0] to parts.getOrElse(1) { "" }
                            }
                    } else emptyMap()
                    val baseUrl = channelUrlRaw.stripKodiOptions()
                    
                    tryPlay(
                        url = baseUrl,
                        userAgent = userAgent,
                        licenseType = licenseType,
                        licenseKey = licenseKey,
                        applyContinueWatching = applyContinueWatching,
                        resolvedHeaders = kodiHeaders.ifEmpty { null }
                    )
                }
            }
        }
    }

    override suspend fun playUrl(url: String, applyContinueWatching: Boolean) {
        timber.d("playUrl")
        release()
        mediaCommand.value = null
        val userAgent = getUserAgent(url, null)
        when (val resolved = mediaResolver.resolve(url, false)) {
            is ResolveResult.Success -> {
                val resolvedUrl = resolved.resolvedUrl
                val resolvedUa = resolved.headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value ?: userAgent
                tryPlay(
                    url = resolvedUrl,
                    userAgent = resolvedUa,
                    licenseType = "",
                    licenseKey = "",
                    applyContinueWatching = applyContinueWatching,
                    resolvedHeaders = resolved.headers
                )
            }
            is ResolveResult.Error -> {
                // Extrai headers Kodi da URL original para fallback
                val kodiHeaders = if (url.contains("|")) {
                    url.substringAfter("|").split("&")
                        .filter { it.contains("=") }
                        .associate { 
                            val parts = it.split("=", limit = 2)
                            parts[0] to parts.getOrElse(1) { "" }
                        }
                } else emptyMap()
                val baseUrl = url.stripKodiOptions()
                
                tryPlay(
                    url = baseUrl,
                    userAgent = userAgent,
                    licenseType = "",
                    licenseKey = "",
                    applyContinueWatching = applyContinueWatching,
                    resolvedHeaders = kodiHeaders.ifEmpty { null }
                )
            }
        }
    }

    private var extractor: MediaExtractorCompat? = null
    private suspend fun tryPlay(
        url: String = channel.value?.url.orEmpty(),
        userAgent: String? = getUserAgent(channel.value?.url.orEmpty(), playlist.value),
        licenseType: String = channel.value?.licenseType.orEmpty(),
        licenseKey: String = channel.value?.licenseKey.orEmpty(),
        applyContinueWatching: Boolean,
        resolvedHeaders: Map<String, String>? = null,
        forceEngine: Int? = null
    ) {
        // Validar URL antes de processar
        if (url.isBlank()) {
            timber.e("tryPlay, URL vazia ou nula")
            playbackException.value = PlaybackException(
                "URL inválida",
                null,
                PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE
            )
            return
        }
        
        val sanitizedUrl = url.stripKodiOptions()
        timber.d("tryPlay, Original URL: $url")
        timber.d("tryPlay, Sanitized URL: $sanitizedUrl")
        
        val protocol = try {
            Url(sanitizedUrl).protocol.name
        } catch (e: Exception) {
            "http" // Fallback
        }
        
        val rtmp: Boolean = protocol == "rtmp"
        val isTunneling: Boolean = settings.get(PreferencesKeys.TUNNELING) ?: false
        val currentEngine = forceEngine ?: settings.playerEngine.value
        val baseHeaders: Map<String, String> = resolvedHeaders ?: getRequestHeaders(url, playlist.value)
        
        // Merge with dynamic headers from APP 2 (Youtube Extractor)
        val dynamicHeaders = com.m3u.core.foundation.JsonHeaderRegistry.getHeadersForUrl(sanitizedUrl)
        
        // CORREÇÃO: Priorizar User-Agent vindo dos headers (Extensão) em vez de re-parsear a URL sanitizada
        var userAgent = baseHeaders["User-Agent"] 
            ?: baseHeaders.entries.find { it.key.equals("User-Agent", true) }?.value
            ?: getUserAgent(url, playlist.value)
        
        timber.d("=== HEADER RESOLUTION DEBUG ===")
        timber.d("URL: ${sanitizedUrl.take(60)}...")
        timber.d("Base headers count: ${baseHeaders.size}")
        timber.d("Dynamic headers from Registry: ${dynamicHeaders?.keys ?: "NONE"}")
        
        val headers = if (dynamicHeaders != null) {
            timber.d("✓ Using headers from JsonHeaderRegistry (extracted)")
            // Requirement 2: Ensure UA consistency
            dynamicHeaders["User-Agent"]?.let { userAgent = it }
            baseHeaders.toMutableMap().apply {
                putAll(dynamicHeaders) // Registry headers override URL headers
                // Force YouTube Referer and Origin for Google domains
                if (sanitizedUrl.contains("googlevideo.com") || sanitizedUrl.contains("youtube.com") || sanitizedUrl.contains("youtu.be")) {
                    if (!containsKey("Referer")) put("Referer", "https://www.youtube.com/")
                    if (!containsKey("Origin")) put("Origin", "https://www.youtube.com")
                    if (!containsKey("User-Agent")) put("User-Agent", userAgent ?: "")
                }
            }
        } else {
            timber.d("⚠ No headers in Registry, using URL headers only")
            baseHeaders.toMutableMap().apply {
                if (sanitizedUrl.contains("googlevideo.com") || sanitizedUrl.contains("youtube.com") || sanitizedUrl.contains("youtu.be")) {
                    if (!containsKey("Referer")) put("Referer", "https://www.youtube.com/")
                    if (!containsKey("Origin")) put("Origin", "https://www.youtube.com")
                }
                // Garante que o User-Agent resolvido (da extensão ou fallback) esteja presente
                put("User-Agent", userAgent ?: "")
            }
        }
        
        timber.d("Final headers: ${headers.keys}")
        timber.d("Final User-Agent: ${headers["User-Agent"]?.take(40)}...")

        // Fix: Force recreation if we have custom headers (crucial for YouTube/IPTV with rotated headers)
        // or if the engine changed. Reusing the controller reuses the DataSourceFactory with OLD headers.
        val shouldRecreate = playerEngine.value != currentEngine || headers.isNotEmpty()
        
        if (currentPlayerEngine != -1 && shouldRecreate) {
             timber.d("tryPlay, switching/recreating engine. New: $currentEngine, Headers: ${headers.isNotEmpty()}")
             release()
        }
        currentPlayerEngine = currentEngine
        playerEngine.value = currentEngine

        if (currentController != null && shouldRecreate) {
            timber.d("♻ Controller recreation forced (Headers present: ${headers.isNotEmpty()})")
            currentController?.release()
            currentController = null
        }

        if (currentController == null) {
            currentController = when (currentEngine) {
                1 -> VlcPlayerController(context)
                2 -> WebPlayController(context)
                else -> {
                    val dataSourceFactory = if (rtmp) RtmpDataSource.Factory() else createDataSourceFactory(sanitizedUrl, userAgent, headers)
                    val extractorsFactory = DefaultExtractorsFactory()
                        .setTsExtractorFlags(
                            FLAG_ALLOW_NON_IDR_KEYFRAMES or 
                            FLAG_DETECT_ACCESS_UNITS or 
                            androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM
                        )
                    val mediaSourceFactory = buildMediaSourceFactory(dataSourceFactory, extractorsFactory, licenseType, licenseKey)
                    
                    // Zapping optimization for ExoPlayer
                    if (settings.zappingMode.value) {
                        // Apply low-latency/zapping specific configs if needed
                    }
                    
                    ExoPlayerController(context, mediaSourceFactory, isTunneling)
                }
            }
            
            // Sync states from controller to PlayerManager flows
            mainCoroutineScope.launch {
                currentController?.playbackState?.collect { playbackState.value = it }
            }
            mainCoroutineScope.launch {
                currentController?.isPlaying?.collect { isPlaying.value = it }
            }
            mainCoroutineScope.launch {
                currentController?.videoSize?.collect { size.value = it }
            }
            mainCoroutineScope.launch {
                currentController?.playbackException?.collect { playbackException.value = it }
            }
            mainCoroutineScope.launch {
                currentController?.tracksGroups?.collect { tracksGroups.value = it }
            }
            mainCoroutineScope.launch {
                currentController?.playbackPosition?.collect { playbackPosition.value = it }
            }
            mainCoroutineScope.launch {
                currentController?.duration?.collect { duration.value = it }
            }
            
            player.value = currentController?.playerObject as? Player
            
            // Re-apply surface if we have one BEFORE starting playback
            currentSurface?.let {
                timber.d("Re-aplicando surface ao novo controller")
                currentController?.setSurface(it)
            }
        }

        // Garantir que a superfície está pronta antes de iniciar playback
        delay(100)
        
        currentController?.play(sanitizedUrl, headers)
        
        mainCoroutineScope.launch {
            if (applyContinueWatching) {
                // Restore position if possible (mostly for non-live content)
                val pos = getCwPosition(sanitizedUrl)
                if (pos > 0) currentController?.seekTo(pos)
            }
        }
    }

    private fun buildMediaSourceFactory(
        dataSourceFactory: DataSource.Factory,
        extractorsFactory: DefaultExtractorsFactory,
        licenseType: String,
        licenseKey: String
    ): MediaSource.Factory {
        timber.d("buildMediaSourceFactory")
        
        // Política de erro mais resiliente para IPTV
        val loadErrorPolicy = object : DefaultLoadErrorHandlingPolicy() {
            override fun getMinimumLoadableRetryCount(dataType: Int): Int {
                return when (dataType) {
                    C.DATA_TYPE_MANIFEST -> 20 // Aumentado para manifestos instáveis
                    C.DATA_TYPE_MEDIA -> 20 // Aumentado para segmentos de mídia
                    else -> 10
                }
            }
            
            override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
                return 500L * (1 shl (loadErrorInfo.errorCount - 1).coerceAtMost(4))
            }
        }
        
        val base = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
            .setLoadErrorHandlingPolicy(loadErrorPolicy)

        if (licenseType.isNotEmpty()) {
            val drmCallback = when {
                (licenseType in arrayOf(Channel.LICENSE_TYPE_CLEAR_KEY, Channel.LICENSE_TYPE_CLEAR_KEY_2)) && !licenseKey.startsWith("http") ->
                    LocalMediaDrmCallback(licenseKey.toByteArray())
                else -> HttpMediaDrmCallback(licenseKey, dataSourceFactory)
            }
            val uuid = when (licenseType) {
                Channel.LICENSE_TYPE_CLEAR_KEY, Channel.LICENSE_TYPE_CLEAR_KEY_2 -> C.CLEARKEY_UUID
                Channel.LICENSE_TYPE_WIDEVINE -> C.WIDEVINE_UUID
                Channel.LICENSE_TYPE_PLAY_READY -> C.PLAYREADY_UUID
                else -> C.UUID_NIL
            }
            if (uuid != C.UUID_NIL && FrameworkMediaDrm.isCryptoSchemeSupported(uuid)) {
                val drmSessionManager = DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(uuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .setMultiSession(licenseType !in arrayOf(Channel.LICENSE_TYPE_CLEAR_KEY, Channel.LICENSE_TYPE_CLEAR_KEY_2))
                    .build(drmCallback)
                base.setDrmSessionManagerProvider { drmSessionManager }
            }
        }
        return base
    }

    override suspend fun replay() {
        val prev = mediaCommand.value
        release()
        prev?.let { play(it, applyContinueWatching = false) }
    }

    override fun release() {
        timber.d("release")
        extractor = null
        currentController?.release()
        currentController = null
        player.value = null
        
        mediaCommand.value = null
        size.value = Rect()
        playbackState.value = Player.STATE_IDLE
        playbackException.value = null
        tracksGroups.value = emptyList()
    }

    override fun clearCache() {
        cache.keys.forEach {
            cache.getCachedSpans(it).forEach { span ->
                cache.removeSpan(span)
            }
        }
    }

    override fun chooseTrack(group: TrackGroup, index: Int) {
        currentController?.chooseTrack(group, index)
    }

    override fun clearTrack(type: @C.TrackType Int) {
        currentController?.clearTrack(type)
    }

    override fun play() {
        currentController?.resume()
    }

    override fun pause() {
        currentController?.pause()
    }

    override fun seekTo(position: Long) {
        currentController?.seekTo(position)
    }

    override fun seekForward() {
        currentController?.seekForward()
    }

    override fun seekBack() {
        currentController?.seekBack()
    }

    override fun setSurface(surface: android.view.Surface?) {
        this.currentSurface = surface
        currentController?.setSurface(surface)
    }

    override val cacheSpace: Flow<Long> = flow {
        while (true) {
            emit(cache.cacheSpace)
            delay(1.seconds)
        }
    }
        .flowOn(Dispatchers.IO)

    override suspend fun reloadThumbnail(channelUrl: String): Uri? {
        val channelPreference = getChannelPreference(channelUrl)
        return channelPreference?.thumbnail
    }

    private val thumbnailDir by lazy {
        context.cacheDir.resolve("thumbnails").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    override suspend fun syncThumbnail(channelUrl: String): Uri? = withContext(Dispatchers.IO) {
        val thumbnail = Codecs.getThumbnail(context, channelUrl.toUri()) ?: return@withContext null
        val filename = UUID.randomUUID().toString() + ".jpeg"
        val file = File(thumbnailDir, filename)
        while (!file.createNewFile()) {
            ensureActive()
            file.delete()
        }
        FileOutputStream(file).use {
            thumbnail.compress(Bitmap.CompressFormat.JPEG, 50, it)
        }
        val uri = file.toUri()
        addChannelPreference(
            channelUrl,
            getChannelPreference(channelUrl)?.copy(
                thumbnail = uri
            ) ?: ChannelPreference(thumbnail = uri)
        )
        uri
    }

    private fun createPlayer(
        mediaSourceFactory: MediaSource.Factory,
        tunneling: Boolean,
        engine: Int
    ): ExoPlayer {
        timber.d("createPlayer, creating player with engine: $engine")

        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(createTrackSelector(tunneling))
            .setLoadControl(loadControl)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NONE)
            .build()
        
        if (engine == 2) {
             timber.w("Using ExoPlayer as fallback for MediaPlayer engine")
        }

        val attributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        
        player.setAudioAttributes(attributes, true)
        player.playWhenReady = true
        player.addListener(this@PlayerManagerImpl)
        
        return player
    }

    private fun createTrackSelector(tunneling: Boolean): TrackSelector {
        return DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setForceHighestSupportedBitrate(true)
                    // Habilitar tunelamento se disponível pelo hardware para reduzir carga na CPU (Ideal para Smart TVs)
                    .setTunnelingEnabled(tunneling)
            )
        }
    }

    private val renderersFactory: RenderersFactory by lazy {
        androidx.media3.exoplayer.DefaultRenderersFactory(context).apply {
            // Prioridade total para aceleração de hardware
            setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            setEnableDecoderFallback(true) // Se o hardware falhar, usa software
            
            // Suporte a codecs modernos HEVC e AV1
            setMediaCodecSelector(androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT)
        }
    }

    private fun createDataSourceFactory(url: String, userAgent: String?, headers: Map<String, String>): DataSource.Factory {
        timber.d("createDataSourceFactory, url: $url, userAgent: $userAgent, headers: ${headers.keys}")
        
        // Usar headers fornecidos (que já foram mesclados com os dinâmicos em tryPlay)
        val finalHeaders = if (headers.isEmpty()) HeaderProvider.getDefaultHeaders() else headers
        val finalUserAgent = userAgent ?: finalHeaders["User-Agent"] ?: HeaderProvider.getDefaultHeaders()["User-Agent"]
        
        // Criar OkHttpClient customizado com timeouts maiores para IPTV
        val customOkHttpClient = okHttpClient.newBuilder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val request = chain.request()
                val urlString = request.url.toString()
                
                // Requirement 2: Consistent Header Injection across all engines
                val isYouTube = urlString.contains("googlevideo.com") || 
                                urlString.contains("youtube.com") || 
                                urlString.contains("youtu.be")
                
                val builder = request.newBuilder()
                
                if (isYouTube) {
                    // Inject critical headers if missing or incorrect
                    if (request.header("Referer").isNullOrBlank()) {
                        builder.header("Referer", "https://www.youtube.com/")
                    }
                    if (request.header("Origin").isNullOrBlank()) {
                        builder.header("Origin", "https://www.youtube.com")
                    }
                    // Ensure User-Agent is consistent
                    if (request.header("User-Agent").isNullOrBlank()) {
                        builder.header("User-Agent", finalUserAgent ?: HeaderProvider.getUserAgent())
                    }
                    
                    timber.v("→ [Handled] YouTube Request: ${request.method} ${urlString.take(80)}...")
                }
                
                val newRequest = builder.build()
                val response = chain.proceed(newRequest)
                
                if (isYouTube && !response.isSuccessful) {
                    timber.w("← YouTube Response Error: ${response.code} for ${urlString.take(60)}...")
                }
                
                response
            }
            .cookieJar(JavaNetCookieJar(CookieManager().apply {
                setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER)
            }))
            .protocols(listOf(Protocol.HTTP_1_1)) // HTTP/1.1 é mais estável para alguns streams HLS legados
            .apply {
                sslSocketFactory(SSLs.socketFactory, SSLs.TLSTrustAll)
                hostnameVerifier { _, _ -> true }
            }
            .addNetworkInterceptor { chain ->
                // Este interceptor roda APÓS redirecionamentos, mostrando os headers REAIS na saída
                val request = chain.request()
                if (request.url.toString().contains("googlevideo.com")) {
                    timber.v(">>> WIRE: ${request.method} ${request.url} | UA: ${request.header("User-Agent")} | Ref: ${request.header("Referer")}")
                }
                chain.proceed(request)
            }
            .build()
        
        val httpDataSourceFactory = OkHttpDataSource.Factory(customOkHttpClient)
            .setUserAgent(finalUserAgent ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .setDefaultRequestProperties(
                headers.toMutableMap().apply {
                    // Garantir que User-Agent também esteja nas propriedades padrão como fallback
                    if (finalUserAgent != null) put("User-Agent", finalUserAgent)
                }
            )
        
        // Não usar cache para live streams IPTV - causa problemas de reprodução
        return DefaultDataSource.Factory(context, httpDataSourceFactory)
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        super.onVideoSizeChanged(videoSize)
        timber.d("onVideoSizeChanged, [${videoSize.toRect()}]")
        size.value = videoSize.toRect()
    }

    override fun onPlaybackStateChanged(state: Int) {
        super.onPlaybackStateChanged(state)
        playbackState.value = state
        if (state == Player.STATE_READY) {
            retryCount = 0
        }
    }

    override fun onPlayerErrorChanged(exception: PlaybackException?) {
        super.onPlayerErrorChanged(exception)
        val error = exception ?: run {
            playbackException.value = null
            return
        }

        timber.e(error, "onPlayerErrorChanged: ${PlaybackException.getErrorCodeName(error.errorCode)}")
        
        when (error.errorCode) {
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> handleBehindLiveWindowError()
            in NETWORK_ERROR_CODES -> handleNetworkError(error)
            in PARSING_ERROR_CODES -> handleParsingError(error)
            else -> handleOtherErrors(error)
        }
    }

    override fun onTracksChanged(tracks: Tracks) {
        super.onTracksChanged(tracks)
        player.value?.isPlaying
        tracksGroups.value = tracks.groups
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        this.isPlaying.value = isPlaying
    }

    override fun pauseOrContinue(value: Boolean) {
        player.value?.apply {
            if (!value) pause() else {
                playWhenReady = true
                prepare()
            }
        }
    }

    override fun updateSpeed(race: Float) {
        player.value?.apply {
            setPlaybackSpeed(race.coerceAtLeast(0.1f))
        }
    }

    override suspend fun recordVideo(uri: Uri) {
        withContext(Dispatchers.Main) {
            try {
                val currentPlayer = player.value ?: return@withContext
                val (mimeType, muxerFactory) = resolveMuxerConfig(currentPlayer) ?: return@withContext
                val transformer = buildTransformer(mimeType, muxerFactory)
                
                transformer.start(
                    MediaItem.fromUri(channel.value?.url.orEmpty()),
                    uri.path.orEmpty()
                )
            } finally {
                timber.d("Record frame completed")
            }
        }
    }

    private fun resolveMuxerConfig(player: Player): Pair<String, Muxer.Factory>? {
        val tracksGroup = player.currentTracks.groups.find { it.type == C.TRACK_TYPE_VIDEO } ?: return null
        val formats = (0 until tracksGroup.length).mapNotNull {
            if (!tracksGroup.isTrackSupported(it)) null
            else tracksGroup.getTrackFormat(it)
        }.mapNotNull { it.containerMimeType ?: it.sampleMimeType }

        return when {
            formats.any { it in FragmentedMp4Muxer.SUPPORTED_VIDEO_SAMPLE_MIME_TYPES } -> {
                val mimeType = formats.first { it in FragmentedMp4Muxer.SUPPORTED_VIDEO_SAMPLE_MIME_TYPES }
                mimeType to InAppFragmentedMp4Muxer.Factory()
            }
            formats.any { it in Mp4Muxer.SUPPORTED_VIDEO_SAMPLE_MIME_TYPES } -> {
                val mimeType = formats.first { it in Mp4Muxer.SUPPORTED_VIDEO_SAMPLE_MIME_TYPES }
                mimeType to InAppMp4Muxer.Factory()
            }
            else -> {
                timber.e("Unsupported video formats for recording: $formats")
                null
            }
        }
    }

    private fun buildTransformer(mimeType: String, muxerFactory: Muxer.Factory): Transformer {
        return Transformer.Builder(context)
            .setMuxerFactory(muxerFactory)
            .setVideoMimeType(mimeType)
            .setEncoderFactory(
                DefaultEncoderFactory.Builder(context.applicationContext)
                    .setEnableFallback(true)
                    .build()
            )
            .addListener(createTransformerListener())
            .build()
    }

    private fun createTransformerListener() = object : Transformer.Listener {
        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
            super.onCompleted(composition, exportResult)
            timber.d("transformer, onCompleted")
        }

        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
            super.onError(composition, exportResult, exportException)
            timber.e(exportException, "transformer, onError")
        }
    }

    override val cwPositionObserver = MutableSharedFlow<Long>(replay = 1)

    override suspend fun onResetPlayback(channelUrl: String) {
        cwPositionObserver.emit(-1L)
        resetContinueWatching(channelUrl, ignorePositionCondition = true)
        val currentPlayer = player.value ?: return
        if (currentPlayer.isCommandAvailable(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)) {
            currentPlayer.seekToDefaultPosition()
        }
    }

    override suspend fun getCwPosition(channelUrl: String): Long {
        val channelPreference = getChannelPreference(channelUrl)
        return channelPreference?.cwPosition ?: -1L
    }

    private suspend fun onPlaybackIdle() {}
    private suspend fun onPlaybackBuffering() {}

    private suspend fun onPlaybackReady() {
        timber.d("onPlaybackReady")
        
        // Salva o estado de reprodução para auto-resume/auto-retorno
        val currentChannel = channel.value
        val currentPlaylist = playlist.value
        
        if (currentChannel != null) {
            timber.d("Salvando último canal assistido: ${currentChannel.title}")
            settings.update(AutoResumePreferences.LAST_CHANNEL_ID, currentChannel.id)
            settings.update(AutoResumePreferences.LAST_STREAM_URL, currentChannel.url)
            settings.update(AutoResumePreferences.LAST_PLAYLIST_URL, currentChannel.playlistUrl)
            settings.update(AutoResumePreferences.LAST_PLAYBACK_TIMESTAMP, System.currentTimeMillis().toString())

            if (currentPlaylist != null) {
                timber.d("Salvando estado de reprodução detalhado...")
                playbackStateManager.savePlaybackState(
                    channelId = currentChannel.id,
                    streamUrl = currentChannel.url,
                    categoryName = currentChannel.category,
                    playlistUrl = currentPlaylist.url
                )
            }
            storeContinueWatching(currentChannel.url)
        }
    }

    private suspend fun onPlaybackEnded() {
        if (settings[PreferencesKeys.RECONNECT_MODE] == ReconnectMode.RECONNECT) {
            mainCoroutineScope.launch { replay() }
        }
        channel.value?.url?.let { channelUrl ->
            if (channelUrl.isNotEmpty()) {
                resetContinueWatching(channelUrl)
            }
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun storeContinueWatching(channelUrl: String) {
        timber.d("start storeContinueWatching")
        // avoid memory leaks caused by loops
        fun checkContinueWatching(): Boolean {
            val currentPlayer = player.value ?: return false
            return continueWatchingCondition.isStoringSupported(currentPlayer)
        }
        if (!checkContinueWatching()) {
            timber.w("failed to storeContinueWatching, playback is not supported.")
            return
        }
        playbackPosition
            .sample(5.seconds)
            .onEach { timber.d("storeContinueWatching, sampled position: $it") }
            .filter { it != -1L }
            .distinctUntilChanged()
            .collect { cwPosition ->
                timber.d("storeContinueWatching, saving position: $cwPosition")
                val channelPreference = getChannelPreference(channelUrl)
                addChannelPreference(
                    channelUrl,
                    channelPreference?.copy(cwPosition = cwPosition)
                        ?: ChannelPreference(cwPosition = cwPosition)
                )
            }
    }

    private suspend fun restoreContinueWatching(player: Player, channelUrl: String) {
        val channelPreference = getChannelPreference(channelUrl)
        val cwPosition = channelPreference?.cwPosition?.takeIf { it != -1L } ?: run {
            cwPositionObserver.emit(-1L)
            return
        }
        withContext(Dispatchers.Main) {
            if (continueWatchingCondition.isRestoringSupported(player)) {
                timber.d("restoreContinueWatching, $cwPosition")
                cwPositionObserver.emit(cwPosition)
                player.seekTo(cwPosition)
            }
        }
    }

    private suspend fun resetContinueWatching(
        channelUrl: String,
        ignorePositionCondition: Boolean = false
    ) {
        timber.d("resetContinueWatching, channelUrl=$channelUrl, ignorePositionCondition=$ignorePositionCondition")
        val channelPreference = getChannelPreference(channelUrl)
        val player = this@PlayerManagerImpl.player.value
        withContext(Dispatchers.Main) {
            if (player != null && continueWatchingCondition.isResettingSupported(
                    player,
                    ignorePositionCondition
                )
            ) {
                addChannelPreference(
                    channelUrl,
                    channelPreference?.copy(cwPosition = -1L) ?: ChannelPreference(cwPosition = -1L)
                )
            }
        }
    }



    /**
     * Get the kodi url options like this:
     * http://host[:port]/directory/file?a=b&c=d|option1=value1&option2=value2
     * Will get:
     * {option1=value1, option2=value2}
     *
     * https://kodi.wiki/view/HTTP
     */
    private fun String.readKodiUrlOptions(): Map<String, String?> {
        val index = this.indexOf('|')
        if (index == -1) return emptyMap()
        val options = this.drop(index + 1).split("&")
        return options
            .filter { it.isNotBlank() }
            .associate {
                val splitIndex = it.indexOf('=')
                if (splitIndex != -1) {
                    val key = it.substring(0, splitIndex)
                    val value = it.substring(splitIndex + 1)
                    key to value
                } else {
                    it to null
                }
            }
    }

    private fun String.stripKodiOptions(): String {
        val index = this.indexOf('|')
        return if (index == -1) this else this.substring(0, index)
    }

    /**
     * Read user-agent appended to the channelUrl.
     * If there is no result from url, it will use playlist user-agent instead.
     */
    private fun getUserAgent(channelUrl: String, playlist: Playlist?): String? {
        val kodiUrlOptions = channelUrl.readKodiUrlOptions()
        val userAgent = kodiUrlOptions[KodiAdaptions.HTTP_OPTION_UA] 
            ?: kodiUrlOptions.entries.firstOrNull { it.key.equals(KodiAdaptions.HTTP_OPTION_UA, ignoreCase = true) }?.value
            ?: playlist?.userAgent
        return userAgent ?: "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
    }

    private fun getRequestHeaders(channelUrl: String, playlist: Playlist?): Map<String, String> {
        val options = channelUrl.readKodiUrlOptions()
        val headers = mutableMapOf<String, String>()
        
        // 1. Aplicar headers padrão inteligentes baseados na URL
        headers.putAll(HeaderProvider.getHeadersForUrl(channelUrl.stripKodiOptions()))

        // 2. Sobrescrever com todos os headers encontrados nas opções do link (Kodi)
        options.forEach { (key, value) ->
            if (!value.isNullOrBlank()) {
                // Remove variações de case para garantir que a opção do link ganhe
                val existingKey = headers.keys.find { it.equals(key, true) }
                if (existingKey != null) headers.remove(existingKey)
                headers[key] = value
            }
        }

        // 3. Lógica específica para User-Agent (playlist fallback ou sistema)
        val ua = getUserAgent(channelUrl, playlist)
        if (!ua.isNullOrBlank()) {
            val keysToRemove = headers.keys.filter { it.equals("user-agent", true) }
            keysToRemove.forEach { headers.remove(it) }
            headers["User-Agent"] = ua
        }

        return headers
    }

    private suspend fun getChannelPreference(channelUrl: String): ChannelPreference? {
        if (channelUrl.isEmpty()) return null
        return channelPreferenceProvider[channelUrl]
    }

    private suspend fun addChannelPreference(
        channelUrl: String,
        channelPreference: ChannelPreference
    ) {
        if (channelUrl.isEmpty()) return
        channelPreferenceProvider[channelUrl] = channelPreference
    }

    private fun handleBehindLiveWindowError() {
        timber.w("onPlayerErrorChanged, ERROR_CODE_BEHIND_LIVE_WINDOW, tentando reiniciar")
        player.value?.apply {
            seekToDefaultPosition()
            prepare()
        }
    }

    private fun handleNetworkError(exception: PlaybackException) {
        timber.e("onPlayerErrorChanged, Erro de rede: ${PlaybackException.getErrorCodeName(exception.errorCode)}, retry: $retryCount")
        if (retryCount < MAX_RETRIES_NETWORK) {
            val retryDelay = (retryCount + 1) * RETRY_DELAY_NETWORK
            retryCount++
            ioCoroutineScope.launch {
                delay(retryDelay)
                withContext(Dispatchers.Main) {
                    player.value?.let { 
                        it.prepare()
                        it.playWhenReady = true
                    } ?: replay()
                }
            }
        } else {
            handleNetworkRetryLimitExceeded(exception)
        }
    }

    private fun handleNetworkRetryLimitExceeded(exception: PlaybackException) {
        timber.e("Limite de tentativas de rede excedido. Tentando próximo canal.")
        retryCount = 0
        ioCoroutineScope.launch {
            val current = channel.value ?: return@launch
            val adjacent = channelRepository.observeAdjacentChannels(
                current.id,
                current.playlistUrl,
                current.category
            ).firstOrNull()
            adjacent?.nextId?.let { play(MediaCommand.Common(it)) } ?: run {
                playbackException.value = exception
            }
        }
    }

    private fun handleParsingError(exception: PlaybackException) {
        timber.e("handleParsingError: ${exception.message}")
        handleOtherErrors(exception)
    }

    private fun handleOtherErrors(exception: PlaybackException) {
        val cause = exception.cause
        if (cause is HttpDataSource.InvalidResponseCodeException && cause.responseCode in RETRYABLE_HTTP_CODES) {
            timber.w("Erro HTTP detectado (${cause.responseCode}). Iniciando protocolo de recuperação...")
            if (handleHttpRetry(cause)) return
        }
        timber.e(exception, PlaybackException.getErrorCodeName(exception.errorCode))
        if (retryCount >= MAX_RETRIES_GENERIC) {
            timber.e("Máximo de tentativas genéricas atingido.")
            playbackException.value = exception
        } else {
            retryCount++
            ioCoroutineScope.launch {
                delay(GENERIC_RETRY_DELAY)
                withContext(Dispatchers.Main) { player.value?.prepare() }
            }
        }
    }

    private fun handleHttpRetry(exception: HttpDataSource.InvalidResponseCodeException): Boolean {
        if (retryCount < MAX_RETRIES_HTTP) {
            val isForbidden = exception.responseCode == 403 || exception.responseCode == 401
            val url = exception.dataSpec.uri.toString()
            
            retryCount++
            
            if (isForbidden) {
                timber.i("Detectado 403/401 em $url. Rodando identidade (UA)...")
                
                val currentHeaders = JsonHeaderRegistry.getHeadersForUrl(url) ?: emptyMap()
                val currentUA = currentHeaders["User-Agent"] ?: "Mozilla/5.0"
                val nextUA = IdentityRotator.getNextUA(currentUA)
                
                val newHeaders = currentHeaders.toMutableMap().apply {
                    put("User-Agent", nextUA)
                    if (!containsKey("Referer")) put("Referer", "https://www.youtube.com/")
                }
                
                // Atualiza o registro para a próxima tentativa
                JsonHeaderRegistry.setHeadersForUrl(url, newHeaders)
                timber.d("Identidade alterada para: ${nextUA.take(30)}...")
            }

            ioCoroutineScope.launch {
                delay(HTTP_RETRY_DELAY)
                timber.i("Repetindo playback (tentativa $retryCount/$MAX_RETRIES_HTTP)...")
                replay()
            }
            return true
        }
        return false
    }

    companion object {
        private const val MAX_RETRIES_NETWORK = 3
        private const val RETRY_DELAY_NETWORK = 2000L
        private const val MAX_RETRIES_HTTP = 5
        private const val HTTP_RETRY_DELAY = 2000L
        private const val MAX_RETRIES_GENERIC = 10
        private const val GENERIC_RETRY_DELAY = 1000L
        
        private val NETWORK_ERROR_CODES = listOf(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED
        )
        
        private val PARSING_ERROR_CODES = listOf(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED
        )
        
        private val RETRYABLE_HTTP_CODES = listOf(401, 403, 404)
    }
}

fun VideoSize.toRect(): Rect {
    return Rect(0, 0, width, height)
}

