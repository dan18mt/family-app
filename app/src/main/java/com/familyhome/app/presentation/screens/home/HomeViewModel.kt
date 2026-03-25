package com.familyhome.app.presentation.screens.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhome.app.data.notification.NotificationCenter
import com.familyhome.app.data.onboarding.InviteDto
import com.familyhome.app.data.onboarding.JoinRequestDto
import com.familyhome.app.data.onboarding.KnockDto
import com.familyhome.app.data.onboarding.NsdHelper
import com.familyhome.app.data.onboarding.OnboardingClient
import com.familyhome.app.data.onboarding.OnboardingState
import com.familyhome.app.data.sync.SyncServer
import com.familyhome.app.domain.model.StockItem
import com.familyhome.app.domain.model.User
import com.familyhome.app.data.sync.SyncRepositoryImpl
import com.familyhome.app.domain.model.Role
import com.familyhome.app.domain.usecase.expense.CheckBudgetAlertUseCase
import com.familyhome.app.domain.usecase.stock.GetLowStockItemsUseCase
import com.familyhome.app.domain.usecase.user.GetCurrentUserUseCase
import com.familyhome.app.domain.usecase.user.GetFamilyMembersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val pendingKnocks: List<KnockDto> = emptyList(),
    val invitingKnockIds: Set<String> = emptySet(),
    val knockError: String? = null,
    val pendingJoinRequests: List<JoinRequestDto> = emptyList(),
    val unreadNotificationCount: Int = 0,
)

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
                // Father's sync server and NSD advertising start automatically
                if (currentUser.role == Role.FATHER) {
                    syncRepository.startHostServer()
                    nsdHelper.startAdvertising(
                        serviceName = "FamilyHome_Host",
                        port        = 8765,
                        serviceType = NsdHelper.FATHER_SERVICE_TYPE,
                    )
                }
                val alerts = checkBudgetAlertUseCase(currentUser.id)
                _state.update { it.copy(budgetAlerts = alerts) }
            }
        }

        // Reactive streams
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

    fun dismissKnockError() {
        _state.update { it.copy(knockError = null) }
    }

    override fun onCleared() {
        super.onCleared()
        nsdHelper.stopAdvertising()
    }

    private fun getLocalIpv4Address(): String {
        return try {
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
