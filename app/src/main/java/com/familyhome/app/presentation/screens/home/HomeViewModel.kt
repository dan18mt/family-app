package com.familyhome.app.presentation.screens.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhome.app.data.notification.NotificationCenter
import com.familyhome.app.data.onboarding.InviteDto
import com.familyhome.app.data.onboarding.JoinRequestDto
import com.familyhome.app.data.onboarding.KnockDto
import com.familyhome.app.data.onboarding.NetworkMonitor
import com.familyhome.app.data.onboarding.NsdHelper
import com.familyhome.app.data.onboarding.OnboardingClient
import com.familyhome.app.data.onboarding.OnboardingState
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.familyhome.app.data.sync.DeletionTracker
import com.familyhome.app.data.sync.MemberPresenceTracker
import com.familyhome.app.presentation.screens.expenses.ExpensesViewModel
import com.familyhome.app.data.sync.SyncRepositoryImpl
import com.familyhome.app.data.sync.SyncServer
import com.familyhome.app.domain.model.Role
import com.familyhome.app.domain.model.StockItem
import com.familyhome.app.domain.model.SyncResult
import com.familyhome.app.domain.model.User
import com.familyhome.app.domain.usecase.expense.CheckBudgetAlertUseCase
import com.familyhome.app.domain.usecase.stock.GetLowStockItemsUseCase
import com.familyhome.app.domain.usecase.user.DeleteFamilyMemberUseCase
import com.familyhome.app.domain.usecase.user.GetCurrentUserUseCase
import com.familyhome.app.domain.usecase.user.GetFamilyMembersUseCase
import com.familyhome.app.domain.usecase.user.UpdateProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import javax.inject.Inject

