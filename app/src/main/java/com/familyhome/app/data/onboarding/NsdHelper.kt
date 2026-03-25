package com.familyhome.app.data.onboarding

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class DiscoveredDevice(
    val serviceName: String,
    val hostAddress: String,
    val port: Int,
)

/**
 * Wraps Android [NsdManager] for mDNS advertising and browsing.
 *
 * Service types:
 *  - Father advertises [FATHER_SERVICE_TYPE] on port 8765 — members browse for it.
 *  - Members advertise [MEMBER_SERVICE_TYPE] on port 8766 — Father browses for it.
 *
 * NSD only supports one simultaneous resolve, so found services are queued and
 * resolved one at a time.
 */
@Singleton
class NsdHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "NsdHelper"
        const val FATHER_SERVICE_TYPE = "_familyhome._tcp."
        const val MEMBER_SERVICE_TYPE  = "_familyhome_member._tcp."
    }

    private val nsdManager: NsdManager =
        context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    // Android NSD allows only one concurrent resolve — queue extras
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var isResolving = false

    // Track IPv6-only resolve attempts per service to allow retries before giving up
    private val resolveAttempts = mutableMapOf<String, Int>()

    // ── Advertising ──────────────────────────────────────────────────────────

    fun startAdvertising(serviceName: String, port: Int, serviceType: String) {
        stopAdvertising()
        val info = NsdServiceInfo().apply {
            this.serviceName = serviceName
            this.serviceType = serviceType
            this.port        = port
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(s: NsdServiceInfo, e: Int)   { Log.e(TAG, "Registration failed: $e") }
            override fun onUnregistrationFailed(s: NsdServiceInfo, e: Int) { Log.e(TAG, "Unregistration failed: $e") }
            override fun onServiceRegistered(s: NsdServiceInfo)            { Log.d(TAG, "NSD registered: ${s.serviceName}") }
            override fun onServiceUnregistered(s: NsdServiceInfo)          { Log.d(TAG, "NSD unregistered: ${s.serviceName}") }
        }
        registrationListener = listener
        nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stopAdvertising() {
        registrationListener?.let {
            runCatching { nsdManager.unregisterService(it) }
            registrationListener = null
        }
    }

    // ── Discovery ────────────────────────────────────────────────────────────

    fun startBrowsing(serviceType: String) {
        stopBrowsing()
        _discoveredDevices.value = emptyList()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(s: String, e: Int) { Log.e(TAG, "Discovery start failed: $e") }
            override fun onStopDiscoveryFailed(s: String, e: Int)  { Log.e(TAG, "Discovery stop failed: $e") }
            override fun onDiscoveryStarted(s: String)             { Log.d(TAG, "Discovery started: $s") }
            override fun onDiscoveryStopped(s: String)             { Log.d(TAG, "Discovery stopped: $s") }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${serviceInfo.serviceName}")
                enqueueResolve(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
                _discoveredDevices.update { list ->
                    list.filter { it.serviceName != serviceInfo.serviceName }
                }
            }
        }
        discoveryListener = listener
        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stopBrowsing() {
        discoveryListener?.let {
            runCatching { nsdManager.stopServiceDiscovery(it) }
            discoveryListener = null
        }
    }

    fun stopAll() {
        stopAdvertising()
        stopBrowsing()
    }

    fun clearDiscovered() {
        _discoveredDevices.value = emptyList()
    }

    // ── Resolve queue ────────────────────────────────────────────────────────

    private fun enqueueResolve(serviceInfo: NsdServiceInfo) {
        resolveQueue.addLast(serviceInfo)
        if (!isResolving) resolveNext()
    }

    private fun resolveNext() {
        val next = resolveQueue.removeFirstOrNull() ?: run { isResolving = false; return }
        isResolving = true
        nsdManager.resolveService(next, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
                isResolving = false
                resolveNext()
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                // Filter out IPv6 link-local addresses; keep only IPv4
                val rawAddress = serviceInfo.host?.hostAddress ?: run {
                    isResolving = false; resolveNext(); return
                }
                val address = if (rawAddress.contains('%')) rawAddress.substringBefore('%') else rawAddress
                if (address.contains(':')) {
                    // IPv6 address — retry up to 3 times hoping to get IPv4
                    val attempts = resolveAttempts.getOrDefault(serviceInfo.serviceName, 0)
                    if (attempts < 3) {
                        resolveAttempts[serviceInfo.serviceName] = attempts + 1
                        isResolving = false
                        enqueueResolve(serviceInfo)
                    } else {
                        Log.w(TAG, "Giving up IPv4 resolution for ${serviceInfo.serviceName} after $attempts retries")
                        resolveAttempts.remove(serviceInfo.serviceName)
                        isResolving = false
                        resolveNext()
                    }
                    return
                }
                resolveAttempts.remove(serviceInfo.serviceName)
                _discoveredDevices.update { list ->
                    val device = DiscoveredDevice(
                        serviceName = serviceInfo.serviceName,
                        hostAddress = address,
                        port        = serviceInfo.port,
                    )
                    if (list.any { it.serviceName == device.serviceName }) list else list + device
                }
                isResolving = false
                resolveNext()
            }
        })
    }
}
