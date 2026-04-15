package com.familyhome.app.data.sync

import com.familyhome.app.data.mapper.*
import com.familyhome.app.data.notification.LowStockNotifier
import com.familyhome.app.data.notification.NotificationCenter
import com.familyhome.app.data.onboarding.ApprovalStatusDto
import com.familyhome.app.data.onboarding.FamilyInfoDto
import com.familyhome.app.data.onboarding.JoinRequestDto
import com.familyhome.app.data.onboarding.KnockDto
import com.familyhome.app.data.onboarding.OnboardingState
import com.familyhome.app.domain.model.AppNotification
import com.familyhome.app.domain.model.CustomExpenseCategoryDto
import com.familyhome.app.domain.model.CustomStockCategory
import com.familyhome.app.domain.model.CustomStockCategoryDto
import com.familyhome.app.domain.model.PrayerGoalSettingDto
import com.familyhome.app.domain.model.PrayerLogDto
import com.familyhome.app.domain.model.PrayerReminderDto
import com.familyhome.app.domain.model.NotificationType
import com.familyhome.app.domain.model.Role
import com.familyhome.app.domain.model.SyncPayload
import com.familyhome.app.domain.model.User
import com.familyhome.app.domain.repository.*
import com.familyhome.app.domain.repository.PrayerRepository
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
 */
