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
import com.familyhome.app.data.sync.MemberPresenceTracker
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
    /** userId → last-seen epoch-ms (only populated on Father's device via MemberPresenceTracker) */
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
                val alerts = checkBudgetAlertUseCase(currentUser.id)
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
     *  - Father  → ensure the Ktor server is running and re-advertise via NSD so members
     *              can always discover the current host IP (survives app restarts + IP changes).
     *  - Member  → browse for Father's NSD service; whenever a host is (re)discovered,
     *              persist its IP and trigger a sync immediately.
     */
    private fun startNsdForRole() {
        viewModelScope.launch {
            val user = getCurrentUserUseCase() ?: return@launch
            if (user.role == Role.FATHER) {
                // Idempotent: server has `if (isRunning) return` guard
                syncRepository.startHostServer()
                nsdHelper.startAdvertising("FamilyHome_Host", 8765, NsdHelper.FATHER_SERVICE_TYPE)
            } else {
                // Start continuous discovery; NsdHelper will re-browse on network reconnect
                // automatically via its NetworkMonitor callback.
                nsdHelper.startBrowsing(NsdHelper.FATHER_SERVICE_TYPE)

                // Whenever the Father service is (re)discovered, update the stored host IP
                // and sync immediately — this is the primary reconnect mechanism.
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
        // Periodic sync — 15 s, background, errors suppressed
        viewModelScope.launch {
            while (true) {
                delay(15_000)
                val user = _state.value.currentUser
                if (user != null && user.role != Role.FATHER && !_state.value.isSyncing) {
                    performSync(isManual = false)
                }
            }
        }
        // NSD safety-net: restart browsing every 60 s in case the OS silently killed it
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
     * [isManual] = true  → always surface errors to the user (button tap).
     * [isManual] = false → silent: no snackbar on failure, no error when offline.
     *                      The next auto or NSD-triggered attempt will retry.
     */
    private suspend fun performSync(isManual: Boolean = false) {
        if (!networkMonitor.isWifiConnected.value) {
            if (isManual) _state.update { it.copy(syncError = "Not connected to Wi-Fi.") }
            return
        }
        _state.update { it.copy(isSyncing = true, syncError = null) }
        when (val result = syncRepository.syncWithHost()) {
            is SyncResult.Success -> _state.update { it.copy(isSyncing = false, lastSyncAt = result.syncedAt) }
            is SyncResult.Error   -> _state.update {
                it.copy(isSyncing = false, syncError = if (isManual) result.message else null)
            }
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
