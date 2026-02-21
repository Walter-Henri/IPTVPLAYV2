package com.m3u.data.repository.programme

import androidx.paging.PagingData
import com.m3u.data.database.model.Programme
import com.m3u.data.database.model.ProgrammeRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface for managing programmes.
 */
interface ProgrammeRepository {
    /**
     * Returns a flow of paging data for programmes associated with the given playlist URL and relation ID.
     *
     * @param playlistUrl the URL of the playlist
     * @param relationId the ID of the relation
     * @return a flow of paging data for programmes
     */
    fun pagingProgrammes(
        playlistUrl: String,
        relationId: String
    ): Flow<PagingData<Programme>>

    /**
     * Returns a flow of programme range for the given playlist URL and relation ID.
     *
     * @param playlistUrl the URL of the playlist
     * @param relationId the ID of the relation
     * @return a flow of programme range
     */
    fun observeProgrammeRange(
        playlistUrl: String,
        relationId: String
    ): Flow<ProgrammeRange>

    /**
     * Returns a flow of programme range for the given playlist URL.
     *
     * @param playlistUrl the URL of the playlist
     * @return a flow of programme range
     */
    fun observeProgrammeRange(
        playlistUrl: String
    ): Flow<ProgrammeRange>

    /**
     * The list of EPG URLs that are currently being refreshed.
     */
    val refreshingEpgUrls: StateFlow<List<String>>

    /**
     * Checks or refreshes programmes for the given playlist URLs and returns a flow of the count of programmes.
     *
     * @param playlistUrls the URLs of the playlists to check or refresh
     * @param ignoreCache whether to ignore the cache
     * @return a flow of the count of programmes
     */
    fun checkOrRefreshProgrammesOrThrow(
        vararg playlistUrls: String,
        ignoreCache: Boolean
    ): Flow<Int>

    /**
     * Retrieves a programme by its ID.
     *
     * @param id the ID of the programme
     * @return the programme with the given ID, or null if not found
     */
    suspend fun getById(id: Int): Programme?

    /**
     * Retrieves the programme currently being displayed on the given channel ID.
     *
     * @param channelId the ID of the channel
     * @return the programme currently being displayed, or null if not found
     */
    suspend fun getProgrammeCurrently(channelId: Int): Programme?
}