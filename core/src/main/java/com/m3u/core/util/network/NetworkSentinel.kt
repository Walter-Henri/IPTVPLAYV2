package com.m3u.core.util.network

import android.content.Context
import android.Manifest
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Network Sentinel - Verifica e aguarda conectividade de rede
 */
object NetworkSentinel {
    
    /**
     * Verifica se há conexão de rede disponível
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    /**
     * Aguarda até que a rede esteja disponível ou até o timeout
     * @param context Contexto da aplicação
     * @param timeoutSeconds Tempo máximo de espera em segundos (padrão: 10s)
     * @return true se a rede ficou disponível, false se timeout
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    suspend fun waitForNetwork(context: Context, timeoutSeconds: Int = 10): Boolean {
        // Verifica se já tem rede disponível
        if (isNetworkAvailable(context)) {
            return true
        }
        
        // Aguarda até o timeout
        val result = withTimeoutOrNull(timeoutSeconds * 1000L) {
            suspendCancellableCoroutine { continuation ->
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                
                val networkCallback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        if (continuation.isActive) {
                            continuation.resume(true)
                        }
                    }
                    
                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities
                    ) {
                        if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                            if (continuation.isActive) {
                                continuation.resume(true)
                            }
                        }
                    }
                }
                
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    .build()
                
                connectivityManager.registerNetworkCallback(request, networkCallback)
                
                continuation.invokeOnCancellation {
                    connectivityManager.unregisterNetworkCallback(networkCallback)
                }
            }
        }
        
        return result ?: false
    }
    
    /**
     * Aguarda um delay configurável antes de iniciar a reprodução
     * Útil para permitir que o sistema operacional estabilize após o boot
     */
    suspend fun applyStartupDelay(delaySeconds: Int) {
        if (delaySeconds > 0) {
            delay(delaySeconds * 1000L)
        }
    }
}
