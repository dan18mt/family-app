package com.familyhome.app.data.onboarding

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors Wi-Fi connectivity and exposes the current IPv4 address.
 *
 * When Wi-Fi reconnects (e.g. after IP reassignment), [onWifiReconnected] callbacks fire
 * so that callers can re-advertise NSD services or refresh the stored host IP.
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "NetworkMonitor"
    }

    private val connectivityManager =
        context.getSystemService(ConnectivityManager::class.java)

    private val _currentIp = MutableStateFlow<String?>(null)
    val currentIp: StateFlow<String?> = _currentIp.asStateFlow()

    private val _isWifiConnected = MutableStateFlow(false)
    val isWifiConnected: StateFlow<Boolean> = _isWifiConnected.asStateFlow()

    private val reconnectCallbacks = mutableListOf<() -> Unit>()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Network available: $network")
            _isWifiConnected.value = true
            refreshIp()
            reconnectCallbacks.forEach { it() }
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "Network lost: $network")
            _isWifiConnected.value = false
            _currentIp.value = null
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            val newIp = linkProperties.linkAddresses
                .mapNotNull { it.address as? Inet4Address }
                .firstOrNull()
                ?.hostAddress
            if (newIp != null && newIp != _currentIp.value) {
                Log.d(TAG, "IP changed to $newIp")
                _currentIp.value = newIp
                reconnectCallbacks.forEach { it() }
            }
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback: ${e.message}")
        }
        refreshIp()
    }

    /** Register a callback that fires whenever Wi-Fi reconnects or the IP changes. */
    fun addReconnectCallback(callback: () -> Unit) {
        reconnectCallbacks += callback
    }

    fun removeReconnectCallback(callback: () -> Unit) {
        reconnectCallbacks -= callback
    }

    /** Returns the current device IPv4 address on the active Wi-Fi network. */
    fun getCurrentIpv4(): String? {
        return connectivityManager.activeNetwork
            ?.let { connectivityManager.getLinkProperties(it) }
            ?.linkAddresses
            ?.mapNotNull { it.address as? Inet4Address }
            ?.firstOrNull()
            ?.hostAddress
            ?: fallbackIpFromWifi()
    }

    private fun refreshIp() {
        _currentIp.value = getCurrentIpv4()
    }

    @Suppress("DEPRECATION")
    private fun fallbackIpFromWifi(): String? {
        return try {
            val wifiManager =
                context.applicationContext.getSystemService(WifiManager::class.java)
            val ipInt = wifiManager.connectionInfo.ipAddress
            if (ipInt == 0) null
            else "%d.%d.%d.%d".format(
                ipInt and 0xFF,
                (ipInt shr 8) and 0xFF,
                (ipInt shr 16) and 0xFF,
                (ipInt shr 24) and 0xFF,
            )
        } catch (e: Exception) {
            null
        }
    }
}
