package com.familyhome.app.presentation.screens.onboarding

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhome.app.data.onboarding.DeviceInfoDto
import com.familyhome.app.data.onboarding.DiscoveredDevice
import com.familyhome.app.data.onboarding.InviteDto
import com.familyhome.app.data.onboarding.JoinRequestDto
import com.familyhome.app.data.onboarding.NsdHelper
import com.familyhome.app.data.onboarding.OnboardingClient
import com.familyhome.app.data.onboarding.OnboardingState
import com.familyhome.app.data.sync.SyncRepositoryImpl
import com.familyhome.app.data.sync.SyncServer
import com.familyhome.app.domain.model.Role
import com.familyhome.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class FatherOnboardingStep {
    object Discovering : FatherOnboardingStep()
    object Approving   : FatherOnboardingStep()
    object Done        : FatherOnboardingStep()
}

data class DiscoveredDeviceUi(
    val device: DiscoveredDevice,
    val deviceInfo: DeviceInfoDto? = null,
    val inviteSent: Boolean = false,
)

data class FatherOnboardingUiState(
    val step: FatherOnboardingStep         = FatherOnboardingStep.Discovering,
    val fatherName: String                 = "",
    val fatherId: String                   = "",
    val localIp: String                    = "",
    val discoveredDevices: List<DiscoveredDeviceUi> = emptyList(),
    val pendingRequests: List<JoinRequestDto>       = emptyList(),
    val isLoadingInvite: Set<String>                = emptySet(),
    val error: String?                     = null,
)

@HiltViewModel
class FatherOnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val nsdHelper: NsdHelper,
    private val onboardingClient: OnboardingClient,
    private val onboardingState: OnboardingState,
    private val userRepository: UserRepository,
    private val syncServer: SyncServer,
    private val syncRepository: SyncRepositoryImpl,
) : ViewModel() {

    private val _state = MutableStateFlow(FatherOnboardingUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch { initFatherOnboarding() }
    }

    private suspend fun initFatherOnboarding() {
        val father = userRepository.getAllUsers().first().firstOrNull { it.role == Role.FATHER }
        val localIp = getLocalIpAddress()

        _state.update {
            it.copy(
                fatherName = father?.name ?: "",
                fatherId   = father?.id ?: "",
                localIp    = localIp,
            )
        }

        // Start Father's sync server (member devices POST join requests to it)
        syncRepository.startHostServer()

        // Advertise via NSD so member devices can discover Father
        nsdHelper.startAdvertising(
            serviceName = "FamilyHome_Host",
            port        = 8765,
            serviceType = NsdHelper.FATHER_SERVICE_TYPE,
        )

        // Browse for member devices that are in join mode
        nsdHelper.startBrowsing(NsdHelper.MEMBER_SERVICE_TYPE)

        // Mirror NSD discoveries into UI state
        nsdHelper.discoveredDevices
            .onEach { devices ->
                _state.update { s ->
                    val existing = s.discoveredDevices.associateBy { it.device.serviceName }
                    val updated = devices.map { d ->
                        existing[d.serviceName] ?: DiscoveredDeviceUi(device = d)
                    }
                    s.copy(discoveredDevices = updated)
                }
                // Fetch device info for any newly discovered device that doesn't have it yet
                devices.forEach { device ->
                    val alreadyFetched = _state.value.discoveredDevices
                        .any { it.device.serviceName == device.serviceName && it.deviceInfo != null }
                    if (!alreadyFetched) fetchDeviceInfo(device)
                }
            }
            .launchIn(viewModelScope)

        // Mirror pending join requests from OnboardingState
        onboardingState.pendingRequests
            .onEach { requests -> _state.update { it.copy(pendingRequests = requests) } }
            .launchIn(viewModelScope)
    }

    private fun fetchDeviceInfo(device: DiscoveredDevice) {
        viewModelScope.launch {
            val info = onboardingClient.getDeviceInfo(device.hostAddress, device.port)
            _state.update { s ->
                s.copy(
                    discoveredDevices = s.discoveredDevices.map { ui ->
                        if (ui.device.serviceName == device.serviceName) ui.copy(deviceInfo = info)
                        else ui
                    }
                )
            }
        }
    }

    fun sendInvite(deviceUi: DiscoveredDeviceUi) {
        val s = _state.value
        if (s.fatherName.isBlank() || s.localIp.isBlank()) return

        viewModelScope.launch {
            _state.update { it.copy(isLoadingInvite = it.isLoadingInvite + deviceUi.device.serviceName) }

            val invite = InviteDto(
                fatherName = s.fatherName,
                familyId   = s.fatherId,
                fatherIp   = s.localIp,
                fatherPort = 8765,
            )
            val success = onboardingClient.sendInvite(
                ip    = deviceUi.device.hostAddress,
                port  = deviceUi.device.port,
                invite = invite,
            )
            _state.update { state ->
                state.copy(
                    isLoadingInvite = state.isLoadingInvite - deviceUi.device.serviceName,
                    discoveredDevices = state.discoveredDevices.map { ui ->
                        if (ui.device.serviceName == deviceUi.device.serviceName)
                            ui.copy(inviteSent = success)
                        else ui
                    },
                    error = if (!success) "Could not reach ${deviceUi.deviceInfo?.deviceName ?: deviceUi.device.serviceName}" else null,
                )
            }
        }
    }

    fun moveToApproving() {
        _state.update { it.copy(step = FatherOnboardingStep.Approving) }
    }

    fun approveRequest(request: JoinRequestDto, role: Role) {
        val fatherId = _state.value.fatherId
        viewModelScope.launch {
            syncServer.createMemberFromRequest(request, role, fatherId)
        }
    }

    fun rejectRequest(request: JoinRequestDto) {
        syncServer.rejectRequest(request.deviceId)
    }

    fun finish() {
        _state.update { it.copy(step = FatherOnboardingStep.Done) }
    }

    override fun onCleared() {
        super.onCleared()
        nsdHelper.stopAll()
    }

    private fun getLocalIpAddress(): String {
        @Suppress("DEPRECATION")
        val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val ip = wm.connectionInfo.ipAddress
        return String.format(
            "%d.%d.%d.%d",
            ip and 0xff,
            ip shr 8  and 0xff,
            ip shr 16 and 0xff,
            ip shr 24 and 0xff,
        )
    }
}
