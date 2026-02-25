package com.m3u.core.plugin

import com.m3u.data.database.dao.ChannelDao
import com.m3u.data.database.dao.PlaylistDao

interface RemoteServiceDependencies {
    val playlistDao: PlaylistDao
    val channelDao: ChannelDao
}