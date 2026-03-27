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
import com.familyhome.app.data.sync.SyncRepositoryImpl
import com.familyhome.app.data.sync.SyncServer
import com.familyhome.app.domain.model.Role
import com.familyhome.app.domain.model.StockItem
import com.familyhome.app.domain.model.SyncResult
import com.familyhome.app.domain.model.User
import com.familyhome.app.domain.usecase.expense.CheckBudgetAlertUseCase
import com.familyhome.app.domain.usecase.stock.GetLowStockItemsUseCase
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
)

private const val AUTO_SYNC_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getFamilyMembersUseCase: GetFamilyMembersUseCase,
    private val getLowStockItemsUseCase: GetLowStockItemsUseCase,
    private val checkBudgetAlertUseCase: CheckBudgetAlertUseCase,
    private val syncRepository: SyncRepositoryImpl,
    private val syncServer: SyncServer,
    private val nsdHelper: NsdHelper,
    private val onboardingClient: OnboardingClient,
    private val onboardingState: OnboardingState,
    private val notificationCenter: NotificationCenter,
    private val networkMonitor: NetworkMonitor,
    private val updateProfileUseCase: UpdateProfileUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state = _state.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val currentUser = getCurrentUserUseCase()
            _state.update { it.copy(currentUser = currentUser) }

            if (currentUser != null) {
                if (currentUser.role == Role.FATHER) {
                    syncRepository.startHostServer()
                    nsdHelper.startAdvertising(
                        serviceName = "FamilyHome_Host",
                        port        = 8765,
                        serviceType = NsdHelper.FATHER_SERVICE_TYPE,
                    )
                } else {
                    // Start periodic auto-sync for non-Father members
                    startAutoSync()
                }
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
                _state.update { it.copy(unreadNotificationCount = notifications.count { n -> !n.isRead }) }
            }
        }

        viewModelScope.launch {
            syncRepository.getLastSyncTimeFlow().collect { ts ->
                _state.update { it.copy(lastSyncAt = ts) }
            }
        }
    }

    private fun startAutoSync() {
        viewModelScope.launch {
            while (true) {
                delay(AUTO_SYNC_INTERVAL_MS)
                performSync()
            }
        }
    }

    fun manualSync() {
        if (_state.value.isSyncing) return
        viewModelScope.launch { performSync() }
    }

    private suspend fun performSync() {
        _state.update { it.copy(isSyncing = true, syncError = null) }
        when (val result = syncRepository.syncWithHost()) {
            is SyncResult.Success -> _state.update { it.copy(isSyncing = false, lastSyncAt = result.syncedAt) }
            is SyncResult.Error   -> _state.update { it.copy(isSyncing = false, syncError = result.message) }
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
        }
    }

    fun rejectJoinRequest(request: JoinRequestDto) {
        syncServer.rejectRequest(request.deviceId)
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

    override fun onCleared() {
        super.onCleared()
        nsdHelper.stopAdvertising()
    }

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
