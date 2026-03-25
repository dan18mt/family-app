package com.familyhome.app.data.onboarding

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import javax.inject.Inject

/**
 * HTTP client for onboarding communication:
 *  - Father → member's [OnboardingServer]  (getDeviceInfo, sendInvite)
 *  - Member → Father's SyncServer          (getFamilyInfo, submitJoinRequest, pollApprovalStatus)
 */
class OnboardingClient @Inject constructor(
    private val httpClient: HttpClient,
) {
    // ── Father → member ───────────────────────────────────────────────────────

    suspend fun getDeviceInfo(ip: String, port: Int): DeviceInfoDto? = runCatching {
        httpClient.get("http://$ip:$port/info").body<DeviceInfoDto>()
    }.getOrNull()

    suspend fun sendInvite(ip: String, port: Int, invite: InviteDto): Boolean = runCatching {
        httpClient.post("http://$ip:$port/invite") {
            contentType(ContentType.Application.Json)
            setBody(invite)
        }.status.isSuccess()
    }.getOrDefault(false)

    // ── Member → Father ───────────────────────────────────────────────────────

    suspend fun getFamilyInfo(ip: String, port: Int): FamilyInfoDto? = runCatching {
        httpClient.get("http://$ip:$port/onboarding/info").body<FamilyInfoDto>()
    }.getOrNull()

    suspend fun submitJoinRequest(ip: String, port: Int, request: JoinRequestDto): Boolean = runCatching {
        httpClient.post("http://$ip:$port/onboarding/join-request") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.status.isSuccess()
    }.getOrDefault(false)

    suspend fun pollApprovalStatus(ip: String, port: Int, deviceId: String): ApprovalStatusDto? = runCatching {
        httpClient.get("http://$ip:$port/onboarding/status/$deviceId").body<ApprovalStatusDto>()
    }.getOrNull()

    suspend fun sendKnock(ip: String, port: Int, knock: KnockDto): Boolean = runCatching {
        httpClient.post("http://$ip:$port/onboarding/knock") {
            contentType(ContentType.Application.Json)
            setBody(knock)
        }.status.isSuccess()
    }.getOrDefault(false)
}
