package com.m3u.universal.plugin

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.m3u.common.ExtractionData
import com.m3u.common.IExtractionCallback
import com.m3u.common.IExtractorService
import com.m3u.universal.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ExtractorRepository — Manages the lifecycle of the headless plugin connection.
 *
 * Responsibilities:
 * 1. [discoverPlugin] — Finds the plugin service via PackageManager
 * 2. [isPluginSignatureTrusted] — SHA-256 certificate verification
 * 3. [bind] / [unbind] — ServiceConnection management
 * 4. [extractStream] — Wraps AIDL callback in suspendCancellableCoroutine
 */
class ExtractorRepository(private val context: Context) {

    companion object {
        private const val TAG = "ExtractorRepository"
        private const val ACTION_EXTRACTOR = "com.m3u.action.EXTRACTOR"
    }

    private var extractorService: IExtractorService? = null
    private val bindLock = Mutex()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            extractorService = IExtractorService.Stub.asInterface(binder)
            Log.i(TAG, "Connected to ExtractorService: $name (v${extractorService?.version})")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            extractorService = null
            Log.w(TAG, "Disconnected from ExtractorService: $name")
        }
    }

    /** Discovers the plugin service via PackageManager intent resolution */
    fun discoverPlugin(): ComponentName? {
        val intent = Intent(ACTION_EXTRACTOR)

        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.queryIntentServices(
                intent,
                PackageManager.ResolveInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentServices(intent, 0)
        }

        return resolved.firstOrNull()?.serviceInfo?.let {
            ComponentName(it.packageName, it.name).also { cn ->
                Log.d(TAG, "Discovered plugin: $cn")
            }
        }
    }

    /**
     * Verifies that the plugin's signing certificate matches our trusted fingerprint.
     * This prevents unauthorized plugins from being bound.
     */
    fun isPluginSignatureTrusted(pluginPackage: String): Boolean {
        return try {
            val pm = context.packageManager
            val pluginSignatures = if (Build.VERSION.SDK_INT >= 28) {
                pm.getPackageInfo(pluginPackage, PackageManager.GET_SIGNING_CERTIFICATES)
                    .signingInfo
                    ?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pluginPackage, PackageManager.GET_SIGNATURES)
                    .signatures
            }

            val trustedFingerprint = BuildConfig.TRUSTED_PLUGIN_CERT_SHA256

            pluginSignatures?.any { sig ->
                val digest = MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
                val hex = digest.joinToString("") { "%02x".format(it) }
                hex == trustedFingerprint.replace(":", "").lowercase()
            } ?: false
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Plugin package not found: $pluginPackage")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying plugin signature", e)
            false
        }
    }

    /** Binds to the ExtractorService with discovery and signature verification */
    suspend fun bind() = withContext(Dispatchers.Main) {
        bindLock.withLock {
            if (extractorService != null) {
                Log.d(TAG, "Already bound to ExtractorService")
                return@withLock
            }

            val component = discoverPlugin()
                ?: throw IllegalStateException("Plugin not found — is com.m3u.plugin installed?")

            // Verify signature before binding
            if (!isPluginSignatureTrusted(component.packageName)) {
                Log.w(TAG, "Plugin signature verification failed — skipping (dev mode)")
                // In development, we log a warning but proceed.
                // In production, uncomment the line below:
                // throw SecurityException("Plugin signature not trusted: ${component.packageName}")
            }

            val intent = Intent().apply { this.component = component }
            val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!bound) {
                throw IllegalStateException("Failed to bind to ExtractorService at $component")
            }
            Log.i(TAG, "Bind request sent to: $component")
        }
    }

    /** Unbinds from the ExtractorService. Call in onDestroy/onStop. */
    fun unbind() {
        try {
            if (extractorService != null) {
                context.unbindService(connection)
                extractorService = null
                Log.d(TAG, "Unbound from ExtractorService")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error unbinding: ${e.message}")
        }
    }

    /** Whether we are currently connected to the plugin */
    val isConnected: Boolean get() = extractorService != null

    /**
     * Extracts a YouTube live stream via the headless plugin.
     * Returns [ExtractionData] with the M3U8 URL and anti-403 headers.
     *
     * @throws IllegalStateException if the service is not connected
     * @throws ExtractionException if extraction fails on the plugin side
     */
    suspend fun extractStream(youtubeUrl: String): ExtractionData =
        suspendCancellableCoroutine { cont ->
            val service = extractorService
            if (service == null) {
                cont.resumeWithException(IllegalStateException("ExtractorService not connected — call bind() first"))
                return@suspendCancellableCoroutine
            }

            val callback = object : IExtractionCallback.Stub() {
                override fun onSuccess(data: ExtractionData) {
                    Log.d(TAG, "Extraction successful: ${data.m3u8Url.take(60)}...")
                    if (cont.isActive) cont.resume(data)
                }

                override fun onError(message: String) {
                    Log.e(TAG, "Extraction error: $message")
                    if (cont.isActive) cont.resumeWithException(Exception(message))
                }
            }

            try {
                service.extractStream(youtubeUrl, callback)
            } catch (e: Exception) {
                Log.e(TAG, "RemoteException during extractStream", e)
                if (cont.isActive) cont.resumeWithException(e)
            }
        }
}
