package com.m3u.plugin.dropbox

import android.content.Context
import android.util.Log
import com.dropbox.core.DbxException
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.oauth.DbxCredential
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.DownloadErrorException
import com.m3u.plugin.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class DropboxRepository(
    private val context: Context
) {
    private val TAG = "DropboxRepository"
    
    // Credentials from BuildConfig
    private val appKey = BuildConfig.DROPBOX_APP_KEY
    private val appSecret = BuildConfig.DROPBOX_APP_SECRET
    private val refreshToken = BuildConfig.DROPBOX_REFRESH_TOKEN
    
    // File identification
    private val path = "/channels.json"
    private val fileId = "id:s0nh2shbg49m00cbwnwhn"

    private val client: DbxClientV2 by lazy {
        val config = DbxRequestConfig.newBuilder("Projeto-Play").build()
        val credential = DbxCredential(
            "", // Initial access token empty
            -1L, // Expiration
            refreshToken,
            appKey,
            appSecret
        )
        DbxClientV2(config, credential)
    }

    /**
     * Downloads channels.json from Dropbox and saves it to internal storage.
     * @return The saved local file
     */
    suspend fun downloadChannelsJson(): File? = withContext(Dispatchers.IO) {
        val localFile = File(context.filesDir, "channels.json")
        
        try {
            Log.d(TAG, "Starting download from Dropbox: $path")
            
            // Force token refresh
            Log.d(TAG, "Refreshing Dropbox access token...")
            client.refreshAccessToken()
            Log.d(TAG, "Dropbox token refreshed successfully")
            
            FileOutputStream(localFile).use { outputStream ->
                try {
                    // Try by ID first
                    val metadata = client.files().download(fileId).download(outputStream)
                    Log.d(TAG, "Success downloading via ID: ${metadata.name}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to download via ID, trying via Path: ${e.message}")
                    // Fallback to Path
                    val metadata = client.files().download(path).download(outputStream)
                    Log.d(TAG, "Success downloading via Path: ${metadata.name}")
                }
            }
            
            localFile
        } catch (e: DownloadErrorException) {
            Log.e(TAG, "Path/Download error: ${e.errorValue}")
            null
        } catch (e: DbxException) {
            Log.e(TAG, "Dropbox API error: ${e.message}")
            null
        } catch (e: IOException) {
            Log.e(TAG, "I/O error saving file: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in Dropbox: ${e.message}")
            null
        }
    }
}
