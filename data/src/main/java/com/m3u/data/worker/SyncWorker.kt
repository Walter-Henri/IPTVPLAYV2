package com.m3u.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.m3u.data.database.model.DataSource
import com.m3u.data.parser.xtream.XtreamInput
import com.m3u.data.repository.playlist.PlaylistRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.coroutineScope
import timber.log.Timber

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val playlistRepository: PlaylistRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = coroutineScope {
        Timber.d("SyncWorker: Starting auto-sync")
        try {
            // Itera por todas as playlists registradas
            val playlists = playlistRepository.getAll()
            Timber.d("SyncWorker: Found ${playlists.size} playlists to refresh")

            playlists.forEach { playlist ->
                try {
                    Timber.d("SyncWorker: Syncing ${playlist.title} (${playlist.url})")
                    when (playlist.source) {
                        DataSource.M3U -> {
                            // A lógica de Merge no m3uOrThrow preservará os favoritos
                            playlistRepository.m3uOrThrow(playlist.title, playlist.url) {
                                // Callback de progresso (ignorado no background)
                            }
                        }
                        DataSource.Xtream -> {
                            val input = XtreamInput.decodeFromPlaylistUrlOrNull(playlist.url)
                            if (input != null) {
                                playlistRepository.xtreamOrThrow(
                                    title = playlist.title,
                                    basicUrl = input.basicUrl,
                                    username = input.username,
                                    password = input.password,
                                    type = input.type
                                ) {
                                    // Callback de progresso
                                }
                            }
                        }
                        else -> {
                            Timber.d("SyncWorker: Unsupported source type ${playlist.source}")
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "SyncWorker: Failed to refresh playlist ${playlist.title}")
                }
            }
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "SyncWorker: Error during sync")
            Result.retry()
        }
    }
}