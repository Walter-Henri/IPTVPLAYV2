package com.m3u.data.repository.playlist

import android.content.Context
import android.net.Uri
import androidx.work.WorkManager
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.execute
import com.m3u.core.architecture.logger.sandBox
import com.m3u.data.database.dao.ChannelDao
import com.m3u.data.database.dao.PlaylistDao
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.PlaylistWithChannels
import com.m3u.data.database.model.toMap
import com.m3u.data.parser.m3u.M3UParser
import com.m3u.data.parser.m3u.toChannel
import com.m3u.data.parser.xtream.XtreamChannelInfo
import com.m3u.data.parser.xtream.XtreamParser
import com.m3u.data.parser.xtream.XtreamInput
import com.m3u.data.parser.xtream.XtreamLive
import com.m3u.data.parser.xtream.XtreamVod
import com.m3u.data.parser.xtream.XtreamSerial
import com.m3u.data.parser.xtream.toChannel as toXtreamChannel
import com.m3u.data.parser.xtream.asChannel as asXtreamChannel
import com.m3u.data.repository.createCoroutineCache
import com.m3u.data.worker.SubscriptionWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.m3u.data.api.OkhttpClient
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@Serializable
private data class ChannelsJson(
    val channels: List<ChannelJsonItem> = emptyList()
)

@Serializable
private data class ChannelJsonItem(
    val name: String,
    val url: String? = null,
    val m3u8: String? = null,
    val logo: String? = null,
    val logo_url: String? = null,
    val icon: String? = null,
    val thumbnail: String? = null,
    val group: String? = null,
    val category: String? = null,
    val success: Boolean = true,
    val headers: Map<String, String>? = null,
    val error: String? = null
)

