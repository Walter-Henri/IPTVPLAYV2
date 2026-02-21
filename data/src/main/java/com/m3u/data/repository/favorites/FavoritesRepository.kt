package com.m3u.data.repository.favorites

import androidx.datastore.preferences.core.Preferences
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class FavoritesState(
    val favoriteChannels: List<Int> = emptyList(),
    val favoriteOrder: Map<String, Int> = emptyMap(),
    val categories: Map<String, List<Int>> = emptyMap(),
    val history: List<Int> = emptyList(),
    val frequent: Map<Int, Int> = emptyMap()
)

@Singleton
class FavoritesRepository @Inject constructor(
    private val settings: Settings
) {
    private val json = Json { ignoreUnknownKeys = true }

    val state: Flow<FavoritesState> = settings.favoritesJson
        .map { jsonStr ->
            val content = jsonStr.orEmpty()
            if (content.isBlank()) FavoritesState() else runCatching { json.decodeFromString<FavoritesState>(content) }.getOrElse { FavoritesState() }
        }

    suspend fun toggleFavorite(channelId: Int) {
        val current = load()
        val next = if (channelId in current.favoriteChannels) {
            current.copy(
                favoriteChannels = current.favoriteChannels.filter { it != channelId },
                favoriteOrder = current.favoriteOrder - channelId.toString()
            )
        } else {
            current.copy(
                favoriteChannels = current.favoriteChannels + channelId,
                favoriteOrder = current.favoriteOrder + (channelId.toString() to (current.favoriteOrder.size + 1)),
                history = listOf(channelId) + current.history.take(99),
                frequent = current.frequent + (channelId to ((current.frequent[channelId] ?: 0) + 1))
            )
        }
        save(next)
    }

    suspend fun reorderFavorites(order: List<Int>) {
        val current = load()
        val indexMap = order.mapIndexed { idx, id -> id.toString() to idx }.toMap()
        save(current.copy(favoriteOrder = indexMap))
    }

    suspend fun assignCategory(category: String, channelId: Int) {
        val current = load()
        val list = current.categories[category].orEmpty()
        val nextList = if (channelId in list) list else list + channelId
        save(current.copy(categories = current.categories + (category to nextList)))
    }

    suspend fun removeFromCategory(category: String, channelId: Int) {
        val current = load()
        val list = current.categories[category].orEmpty().filterNot { it == channelId }
        save(current.copy(categories = current.categories + (category to list)))
    }

    suspend fun importFromJson(content: String) {
        val parsed = runCatching { json.decodeFromString<FavoritesState>(content) }.getOrElse { FavoritesState() }
        save(parsed)
    }

    suspend fun exportToJson(): String {
        return json.encodeToString(load())
    }

    private suspend fun load(): FavoritesState {
        val raw = settings.get(PreferencesKeys.FAVORITES_JSON) ?: ""
        return if (raw.isBlank()) FavoritesState() else runCatching { json.decodeFromString<FavoritesState>(raw) }.getOrElse { FavoritesState() }
    }

    private suspend fun save(state: FavoritesState) {
        settings.update(PreferencesKeys.FAVORITES_JSON as Preferences.Key<String>, json.encodeToString(state))
    }
}