data class HomeUiState(
    val currentUser: User? = null,
    val familyMembers: List<User> = emptyList(),
    val lowStockItems: List<StockItem> = emptyList(),
    val budgetAlerts: List<CheckBudgetAlertUseCase.BudgetAlert> = emptyList(),
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val lastSyncAt: Long? = null,
    val syncError: String? = null,
    val pendingKnocks: List<KnockDto> = emptyList(),
    val invitingKnockIds: Set<String> = emptySet(),
    val knockError: String? = null,
    val pendingJoinRequests: List<JoinRequestDto> = emptyList(),
    val unreadNotificationCount: Int = 0,
    /** userId → last-seen epoch-ms; populated on all devices after sync. */
    val memberLastSeen: Map<String, Long> = emptyMap(),
    val kickError: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getFamilyMembersUseCase: GetFamilyMembersUseCase,
    private val getLowStockItemsUseCase: GetLowStockItemsUseCase,
    private val checkBudgetAlertUseCase: CheckBudgetAlertUseCase,
    private val syncRepository: SyncRepositoryImpl,
    private val syncServer: SyncServer,
    private val onboardingClient: OnboardingClient,
    private val onboardingState: OnboardingState,
    private val notificationCenter: NotificationCenter,
    private val networkMonitor: NetworkMonitor,
    private val nsdHelper: NsdHelper,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val deleteFamilyMemberUseCase: DeleteFamilyMemberUseCase,
    private val presenceTracker: MemberPresenceTracker,
    private val deletionTracker: DeletionTracker,
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state = _state.asStateFlow()

    init {
        loadData()
        startNsdForRole()
        startAutoLoops()
    }

    private fun loadData() {
        viewModelScope.launch {
            val currentUser = getCurrentUserUseCase()
            _state.update { it.copy(currentUser = currentUser) }
            if (currentUser != null) {
                val payrollDay = dataStore.data.first()[ExpensesViewModel.payrollStartDayKey] ?: 1
                val alerts = checkBudgetAlertUseCase(currentUser.id, payrollDay)
                _state.update { it.copy(budgetAlerts = alerts) }
            }
        }

        viewModelScope.launch {
            getFamilyMembersUseCase().collect { members ->
                _state.update { it.copy(familyMembers = members, isLoading = false) }
            }
        }

        viewModelScope.launch {
            getLowStockItemsUseCase().collect { items ->
                _state.update { it.copy(lowStockItems = items) }
            }
        }

        viewModelScope.launch {
            onboardingState.pendingKnocks.collect { knocks ->
                _state.update { it.copy(pendingKnocks = knocks) }
            }
        }

        viewModelScope.launch {
            onboardingState.pendingRequests.collect { requests ->
                _state.update { it.copy(pendingJoinRequests = requests) }
            }
        }

        viewModelScope.launch {
            notificationCenter.notifications.collect { notifications ->
                _state.update { it.copy(unreadNotificationCount = notifications.count { n -> !n.isRead && n.isActive }) }
            }
        }

        viewModelScope.launch {
            syncRepository.getLastSyncTimeFlow().collect { ts ->
                _state.update { it.copy(lastSyncAt = ts) }
            }
        }

        viewModelScope.launch {
            presenceTracker.lastSeen.collect { lastSeen ->
                _state.update { it.copy(memberLastSeen = lastSeen) }
            }
        }
    }

    /**
     * Start NSD based on role:
     *  - Father  → advertise via NSD so members can always discover the current host IP.
     *  - Member  → browse for Father's NSD service; whenever discovered, update IP and sync.
     */
    private fun startNsdForRole() {
        viewModelScope.launch {
            val user = getCurrentUserUseCase() ?: return@launch
            if (user.role == Role.FATHER) {
                syncRepository.startHostServer()
                nsdHelper.startAdvertising("FamilyHome_Host", 8765, NsdHelper.FATHER_SERVICE_TYPE)
            } else {
                nsdHelper.startBrowsing(NsdHelper.FATHER_SERVICE_TYPE)

                nsdHelper.discoveredDevices
                    .filter { it.isNotEmpty() }
                    .collect { devices ->
                        val host = devices.first()
                        val storedIp = syncRepository.getHostIpFlow().first()
                        if (host.hostAddress != storedIp) {
                            Log.d("HomeViewModel", "Host IP updated: $storedIp → ${host.hostAddress}")
                            syncRepository.saveHostIp(host.hostAddress)
                        }
                        if (!_state.value.isSyncing) performSync(isManual = false)
                    }
            }
        }
    }

    /** Auto-sync every 15 s (members only, silent) + NSD safety-net re-browse every 60 s. */
    private fun startAutoLoops() {
        viewModelScope.launch {
            while (true) {
                delay(15_000)
                val user = _state.value.currentUser
                if (user != null && user.role != Role.FATHER && !_state.value.isSyncing) {
                    performSync(isManual = false)
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                val user = _state.value.currentUser
                if (user != null && user.role != Role.FATHER && networkMonitor.isWifiConnected.value) {
                    nsdHelper.startBrowsing(NsdHelper.FATHER_SERVICE_TYPE)
                }
            }
        }
    }

    fun manualSync() {
        if (_state.value.isSyncing) return
        viewModelScope.launch { performSync(isManual = true) }
    }

    /**
     * Run a sync cycle.
     *
     * On failure for a manual sync, automatically attempts NSD rediscovery of the leader.
     * - If the leader is found on a new IP → updates stored IP and retries once.
     * - If the leader is not found → shows "Family leader is not on the same network."
     * For auto-sync, failures silently trigger an NSD re-browse for the next cycle.
     */
    private suspend fun performSync(isManual: Boolean = false) {
        if (!networkMonitor.isWifiConnected.value) {
            if (isManual) _state.update { it.copy(syncError = "Not connected to Wi-Fi.") }
            return
        }
        _state.update { it.copy(isSyncing = true, syncError = null) }

        when (val result = syncRepository.syncWithHost()) {
            is SyncResult.Success -> {
                _state.update { it.copy(isSyncing = false, lastSyncAt = result.syncedAt) }
            }
            is SyncResult.Error -> {
                if (isManual) {
                    // Try to find the leader on a (possibly new) IP via NSD
                    val leaderFound = tryRediscoverLeader()
                    if (leaderFound) {
                        // Retry with the updated IP
                        when (val retryResult = syncRepository.syncWithHost()) {
                            is SyncResult.Success -> _state.update {
                                it.copy(isSyncing = false, lastSyncAt = retryResult.syncedAt)
                            }
                            is SyncResult.Error -> _state.update {
                                it.copy(isSyncing = false, syncError = retryResult.message)
                            }
                        }
                    } else {
                        val errorMsg = if (networkMonitor.isWifiConnected.value)
                            "Family leader is not on the same network."
                        else
                            "Not connected to Wi-Fi."
                        _state.update { it.copy(isSyncing = false, syncError = errorMsg) }
                    }
                } else {
                    // Auto-sync failure: silently trigger NSD re-browse for the next attempt
                    nsdHelper.startBrowsing(NsdHelper.FATHER_SERVICE_TYPE)
                    _state.update { it.copy(isSyncing = false) }
                }
            }
        }
    }

    /**
     * Restart NSD browsing and wait up to 8 seconds for the leader to be discovered.
     * If found, saves the new IP and returns true.
     */
    private suspend fun tryRediscoverLeader(): Boolean {
        nsdHelper.startBrowsing(NsdHelper.FATHER_SERVICE_TYPE)
        val found = withTimeoutOrNull(8_000L) {
            nsdHelper.discoveredDevices.first { it.isNotEmpty() }
        }
        return if (found != null) {
            val device = found.first()
            syncRepository.saveHostIp(device.hostAddress)
            Log.d("HomeViewModel", "Leader rediscovered at ${device.hostAddress}")
            true
        } else {
            false
        }
    }

    fun updateProfile(user: User) {
        viewModelScope.launch {
            updateProfileUseCase(user)
                .onSuccess { _state.update { it.copy(currentUser = user) } }
                .onFailure { e -> Log.e("HomeViewModel", "Failed to update profile", e) }
        }
    }

    fun approveJoinRequest(request: JoinRequestDto, role: Role) {
        val fatherId = _state.value.currentUser?.id ?: return
        viewModelScope.launch {
            syncServer.createMemberFromRequest(request, role, fatherId)
                .onFailure { e -> _state.update { it.copy(kickError = e.message) } }
        }
    }

    fun rejectJoinRequest(request: JoinRequestDto) {
        syncServer.rejectRequest(request.deviceId)
    }

    fun kickMember(member: User) {
        val actor = _state.value.currentUser ?: return
        viewModelScope.launch {
            deleteFamilyMemberUseCase(actor, member.id)
                .onSuccess {
                    // Record deletion so it propagates to all member devices on next sync
                    deletionTracker.recordUserDeletion(member.id)
                }
                .onFailure { e -> _state.update { it.copy(kickError = e.message) } }
        }
    }

    fun sendInviteFromKnock(knock: KnockDto) {
        val father = _state.value.currentUser ?: return
        viewModelScope.launch {
            _state.update { it.copy(invitingKnockIds = it.invitingKnockIds + knock.deviceId, knockError = null) }
            val localIp = getLocalIpv4Address()
            if (localIp.isBlank()) {
                _state.update { it.copy(invitingKnockIds = it.invitingKnockIds - knock.deviceId, knockError = "Could not determine local IP address.") }
                return@launch
            }
            val invite = InviteDto(
                fatherName = father.name,
                familyId   = father.id,
                fatherIp   = localIp,
                fatherPort = 8765,
            )
            val success = onboardingClient.sendInvite(knock.memberIp, knock.memberPort, invite)
            _state.update { it.copy(invitingKnockIds = it.invitingKnockIds - knock.deviceId) }
            if (success) {
                onboardingState.removeKnock(knock.deviceId)
            } else {
                _state.update { it.copy(knockError = "Could not reach ${knock.deviceName}. Make sure both devices are on the same WiFi.") }
            }
        }
    }

    fun dismissKnockError() = _state.update { it.copy(knockError = null) }
    fun dismissSyncError()  = _state.update { it.copy(syncError = null) }
    fun dismissKickError()  = _state.update { it.copy(kickError = null) }

    private fun getLocalIpv4Address(): String =
        networkMonitor.getCurrentIpv4() ?: run {
            try {
                Collections.list(NetworkInterface.getNetworkInterfaces())
                    .flatMap { Collections.list(it.inetAddresses) }
                    .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                    ?.hostAddress ?: ""
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Failed to get local IP", e)
                ""
            }
        }
}
