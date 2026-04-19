package com.familyhome.app.presentation.screens.onboarding

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhome.app.data.onboarding.InviteDto
import com.familyhome.app.data.onboarding.JoinRequestDto
import com.familyhome.app.data.onboarding.KnockDto
import com.familyhome.app.data.onboarding.NsdHelper
import com.familyhome.app.data.onboarding.OnboardingClient
import com.familyhome.app.data.onboarding.OnboardingServer
import com.familyhome.app.data.sync.SyncRepositoryImpl
import com.familyhome.app.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import java.util.UUID
import javax.inject.Inject

sealed class MemberOnboardingStep {
    object Scanning         : MemberOnboardingStep()
    object InviteReceived   : MemberOnboardingStep()
    object FillProfile      : MemberOnboardingStep()
    object WaitingApproval  : MemberOnboardingStep()
    object Done             : MemberOnboardingStep()
}

data class MemberOnboardingUiState(
    val step: MemberOnboardingStep = MemberOnboardingStep.Scanning,
    val invite: InviteDto?         = null,
    // Profile form
    val name: String               = "",
    val error: String?             = null,
    val isLoading: Boolean         = false,
    val knockSent: Boolean         = false,
)

@HiltViewModel
class MemberOnboardingViewModel @Inject constructor(
    private val nsdHelper: NsdHelper,
    private val onboardingServer: OnboardingServer,
    private val onboardingClient: OnboardingClient,
    private val syncRepository: SyncRepositoryImpl,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MemberOnboardingUiState())
    val state = _state.asStateFlow()

    /** Stable device ID for this onboarding session */
    private val deviceId = UUID.randomUUID().toString()

    init {
        startListening()
    }

    private fun startListening() {
        val deviceName = Build.MODEL
        val localIp = getLocalIpv4Address()

        // Start the small Ktor server that receives Father's invite
        onboardingServer.start(deviceName, deviceId)

        // Advertise this device so Father can discover it via NSD
        nsdHelper.startAdvertising(
            serviceName = "FamilyMember_${deviceId.take(8)}",
            port        = OnboardingServer.PORT,
            serviceType = NsdHelper.MEMBER_SERVICE_TYPE,
        )

        // Also browse for Father so we can send a knock notification
        nsdHelper.startBrowsing(NsdHelper.FATHER_SERVICE_TYPE)

        // When Father is discovered, send a knock so they know a device wants to join
        nsdHelper.discoveredDevices
            .onEach { devices ->
                val father = devices.firstOrNull()
                if (father != null && !_state.value.knockSent && localIp.isNotBlank()) {
                    val knock = KnockDto(
                        deviceId   = deviceId,
                        deviceName = deviceName,
                        memberIp   = localIp,
                        memberPort = OnboardingServer.PORT,
                    )
                    val success = onboardingClient.sendKnock(father.hostAddress, father.port, knock)
                    if (success) {
                        _state.update { it.copy(knockSent = true) }
                    }
                }
            }
            .launchIn(viewModelScope)

        // Observe incoming invite
        onboardingServer.receivedInvite
            .filterNotNull()
            .onEach { invite ->
                _state.update { it.copy(step = MemberOnboardingStep.InviteReceived, invite = invite) }
            }
            .launchIn(viewModelScope)
    }

    private fun getLocalIpv4Address(): String {
        return try {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .flatMap { Collections.list(it.inetAddresses) }
                .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                ?.hostAddress ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    fun acceptInvite() {
        _state.update { it.copy(step = MemberOnboardingStep.FillProfile) }
    }

    fun declineInvite() {
        onboardingServer.clearInvite()
        _state.update { it.copy(step = MemberOnboardingStep.Scanning, invite = null) }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value, error = null) }

    fun submitProfile() {
        val s = _state.value
        val invite = s.invite ?: return

        if (s.name.isBlank()) {
            _state.update { it.copy(error = "Name cannot be empty.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val request = JoinRequestDto(
                deviceId   = deviceId,
                deviceName = Build.MODEL,
                name       = s.name,
            )

            val submitted = onboardingClient.submitJoinRequest(
                ip      = invite.fatherIp,
                port    = invite.fatherPort,
                request = request,
            )

            if (!submitted) {
                _state.update { it.copy(isLoading = false, error = "Could not reach Father's device. Make sure you're on the same WiFi.") }
                return@launch
            }

            _state.update { it.copy(isLoading = false, step = MemberOnboardingStep.WaitingApproval) }
            pollForApproval(invite)
        }
    }

    private fun pollForApproval(invite: InviteDto) {
        viewModelScope.launch {
            while (true) {
                delay(3_000)
                val status = onboardingClient.pollApprovalStatus(
                    ip       = invite.fatherIp,
                    port     = invite.fatherPort,
                    deviceId = deviceId,
                ) ?: continue

                when (status.status) {
                    "approved" -> {
                        val userId = status.userId ?: continue
                        // Save Father's IP for future syncs and pull full family data
                        syncRepository.saveHostIp(invite.fatherIp)
                        syncRepository.syncWithHost()
                        // Log the member in
                        sessionRepository.setCurrentUserId(userId)
                        _state.update { it.copy(step = MemberOnboardingStep.Done) }
                        return@launch
                    }
                    "rejected" -> {
                        _state.update {
                            it.copy(
                                step  = MemberOnboardingStep.Scanning,
                                error = "Your request was declined. You can try again.",
                            )
                        }
                        onboardingServer.clearInvite()
                        return@launch
                    }
                    // "pending" — keep polling
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        nsdHelper.stopAll()
        onboardingServer.stop()
    }
}
