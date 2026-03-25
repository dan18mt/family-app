package com.familyhome.app.data.onboarding

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory singleton shared between [com.familyhome.app.data.sync.SyncServer]
 * (which receives join requests over HTTP) and [FatherOnboardingViewModel]
 * (which renders them and calls approve/reject).
 */
@Singleton
class OnboardingState @Inject constructor() {

    private val _pendingRequests = MutableStateFlow<List<JoinRequestDto>>(emptyList())
    val pendingRequests: StateFlow<List<JoinRequestDto>> = _pendingRequests.asStateFlow()

    private val _approvals = MutableStateFlow<Map<String, ApprovalStatusDto>>(emptyMap())
    val approvals: StateFlow<Map<String, ApprovalStatusDto>> = _approvals.asStateFlow()

    private val _pendingKnocks = MutableStateFlow<List<KnockDto>>(emptyList())
    val pendingKnocks: StateFlow<List<KnockDto>> = _pendingKnocks.asStateFlow()

    fun addJoinRequest(request: JoinRequestDto) {
        _pendingRequests.update { list ->
            if (list.any { it.deviceId == request.deviceId }) list else list + request
        }
    }

    fun setApproval(deviceId: String, status: ApprovalStatusDto) {
        _approvals.update { it + (deviceId to status) }
        _pendingRequests.update { list -> list.filter { it.deviceId != deviceId } }
    }

    fun getApprovalStatus(deviceId: String): ApprovalStatusDto =
        _approvals.value[deviceId] ?: ApprovalStatusDto(status = "pending")

    fun addKnock(knock: KnockDto) {
        _pendingKnocks.update { list ->
            if (list.any { it.deviceId == knock.deviceId }) list else list + knock
        }
    }

    fun removeKnock(deviceId: String) {
        _pendingKnocks.update { it.filter { k -> k.deviceId != deviceId } }
    }

    fun clear() {
        _pendingRequests.value = emptyList()
        _approvals.value = emptyMap()
        _pendingKnocks.value = emptyList()
    }
}
