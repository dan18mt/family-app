package com.familyhome.app.presentation.screens.onboarding

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhome.app.data.onboarding.InviteDto
import com.familyhome.app.data.onboarding.JoinRequestDto
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
import java.security.MessageDigest
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
    val pin: String                = "",
    val confirmPin: String         = "",
    val error: String?             = null,
    val isLoading: Boolean         = false,
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

        // Start the small Ktor server that receives Father's invite
        onboardingServer.start(deviceName, deviceId)

        // Advertise this device so Father can discover it
        nsdHelper.startAdvertising(
            serviceName = "FamilyMember_${deviceId.take(8)}",
            port        = OnboardingServer.PORT,
            serviceType = NsdHelper.MEMBER_SERVICE_TYPE,
        )

        // Observe incoming invite
        onboardingServer.receivedInvite
            .filterNotNull()
            .onEach { invite ->
                _state.update { it.copy(step = MemberOnboardingStep.InviteReceived, invite = invite) }
            }
            .launchIn(viewModelScope)
    }

    fun acceptInvite() {
        _state.update { it.copy(step = MemberOnboardingStep.FillProfile) }
    }

    fun declineInvite() {
        onboardingServer.clearInvite()
        _state.update { it.copy(step = MemberOnboardingStep.Scanning, invite = null) }
    }

    fun onNameChange(value: String)       = _state.update { it.copy(name = value, error = null) }
    fun onPinChange(value: String)        = _state.update { it.copy(pin = value, error = null) }
    fun onConfirmPinChange(value: String) = _state.update { it.copy(confirmPin = value, error = null) }

    fun submitProfile() {
        val s = _state.value
        val invite = s.invite ?: return

        if (s.name.isBlank()) {
            _state.update { it.copy(error = "Name cannot be empty.") }
            return
        }
        if (s.pin.length != 4) {
            _state.update { it.copy(error = "PIN must be 4 digits.") }
            return
        }
        if (s.pin != s.confirmPin) {
            _state.update { it.copy(error = "PINs do not match.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val request = JoinRequestDto(
                deviceId   = deviceId,
                deviceName = Build.MODEL,
                name       = s.name,
                pinHash    = hashPin(s.pin),
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

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
