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
    private val networkMonitor: NetworkMonitor,
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

    /** Member devices discovered by the leader via [MEMBER_SERVICE_TYPE]. */
    private val _discoveredMembers = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredMembers: StateFlow<List<DiscoveredDevice>> = _discoveredMembers.asStateFlow()

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var memberDiscoveryListener: NsdManager.DiscoveryListener? = null

    /** Service type currently being browsed — used to restart on reconnect. */
    private var activeBrowseServiceType: String? = null
    private var activeMemberBrowseServiceType: String? = null

    /** Service name + type being advertised — used to restart on reconnect. */
    private var activeAdvertiseInfo: Triple<String, Int, String>? = null // name, port, type

    // Android NSD allows only one concurrent resolve — queue extras
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var isResolving = false

    // Track IPv6-only resolve attempts per service to allow retries before giving up
    private val resolveAttempts = mutableMapOf<String, Int>()

    init {
        // Re-advertise / re-browse when Wi-Fi reconnects (e.g., IP change)
        networkMonitor.addReconnectCallback {
            activeAdvertiseInfo?.let { (name, port, type) ->
                Log.d(TAG, "Network reconnected — re-advertising NSD service")
                startAdvertising(name, port, type)
            }
            activeBrowseServiceType?.let { type ->
                Log.d(TAG, "Network reconnected — restarting NSD browse for $type")
                startBrowsing(type)
            }
            activeMemberBrowseServiceType?.let { type ->
                Log.d(TAG, "Network reconnected — restarting NSD member browse for $type")
                startMemberBrowsing()
            }
        }
    }

    // ── Advertising ──────────────────────────────────────────────────────────

    fun startAdvertising(serviceName: String, port: Int, serviceType: String) {
        activeAdvertiseInfo = Triple(serviceName, port, serviceType)
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
        activeAdvertiseInfo = null
    }

    // ── Discovery ────────────────────────────────────────────────────────────

    fun startBrowsing(serviceType: String) {
        activeBrowseServiceType = serviceType
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
        activeBrowseServiceType = null
    }

    /**
     * Start browsing for member devices (used by the leader).
     * Results accumulate in [discoveredMembers].
     */
    fun startMemberBrowsing() {
        activeMemberBrowseServiceType = MEMBER_SERVICE_TYPE
        stopMemberBrowsing()
        _discoveredMembers.value = emptyList()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(s: String, e: Int) { Log.e(TAG, "Member discovery start failed: $e") }
            override fun onStopDiscoveryFailed(s: String, e: Int)  { Log.e(TAG, "Member discovery stop failed: $e") }
            override fun onDiscoveryStarted(s: String)             { Log.d(TAG, "Member discovery started: $s") }
            override fun onDiscoveryStopped(s: String)             { Log.d(TAG, "Member discovery stopped: $s") }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Member service found: ${serviceInfo.serviceName}")
                enqueueMemberResolve(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Member service lost: ${serviceInfo.serviceName}")
                _discoveredMembers.update { list ->
                    list.filter { it.serviceName != serviceInfo.serviceName }
                }
            }
        }
        memberDiscoveryListener = listener
        nsdManager.discoverServices(MEMBER_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stopMemberBrowsing() {
        memberDiscoveryListener?.let {
            runCatching { nsdManager.stopServiceDiscovery(it) }
            memberDiscoveryListener = null
        }
        activeMemberBrowseServiceType = null
        _discoveredMembers.value = emptyList()
    }

    fun stopAll() {
        stopAdvertising()
        stopBrowsing()
        stopMemberBrowsing()
    }

    fun clearDiscovered() {
        _discoveredDevices.value = emptyList()
    }

    // ── Resolve queue ────────────────────────────────────────────────────────

    private fun enqueueResolve(serviceInfo: NsdServiceInfo) {
        resolveQueue.addLast(serviceInfo)
        if (!isResolving) resolveNext()
    }

    /** Member services are resolved through a separate queue to avoid conflicts. */
    private val memberResolveQueue  = ArrayDeque<NsdServiceInfo>()
    private var isMemberResolving   = false
    private val memberResolveAttempts = mutableMapOf<String, Int>()

    private fun enqueueMemberResolve(serviceInfo: NsdServiceInfo) {
        memberResolveQueue.addLast(serviceInfo)
        if (!isMemberResolving) resolveNextMember()
    }

    private fun resolveNextMember() {
        val next = memberResolveQueue.removeFirstOrNull() ?: run { isMemberResolving = false; return }
        isMemberResolving = true
        nsdManager.resolveService(next, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Member resolve failed for ${serviceInfo.serviceName}: $errorCode")
                isMemberResolving = false
                resolveNextMember()
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val rawAddress = serviceInfo.host?.hostAddress ?: run {
                    isMemberResolving = false; resolveNextMember(); return
                }
                val address = if (rawAddress.contains('%')) rawAddress.substringBefore('%') else rawAddress
                if (address.contains(':')) {
                    val attempts = memberResolveAttempts.getOrDefault(serviceInfo.serviceName, 0)
                    if (attempts < 3) {
                        memberResolveAttempts[serviceInfo.serviceName] = attempts + 1
                        isMemberResolving = false
                        enqueueMemberResolve(serviceInfo)
                    } else {
                        Log.w(TAG, "Giving up IPv4 member resolution for ${serviceInfo.serviceName}")
                        memberResolveAttempts.remove(serviceInfo.serviceName)
                        isMemberResolving = false
                        resolveNextMember()
                    }
                    return
                }
                memberResolveAttempts.remove(serviceInfo.serviceName)
                _discoveredMembers.update { list ->
                    val device = DiscoveredDevice(serviceInfo.serviceName, address, serviceInfo.port)
                    val existing = list.find { it.serviceName == device.serviceName }
                    if (existing != null) list.map { if (it.serviceName == device.serviceName) device else it }
                    else list + device
                }
                isMemberResolving = false
                resolveNextMember()
            }
        })
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
                    // Update IP if the service was already known (handles host IP changes)
                    val existing = list.find { it.serviceName == device.serviceName }
                    if (existing != null) {
                        if (existing.hostAddress != address) {
                            Log.d(TAG, "IP updated for '${device.serviceName}': ${existing.hostAddress} → $address")
                        }
                        list.map { if (it.serviceName == device.serviceName) device else it }
                    } else {
                        list + device
                    }
                }
                isResolving = false
                resolveNext()
            }
        })
    }
}