internal class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val channelDao: ChannelDao,
    private val m3uParser: M3UParser,
    @OkhttpClient(false) private val okHttpClient: OkHttpClient,
    private val logger: Logger,
    private val xtreamParser: XtreamParser,
    @ApplicationContext private val context: Context
) : PlaylistRepository {

    override fun observeAll(): Flow<List<Playlist>> = playlistDao.observeAll()

    override fun observeAllEpgs(): Flow<List<Playlist>> = playlistDao.observeAllEpgs()

    override fun observePlaylistUrls(): Flow<List<String>> = playlistDao.observePlaylistUrls()

    override fun observe(url: String): Flow<Playlist?> = playlistDao.observeByUrl(url)

    override fun observePlaylistWithChannels(url: String): Flow<PlaylistWithChannels?> = 
        playlistDao.observeByUrlWithChannels(url)

    override suspend fun get(url: String): Playlist? = playlistDao.get(url)

    override suspend fun getById(id: Int): Playlist? = playlistDao.getById(id)

    override suspend fun getAll(): List<Playlist> = playlistDao.getAll()

    override suspend fun getAllAutoRefresh(): List<Playlist> = playlistDao.getAllAutoRefresh()

    override suspend fun getBySource(source: DataSource): List<Playlist> = playlistDao.getBySource(source)

    override suspend fun getPlaylistWithChannels(url: String): PlaylistWithChannels? = 
        playlistDao.getByUrlWithChannels(url)

    override suspend fun onUpdatePlaylistTitle(url: String, title: String) = 
        playlistDao.updateTitle(url, title)

    override suspend fun onUpdatePlaylistUrl(id: Int, newUrl: String) = 
        playlistDao.updateUrl(id, newUrl)

    override suspend fun unsubscribe(url: String): Playlist? = logger.sandBox {
        val playlist = playlistDao.get(url)
        playlistDao.deleteByUrl(url)
        channelDao.deleteByPlaylistUrl(url)
        playlist
    }

    override suspend fun addVirtualStream(title: String, url: String): Int {
        val allPlaylists = playlistDao.getAll()
        val activePlaylist = allPlaylists.firstOrNull { it.active && it.source != DataSource.EPG && !it.url.startsWith("virtual:") }
        val firstPlaylist = allPlaylists.firstOrNull { it.source != DataSource.EPG && !it.url.startsWith("virtual:") }
        val userPlaylist = activePlaylist ?: firstPlaylist
        val playlistUrl = userPlaylist?.url ?: "virtual:channels_json"
        
        if (userPlaylist == null && playlistDao.get(playlistUrl) == null) {
            playlistDao.insertOrReplace(
                Playlist(title = "Canais Importados", url = playlistUrl, source = DataSource.M3U)
            )
        }
        
        val existing = channelDao.getByPlaylistUrlAndUrl(playlistUrl, url)
        return if (existing != null) {
            channelDao.update(existing.copy(title = title, category = "Extraídos"))
            existing.id
        } else {
            channelDao.insertOrReplace(
                Channel(
                    url = url,
                    title = title,
                    category = "Extraídos",
                    playlistUrl = playlistUrl
                )
            ).toInt()
        }
    }

    override suspend fun importChannelsJson(retryTimes: Int): Int {
        logger.log("importChannelsJson via Data layer is deprecated. Use Plugin.")
        return 0
    }

    override suspend fun importChannelsJsonBody(body: String): Int = withContext(Dispatchers.IO) {
        try {
            // Log do JSON completo para debug
            Timber.d("JSON recebido da extensão (primeiros 500 chars): ${body.take(500)}")
            
            val json = Json { 
                ignoreUnknownKeys = true 
                coerceInputValues = true
            }
            val data = json.decodeFromString<ChannelsJson>(body)
            
            // CRITICAL: Register headers in JsonHeaderRegistry for PlayerManager
            Timber.d("Registrando ${data.channels.size} canais no JsonHeaderRegistry...")
            
            // Tenta encontrar uma playlist real do usuário para "fundir" os canais
            val allPlaylists = playlistDao.getAll()
            val activePlaylist = allPlaylists.firstOrNull { it.active && it.source != DataSource.EPG && !it.url.startsWith("virtual:") }
            val firstPlaylist = allPlaylists.firstOrNull { it.source != DataSource.EPG && !it.url.startsWith("virtual:") }
            
            val userPlaylist = activePlaylist ?: firstPlaylist
            val playlistUrl = userPlaylist?.url ?: "virtual:channels_json"
            
            // Se cair no fallback virtual, garante que a playlist de cabeçalho existe
            if (userPlaylist == null && playlistDao.get(playlistUrl) == null) {
                playlistDao.insertOrReplace(
                    Playlist(
                        title = "Canais Importados",
                        url = playlistUrl,
                        source = DataSource.M3U
                    )
                )
            }

            val channels = data.channels
                .filter { it.success }
                .mapNotNull { item ->
                    val name = item.name
                    val group = item.group ?: item.category
                    var finalUrl = item.m3u8 ?: item.url
                    
                    if (name.isBlank() || group.isNullOrBlank() || finalUrl.isNullOrBlank()) {
                        null
                    } else {
                        // Extract clean URL (without Kodi options)
                        val cleanUrl = finalUrl!!.substringBefore("|")
                        
                        // Merge headers from JSON with any existing in URL
                        val allHeaders = mutableMapOf<String, String>()
                        
                        // 1. Parse existing headers from URL (Kodi format)
                        if (finalUrl.contains("|")) {
                            val optionsPart = finalUrl.substringAfter("|")
                            optionsPart.split("&").forEach { opt ->
                                val kv = opt.split("=", limit = 2)
                                if (kv.size == 2) allHeaders[kv[0]] = kv[1]
                            }
                        }
                        
                        // 2. Merge with headers from JSON (priority to JSON)
                        if (!item.headers.isNullOrEmpty()) {
                            allHeaders.putAll(item.headers)
                        }
                        
                        // 3. Register in JsonHeaderRegistry for PlayerManager
                        if (allHeaders.isNotEmpty()) {
                            com.m3u.core.foundation.JsonHeaderRegistry.setHeadersForUrl(cleanUrl, allHeaders)
                            Timber.d("✓ Registrado headers para $name: ${allHeaders.keys}")
                        }
                        
                        // 4. Rebuild URL with all headers (Kodi format)
                        finalUrl = if (allHeaders.isNotEmpty()) {
                            val options = allHeaders.entries.joinToString("&") { "${it.key}=${it.value}" }
                            "$cleanUrl|$options"
                        } else {
                            cleanUrl
                        }

                        // Try to find existing by URL first, then by Title as fallback for dynamic links
                        var existing = channelDao.getByPlaylistUrlAndUrl(playlistUrl, finalUrl!!)
                        if (existing == null && playlistUrl.startsWith("virtual:")) {
                             existing = channelDao.getByPlaylistUrlAndTitle(playlistUrl, name)
                        }

                        val cover = item.logo ?: item.logo_url ?: item.icon ?: item.thumbnail
                        val category = group
                        
                        // Log detalhado para debug dos logos
                        Timber.d("Processando canal: $name")
                        Timber.d("  - Logo do JSON: ${item.logo}")
                        Timber.d("  - Logo_url do JSON: ${item.logo_url}")
                        Timber.d("  - Icon do JSON: ${item.icon}")
                        Timber.d("  - Thumbnail do JSON: ${item.thumbnail}")
                        Timber.d("  - Cover final: $cover")
                        
                        if (existing != null) {
                            existing.copy(
                                url = finalUrl,
                                title = name,
                                category = category ?: "Geral",
                                cover = cover ?: ""
                            )
                        } else {
                            Channel(
                                url = finalUrl,
                                title = name,
                                category = category ?: "Geral",
                                cover = cover ?: "",
                                playlistUrl = playlistUrl
                            )
                        }
                    }
                }
            
            
            channelDao.insertOrReplaceAll(*channels.toTypedArray())
            channels.size
        } catch (e: Exception) {
            logger.execute { e.printStackTrace() }
            0
        }
    }

    override suspend fun m3uOrThrow(title: String, url: String, callback: (count: Int) -> Unit) {
        playlistDao.get(url) ?: Playlist(
            title = title,
            url = url,
            source = DataSource.M3U
        ).also { playlistDao.insertOrReplace(it) }
        
        try {
            val inputStream = withContext(Dispatchers.IO) {
                when {
                    url.startsWith("content://", ignoreCase = true) -> {
                        context.contentResolver.openInputStream(Uri.parse(url))
                    }
                    url.startsWith("file://", ignoreCase = true) -> {
                        val file = File(Uri.parse(url).path ?: return@withContext null)
                        if (file.exists()) file.inputStream() else null
                    }
                    else -> {
                        val request = Request.Builder().url(url).build()
                        okHttpClient.newCall(request).execute().body?.byteStream()
                    }
                }
            } ?: return
            
            val favOrHiddenRelationIds = channelDao.getFavOrHiddenRelationIdsByPlaylistUrl(url)
            val cache = createCoroutineCache<Channel>(BUFFER_M3U_CAPACITY) { all ->
                channelDao.insertOrReplaceAll(*all.toTypedArray())
                callback(all.size)
            }
            m3uParser.parse(inputStream)
                .map { it.toChannel(url) }
                .filterNot { it.relationId in favOrHiddenRelationIds }
                .onEach { cache.push(it) }
                .onCompletion { cache.flush() }
                .collect()
        } catch (e: Exception) {
            logger.execute { e.printStackTrace() }
            throw e
        }
    }

    override suspend fun xtreamOrThrow(
        title: String,
        basicUrl: String,
        username: String,
        password: String,
        type: String?,
        callback: (count: Int) -> Unit
    ) {
        val playlistUrl = XtreamInput.encodeToPlaylistUrl(
            XtreamInput(basicUrl, username, password, type)
        )
        playlistDao.get(playlistUrl) ?: Playlist(
            title = title,
            url = playlistUrl,
            source = DataSource.Xtream
        ).also { playlistDao.insertOrReplace(it) }

        val input = XtreamInput(basicUrl, username, password, type)
        val output = xtreamParser.getXtreamOutput(input)
        
        val liveCategories = output.liveCategories.associate { (it.categoryId?.toString() ?: "") to (it.categoryName ?: "Geral") }
        val vodCategories = output.vodCategories.associate { (it.categoryId?.toString() ?: "") to (it.categoryName ?: "Geral") }
        val serialCategories = output.serialCategories.associate { (it.categoryId?.toString() ?: "") to (it.categoryName ?: "Geral") }
        
        val containerPlugin = output.allowedOutputFormats.firstOrNull() ?: "ts"

        val cache = createCoroutineCache<Channel>(BUFFER_M3U_CAPACITY) { all ->
            channelDao.insertOrReplaceAll(*all.toTypedArray())
            callback(all.size)
        }

        xtreamParser.parse(input)
            .onEach { data ->
                val channel = when (data) {
                    is XtreamLive -> data.toXtreamChannel(
                        basicUrl = basicUrl,
                        username = username,
                        password = password,
                        playlistUrl = playlistUrl,
                        category = liveCategories[data.categoryId?.toString()] ?: "Geral",
                        containerPlugin = containerPlugin
                    )
                    is XtreamVod -> data.toXtreamChannel(
                        basicUrl = basicUrl,
                        username = username,
                        password = password,
                        playlistUrl = playlistUrl,
                        category = vodCategories[data.categoryId?.toString()] ?: "Geral"
                    )
                    is XtreamSerial -> data.asXtreamChannel(
                        basicUrl = basicUrl,
                        username = username,
                        password = password,
                        playlistUrl = playlistUrl,
                        category = serialCategories[data.categoryId?.toString()] ?: "Geral"
                    )
                    else -> null
                }
                if (channel != null) cache.push(channel)
            }
            .onCompletion { cache.flush() }
            .collect()
    }

    override suspend fun insertEpgAsPlaylist(title: String, epg: String) {
        val playlist = Playlist(title = title, url = epg, source = DataSource.EPG)
        playlistDao.insertOrReplace(playlist)
    }

    override suspend fun refresh(url: String) {
        val playlist = playlistDao.get(url) ?: return
        SubscriptionWorker.m3u(WorkManager.getInstance(context), playlist.title, playlist.url)
    }

    override suspend fun backupOrThrow(uri: Uri) {
        // Implementar backup
    }

    override suspend fun restoreOrThrow(uri: Uri) {
        // Implementar restore
    }

    override suspend fun updateActive(url: String) = playlistDao.updateActive(url)

    override suspend fun pinOrUnpinCategory(url: String, category: String) {
        playlistDao.updatePinnedCategories(url) { prev ->
            if (category in prev) prev - category else prev + category
        }
    }

    override suspend fun hideOrUnhideCategory(url: String, category: String) = 
        playlistDao.hideOrUnhideCategory(url, category)

    override suspend fun onUpdatePlaylistUserAgent(url: String, userAgent: String?) = 
        playlistDao.updateUserAgent(url, userAgent)

    override fun observeAllCounts(): Flow<Map<Playlist, Int>> = 
        playlistDao.observeAllCounts().map { it.toMap() }

    override suspend fun getCategoriesByPlaylistUrlIgnoreHidden(url: String, query: String): List<String> = 
        channelDao.getCategoriesByPlaylistUrl(url, query)

    override fun observeCategoriesByPlaylistUrlIgnoreHidden(url: String, query: String): Flow<List<String>> = 
        channelDao.observeCategoriesByPlaylistUrl(url, query)

    override suspend fun readEpisodesOrThrow(series: Channel): List<XtreamChannelInfo.Episode> {
        val input = XtreamInput.decodeFromPlaylistUrlOrNull(series.playlistUrl) ?: return emptyList()
        val seriesId = series.relationId?.toIntOrNull() ?: return emptyList()
        val info = xtreamParser.getSeriesInfoOrThrow(input, seriesId)
        return info.episodes.values.flatten()
    }

    override suspend fun deleteEpgPlaylistAndProgrammes(epgUrl: String) {
        playlistDao.deleteByUrl(epgUrl)
    }

    override suspend fun onUpdateEpgPlaylist(useCase: PlaylistRepository.EpgPlaylistUseCase) {
        when (useCase) {
            is PlaylistRepository.EpgPlaylistUseCase.Check -> {
                playlistDao.updateEpgUrls(useCase.playlistUrl) { prev ->
                    if (useCase.action) prev + useCase.epgUrl
                    else prev - useCase.epgUrl
                }
            }
            is PlaylistRepository.EpgPlaylistUseCase.Upgrade -> {
                // Implementar upgrade
            }
        }
    }

    override suspend fun onUpdatePlaylistAutoRefreshProgrammes(playlistUrl: String) {
        val playlist = playlistDao.get(playlistUrl) ?: return
        playlistDao.updatePlaylistAutoRefreshProgrammes(
            playlistUrl,
            !playlist.autoRefreshProgrammes
        )
    }

    companion object {
        private const val BUFFER_M3U_CAPACITY = 100
    }
}
