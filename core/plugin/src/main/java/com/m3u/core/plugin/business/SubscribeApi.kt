package com.m3u.core.plugin.business

import com.m3u.core.plugin.model.*

interface SubscribeApi {
    suspend fun addPlaylist(playlist: Playlist): Result
    suspend fun addChannel(channel: Channel): Result
    
    suspend fun obtainPlaylists(): ObtainPlaylistsResponse
    suspend fun obtainChannels(playlist: Playlist): ObtainPlaylistsResponse

    @Deprecated("Use addPlaylist(playlist: Playlist) overload instead")
    suspend fun addPlaylist(req: AddPlaylistRequest): Result
    
    @Deprecated("Use addChannel(channel: Channel) overload instead")
    suspend fun addChannel(req: AddChannelRequest): Result
}
