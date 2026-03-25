package com.familyhome.app.data.sync

import com.familyhome.app.data.mapper.*
import com.familyhome.app.data.notification.NotificationCenter
import com.familyhome.app.data.onboarding.ApprovalStatusDto
import com.familyhome.app.data.onboarding.FamilyInfoDto
import com.familyhome.app.data.onboarding.JoinRequestDto
import com.familyhome.app.data.onboarding.KnockDto
import com.familyhome.app.data.onboarding.OnboardingState
import com.familyhome.app.domain.model.AppNotification
import com.familyhome.app.domain.model.NotificationType
import com.familyhome.app.domain.model.Role
import com.familyhome.app.domain.model.SyncPayload
import com.familyhome.app.domain.model.User
import com.familyhome.app.domain.repository.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Embedded Ktor HTTP server that runs on the host device (Father's phone).
 * Handles both data sync and onboarding join requests from member devices.
 *
 * To swap in Firebase later: delete this class and [SyncClient], replace
 * [SyncRepositoryImpl] with a Firestore-backed implementation — domain layer unchanged.
 */
@Singleton
class SyncServer @Inject constructor(
    private val userRepository: UserRepository,
    private val stockRepository: StockRepository,
    private val choreRepository: ChoreRepository,
    private val expenseRepository: ExpenseRepository,
    private val budgetRepository: BudgetRepository,
    private val onboardingState: OnboardingState,
    private val notificationCenter: NotificationCenter,
) {
    private var server: ApplicationEngine? = null

    val isRunning: Boolean get() = server != null

    fun start(port: Int) {
        if (isRunning) return
        server = embeddedServer(CIO, port = port) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            routing {
                // ── Sync ─────────────────────────────────────────────────────
                get("/ping")        { call.respond("pong") }
                get("/sync/pull")   { handlePull(call) }
                post("/sync/push")  { handlePush(call) }

                // ── Onboarding ────────────────────────────────────────────────
                get("/onboarding/info")                  { handleOnboardingInfo(call) }
                post("/onboarding/join-request")         { handleJoinRequest(call) }
                get("/onboarding/pending")               { handleGetPending(call) }
                get("/onboarding/status/{deviceId}")     { handleStatus(call) }
                post("/onboarding/knock")                { handleKnock(call) }
            }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 500, timeoutMillis = 1_000)
        server = null
    }

    // ── Sync handlers ────────────────────────────────────────────────────────

    private suspend fun handlePull(call: ApplicationCall) {
        val payload = SyncPayload(
            users          = userRepository.getAllUsers().first().map { it.toDto() },
            stockItems     = stockRepository.getAllItems().first().map { it.toDto() },
            choreLogs      = choreRepository.getChoreHistory(0L).first().map { it.toDto() },
            recurringTasks = choreRepository.getRecurringTasks().first().map { it.toDto() },
            expenses       = expenseRepository.getAllExpenses().first().map { it.toDto() },
            budgets        = budgetRepository.getAllBudgets().first().map { it.toDto() },
        )
        call.respond(payload)
    }

    private suspend fun handlePush(call: ApplicationCall) {
        val payload = call.receive<SyncPayload>()
        mergePayload(payload)
        postSyncNotifications(payload)
        call.respond(mapOf("status" to "ok"))
    }

    private fun postSyncNotifications(payload: SyncPayload) {
        payload.choreLogs?.forEach { log ->
            notificationCenter.post(AppNotification(
                type    = NotificationType.CHORE_COMPLETED,
                title   = "Chore completed",
                message = log.taskName,
            ))
        }
        payload.recurringTasks?.forEach { task ->
            notificationCenter.post(AppNotification(
                type    = NotificationType.CHORE_ASSIGNED,
                title   = "Chore assigned",
                message = task.taskName,
            ))
        }
        payload.expenses?.forEach { expense ->
            notificationCenter.post(AppNotification(
                type    = NotificationType.EXPENSE_ADDED,
                title   = "New expense",
                message = "${expense.description} — ${expense.category.lowercase().replaceFirstChar { it.uppercase() }}",
            ))
        }
    }

    /** Last-write-wins merge: upsert everything received from the client. */
    private suspend fun mergePayload(payload: SyncPayload) {
        payload.users?.let          { userRepository.upsertAll(it.map { dto -> dto.toDomain() }) }
        payload.stockItems?.let     { stockRepository.upsertAll(it.map { dto -> dto.toDomain() }) }
        payload.choreLogs?.let      { choreRepository.upsertAllLogs(it.map { dto -> dto.toDomain() }) }
        payload.recurringTasks?.let { choreRepository.upsertAllRecurring(it.map { dto -> dto.toDomain() }) }
        payload.expenses?.let       { expenseRepository.upsertAll(it.map { dto -> dto.toDomain() }) }
        payload.budgets?.let        { budgetRepository.upsertAll(it.map { dto -> dto.toDomain() }) }
    }

    // ── Onboarding handlers ──────────────────────────────────────────────────

    private suspend fun handleOnboardingInfo(call: ApplicationCall) {
        val father = userRepository.getAllUsers().first().firstOrNull { it.role == Role.FATHER }
        if (father == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "No father account found"))
            return
        }
        call.respond(FamilyInfoDto(fatherName = father.name, familyId = father.id))
    }

    private suspend fun handleJoinRequest(call: ApplicationCall) {
        val request = call.receive<JoinRequestDto>()
        onboardingState.addJoinRequest(request)
        notificationCenter.post(AppNotification(
            type    = NotificationType.JOIN_REQUEST,
            title   = "New join request",
            message = "${request.name} (${request.deviceName}) wants to join your family",
        ))
        call.respond(mapOf("status" to "pending"))
    }

    private suspend fun handleGetPending(call: ApplicationCall) {
        call.respond(onboardingState.pendingRequests.value)
    }

    private suspend fun handleKnock(call: ApplicationCall) {
        val knock = call.receive<KnockDto>()
        onboardingState.addKnock(knock)
        call.respond(mapOf("status" to "received"))
    }

    private suspend fun handleStatus(call: ApplicationCall) {
        val deviceId = call.parameters["deviceId"] ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing deviceId"))
            return
        }
        call.respond(onboardingState.getApprovalStatus(deviceId))
    }

    // ── Member creation (called directly by FatherOnboardingViewModel) ───────

    /**
     * Creates a new family member from an approved [JoinRequestDto].
     * The [pinHash] in the request is already SHA-256 hashed by the member device,
     * so it is stored as-is without a second round of hashing.
     */
    suspend fun createMemberFromRequest(
        request: JoinRequestDto,
        role: Role,
        fatherId: String,
    ): User {
        val newUser = User(
            id        = UUID.randomUUID().toString(),
            name      = request.name,
            role      = role,
            parentId  = fatherId,
            avatarUri = request.avatarUri,
            pin       = request.pinHash,
            createdAt = System.currentTimeMillis(),
        )
        userRepository.insertUser(newUser)
        onboardingState.setApproval(
            deviceId = request.deviceId,
            status   = ApprovalStatusDto(status = "approved", userId = newUser.id),
        )
        notificationCenter.post(AppNotification(
            type    = NotificationType.MEMBER_JOINED,
            title   = "Member joined",
            message = "${newUser.name} is now part of your family as ${newUser.role.displayName}",
        ))
        return newUser
    }

    fun rejectRequest(deviceId: String) {
        onboardingState.setApproval(
            deviceId = deviceId,
            status   = ApprovalStatusDto(status = "rejected"),
        )
    }
}