@Singleton
class SyncServer @Inject constructor(
    private val userRepository: UserRepository,
    private val stockRepository: StockRepository,
    private val choreRepository: ChoreRepository,
    private val expenseRepository: ExpenseRepository,
    private val budgetRepository: BudgetRepository,
    private val customStockCategoryRepository: CustomStockCategoryRepository,
    private val customExpenseCategoryRepository: CustomExpenseCategoryRepository,
    private val prayerRepository: PrayerRepository,
    private val onboardingState: OnboardingState,
    private val notificationCenter: NotificationCenter,
    private val lowStockNotifier: LowStockNotifier,
    private val presenceTracker: MemberPresenceTracker,
    private val deletionTracker: DeletionTracker,
    private val prayerReminderStore: PrayerReminderStore,
    private val sessionRepository: SessionRepository,
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
                get("/ping")        { call.respond("pong") }
                get("/sync/pull")   { handlePull(call) }
                post("/sync/push")  { handlePush(call) }
                post("/notify")     { handleDirectNotify(call) }

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
        // Ensure persisted deletions are loaded from DataStore before building the response.
        // Without this, a fast sync right after app restart could send empty deletedXIds,
        // causing members to re-insert items that were already deleted.
        deletionTracker.awaitReady()

        val father = userRepository.getAllUsers().first().firstOrNull { it.role == Role.FATHER }

        // Build presence map: include all tracked member presence + leader's own timestamp
        val presenceMap = presenceTracker.lastSeen.value.toMutableMap()
        father?.let { presenceMap[it.id] = System.currentTimeMillis() }

        val payload = SyncPayload(
            users                   = userRepository.getAllUsers().first().map { it.toDto() },
            stockItems              = stockRepository.getAllItems().first().map { it.toDto() },
            choreLogs               = choreRepository.getChoreHistory(0L).first().map { it.toDto() },
            recurringTasks          = choreRepository.getRecurringTasks().first()
                .filter { it.id !in deletionTracker.getDeletedRecurringTaskIds() }
                .map { it.toDto() },
            choreAssignments        = choreRepository.getAllAssignments().first().map { it.toAssignmentDto() },
            expenses                = expenseRepository.getAllExpenses().first().map { it.toDto() },
            budgets                 = budgetRepository.getAllBudgets().first()
                .filter { it.id !in deletionTracker.getDeletedBudgetIds() }
                .map { it.toDto() },
            customStockCategories   = customStockCategoryRepository.getAllCategories().first()
                .map { CustomStockCategoryDto(it.id, it.name, it.iconName) },
            customExpenseCategories = customExpenseCategoryRepository.getAllCategories().first()
                .map { CustomExpenseCategoryDto(it.id, it.name, it.iconName) },
            prayerGoalSettings      = prayerRepository.getAllGoalSettings().first()
                .map { PrayerGoalSettingDto(
                    id = it.id, sunnahKey = it.sunnahKey, isEnabled = it.isEnabled,
                    assignedTo = it.assignedUserIds?.joinToString(","),
                    createdBy = it.createdBy, createdAt = it.createdAt,
                    reminderEnabled = it.reminderEnabled,
                ) },
            prayerLogs              = prayerRepository.getLogsSince(0L).first()
                .map { PrayerLogDto(it.id, it.userId, it.sunnahKey, it.epochDay, it.completedCount, it.loggedAt) },
            deletedUserIds             = deletionTracker.getDeletedUserIds().toList().ifEmpty { null },
            deletedPrayerGoalIds       = deletionTracker.getDeletedPrayerGoalIds().toList().ifEmpty { null },
            deletedBudgetIds           = deletionTracker.getDeletedBudgetIds().toList().ifEmpty { null },
            deletedRecurringTaskIds    = deletionTracker.getDeletedRecurringTaskIds().toList().ifEmpty { null },
            prayerReminders         = prayerReminderStore.getActiveReminders().ifEmpty { null },
            presenceMap             = presenceMap.ifEmpty { null },
            leaderId                = father?.id,
        )
        call.respond(payload)
    }

    private suspend fun handlePush(call: ApplicationCall) {
        val payload = call.receive<SyncPayload>()
        payload.pusherId?.let { presenceTracker.update(it) }
        mergePayload(payload)
        postSyncNotifications(payload)
        call.respond(mapOf("status" to "ok"))
    }

    /**
     * Direct push from a member: receives a single [PrayerReminderDto] and shows
     * an OS notification immediately if the leader is the target.
     */
    private suspend fun handleDirectNotify(call: ApplicationCall) {
        val reminder = call.receive<PrayerReminderDto>()
        prayerReminderStore.mergeReminders(listOf(reminder))
        val leaderId = sessionRepository.getCurrentUserId()
        if (leaderId != null) {
            prayerReminderStore.processForCurrentUser(leaderId)
        }
        call.respond(mapOf("status" to "ok"))
    }

    private fun postSyncNotifications(payload: SyncPayload) {
        payload.choreLogs?.forEach { log ->
            notificationCenter.post(AppNotification(
                type     = NotificationType.CHORE_COMPLETED,
                title    = "Chore completed",
                message  = log.taskName,
                sourceId = "chore_log_${log.id}",
            ))
        }
        payload.expenses?.forEach { expense ->
            notificationCenter.post(AppNotification(
                type     = NotificationType.EXPENSE_ADDED,
                title    = "New expense",
                message  = "${expense.description} — ${expense.category.lowercase().replaceFirstChar { it.uppercase() }}",
                sourceId = "expense_${expense.id}",
            ))
        }
        payload.stockItems?.let { dtos ->
            dtos.map { it.toDomain() }.forEach { item -> lowStockNotifier.notifyIfLow(item) }
        }
    }

    /**
     * Last-write-wins merge. Deleted users are never re-inserted — the leader's
     * deletions are the source of truth for user membership.
     */
    private suspend fun mergePayload(payload: SyncPayload) {
        // Ensure persisted deletions are loaded before checking them, preventing
        // a cold-start race where deleted IDs would appear empty.
        deletionTracker.awaitReady()
        payload.users?.let { dtos ->
            val deleted = deletionTracker.getDeletedUserIds()
            val toUpsert = dtos.filter { it.id !in deleted }
            if (toUpsert.isNotEmpty()) userRepository.upsertAll(toUpsert.map { it.toDomain() })
        }
        payload.stockItems?.let            { stockRepository.upsertAll(it.map { dto -> dto.toDomain() }) }
        payload.choreLogs?.let             { choreRepository.upsertAllLogs(it.map { dto -> dto.toDomain() }) }
        // Apply recurring task deletions pushed by any member before upserting
        payload.deletedRecurringTaskIds?.let { ids ->
            ids.forEach { id ->
                choreRepository.deleteRecurringTask(id)
                deletionTracker.recordRecurringTaskDeletion(id)
            }
        }
        payload.recurringTasks?.let { dtos ->
            val deletedTaskIds = deletionTracker.getDeletedRecurringTaskIds()
            val toUpsert = dtos.filter { it.id !in deletedTaskIds }
            if (toUpsert.isNotEmpty()) choreRepository.upsertAllRecurring(toUpsert.map { it.toDomain() })
        }
        payload.choreAssignments?.let      { choreRepository.upsertAllAssignments(it.map { dto -> dto.toAssignmentDomain() }) }
        payload.expenses?.let              { expenseRepository.upsertAll(it.map { dto -> dto.toDomain() }) }
        payload.deletedBudgetIds?.let { ids ->
            ids.forEach { id ->
                budgetRepository.deleteBudget(id)
                deletionTracker.recordBudgetDeletion(id)
            }
        }
        payload.budgets?.let { dtos ->
            val deletedBudgetIds = deletionTracker.getDeletedBudgetIds()
            val toUpsert = dtos.filter { it.id !in deletedBudgetIds }
            if (toUpsert.isNotEmpty()) budgetRepository.upsertAll(toUpsert.map { it.toDomain() })
        }
        payload.customStockCategories?.let {
            customStockCategoryRepository.upsertAll(
                it.map { dto -> CustomStockCategory(dto.id, dto.name, dto.iconName) }
            )
        }
        payload.customExpenseCategories?.let { dtos ->
            customExpenseCategoryRepository.upsertAll(
                dtos.map { dto -> com.familyhome.app.domain.model.CustomExpenseCategory(dto.id, dto.name, dto.iconName) }
            )
        }
        payload.deletedPrayerGoalIds?.let { ids ->
            ids.forEach { id ->
                prayerRepository.deleteGoalSetting(id)
                deletionTracker.recordPrayerGoalDeletion(id)
            }
        }
        payload.prayerGoalSettings?.let { dtos ->
            val deletedGoalIds = deletionTracker.getDeletedPrayerGoalIds()
            val toUpsert = dtos.filter { it.id !in deletedGoalIds }
            if (toUpsert.isNotEmpty()) {
                prayerRepository.upsertAllGoalSettings(
                    toUpsert.map { dto ->
                        com.familyhome.app.domain.model.PrayerGoalSetting(
                            id              = dto.id,
                            sunnahKey       = dto.sunnahKey,
                            isEnabled       = dto.isEnabled,
                            assignedUserIds = dto.assignedTo?.split(",")?.filter { it.isNotBlank() },
                            reminderEnabled = dto.reminderEnabled,
                            createdBy       = dto.createdBy,
                            createdAt       = dto.createdAt,
                        )
                    }
                )
            }
        }
        payload.prayerLogs?.let { dtos ->
            prayerRepository.upsertAllLogs(
                dtos.map { dto -> com.familyhome.app.domain.model.PrayerLog(dto.id, dto.userId, dto.sunnahKey, dto.epochDay, dto.completedCount, dto.loggedAt) }
            )
        }
        // Merge family prayer reminders; also notify the leader if they're the target
        payload.prayerReminders?.let { reminders ->
            prayerReminderStore.mergeReminders(reminders)
            val leaderId = sessionRepository.getCurrentUserId()
            if (leaderId != null) {
                prayerReminderStore.processForCurrentUser(leaderId)
            }
        }
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

    // ── Member creation ──────────────────────────────────────────────────────

    suspend fun createMemberFromRequest(
        request: JoinRequestDto,
        role: Role,
        fatherId: String,
    ): Result<User> {
        val existingUsers = userRepository.getAllUsers().first()
        val nameTaken = existingUsers.any { it.name.equals(request.name, ignoreCase = true) }
        if (nameTaken) {
            return Result.failure(IllegalStateException("A family member named '${request.name}' already exists. Please use a unique name."))
        }

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
        return Result.success(newUser)
    }

    fun rejectRequest(deviceId: String) {
        onboardingState.setApproval(
            deviceId = deviceId,
            status   = ApprovalStatusDto(status = "rejected"),
        )
    }
}
