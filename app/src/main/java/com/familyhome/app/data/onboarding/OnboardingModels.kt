package com.familyhome.app.data.onboarding

import kotlinx.serialization.Serializable

/** Returned by member's OnboardingServer  GET /info */
@Serializable
data class DeviceInfoDto(
    val deviceName: String,
    val deviceId: String,
)

/** Returned by Father's SyncServer  GET /onboarding/info */
@Serializable
data class FamilyInfoDto(
    val fatherName: String,
    val familyId: String,
)

/** Father → member device  POST /invite */
@Serializable
data class InviteDto(
    val fatherName: String,
    val familyId: String,
    val fatherIp: String,
    val fatherPort: Int,
)

/** Member → Father's SyncServer  POST /onboarding/join-request */
@Serializable
data class JoinRequestDto(
    val deviceId: String,
    val deviceName: String,
    val name: String,
    val avatarUri: String? = null,
)

/** Member polls Father's SyncServer  GET /onboarding/status/{deviceId} */
@Serializable
data class ApprovalStatusDto(
    /** "pending" | "approved" | "rejected" */
    val status: String,
    val userId: String? = null,
)

/** Member → Father's SyncServer  POST /onboarding/knock — notifies Father that a device wants to join */
@Serializable
data class KnockDto(
    val deviceId: String,
    val deviceName: String,
    val memberIp: String,
    val memberPort: Int,
)
