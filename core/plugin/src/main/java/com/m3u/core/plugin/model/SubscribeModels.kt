package com.m3u.core.plugin.model

import kotlinx.serialization.Serializable

@Serializable
data class Playlist(
    val url: String,
    val title: String,
    val user_agent: String? = null
)

@Serializable
data class Channel(
    val title: String,
    val url: String,
    val playlist_url: String,
    val cover: String? = null,
    val category: String? = null,
    val license_key: String? = null,
    val license_type: String? = null
)

@Serializable
data class AddPlaylistRequest(
    val url: String,
    val title: String,
    val user_agent: String? = null
)

@Serializable
data class AddChannelRequest(
    val title: String,
    val url: String,
    val playlist_url: String,
    val cover: String? = null,
    val category: String? = null,
    val license_key: String? = null,
    val license_type: String? = null
)

@Serializable
data class ObtainPlaylistsResponse(
    val playlists: List<Playlist> = emptyList()
)
