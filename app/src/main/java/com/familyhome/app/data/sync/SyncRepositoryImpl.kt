package com.familyhome.app.data.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.familyhome.app.data.mapper.*
import com.familyhome.app.data.notification.AlarmScheduler
import com.familyhome.app.data.notification.LowStockNotifier
import com.familyhome.app.data.notification.PrayerReminderScheduler
import com.familyhome.app.domain.model.SunnahGoal
import com.familyhome.app.data.onboarding.NsdHelper
import com.familyhome.app.domain.model.CustomExpenseCategory
import com.familyhome.app.domain.model.CustomExpenseCategoryDto
import com.familyhome.app.domain.model.CustomStockCategory
import com.familyhome.app.domain.model.CustomStockCategoryDto
import com.familyhome.app.domain.model.PrayerReminderDto
import com.familyhome.app.domain.model.SyncPayload
import com.familyhome.app.domain.model.SyncResult
import com.familyhome.app.domain.repository.*
import com.familyhome.app.domain.repository.PrayerRepository
import com.familyhome.app.domain.repository.SessionRepository
import com.familyhome.app.util.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncServer: SyncServer,
    private val syncClient: SyncClient,
    private val userRepository: UserRepository,
    private val stockRepository: StockRepository,
    private val choreRepository: ChoreRepository,
    private val expenseRepository: ExpenseRepository,
    private val budgetRepository: BudgetRepository,
    private val customStockCategoryRepository: CustomStockCategoryRepository,
    private val customExpenseCategoryRepository: CustomExpenseCategoryRepository,
    private val prayerRepository: PrayerRepository,
    private val alarmScheduler: AlarmScheduler,
    private val prayerReminderScheduler: PrayerReminderScheduler,
    private val sessionRepository: SessionRepository,
    private val lowStockNotifier: LowStockNotifier,
    private val presenceTracker: MemberPresenceTracker,
    private val deletionTracker: DeletionTracker,
    private val prayerReminderStore: PrayerReminderStore,
    private val nsdHelper: NsdHelper,
) {
    private val lastSyncKey = longPreferencesKey("last_sync_time")
    private val hostIpKey   = stringPreferencesKey("sync_host_ip")
    private val syncPort    = 8765

    // ── Host mode ────────────────────────────────────────────────────────────

    fun startHostServer() = syncServer.start(syncPort)
    fun stopHostServer()  = syncServer.stop()
    val isHostRunning: Boolean get() = syncServer.isRunning

    // ── Client mode ──────────────────────────────────────────────────────────

    suspend fun saveHostIp(ip: String) {
        context.dataStore.edit { it[hostIpKey] = ip }
    }

    fun getHostIpFlow(): Flow<String?> =
        context.dataStore.data.map { it[hostIpKey] }

    suspend fun ping(): Boolean {
        val ip = context.dataStore.data.first()[hostIpKey] ?: return false
        return syncClient.ping(ip, syncPort)
    }

    suspend fun syncWithHost(): SyncResult {
        val ip = context.dataStore.data.first()[hostIpKey]
            ?: return SyncResult.Error("Host IP not configured.")

        // Ensure persisted deletions are loaded before building the local payload,
        // preventing a cold-start race where deletedBudgetIds would appear empty.
        deletionTracker.awaitReady()

        return runCatching {
            // 1. Push local snapshot to host (excluding locally-tracked deleted users)
            val localPayload = buildLocalPayload()
            syncClient.push(ip, syncPort, localPayload)

            // 2. Pull host snapshot and merge into local DB
            val remotePayload = syncClient.pull(ip, syncPort)
            mergeRemotePayload(remotePayload)

            val now = System.currentTimeMillis()
            context.dataStore.edit { it[lastSyncKey] = now }
            SyncResult.Success(now)
        }.getOrElse { e ->
            SyncResult.Error(e.message ?: "Unknown sync error")
        }
    }

    fun getLastSyncTimeFlow(): Flow<Long?> =
        context.dataStore.data.map { it[lastSyncKey] }

    // ── Direct push (same-network) ───────────────────────────────────────────

    /**
     * Attempts to deliver [reminder] directly to the target device over local Wi-Fi.
     *
     * - If the target is a **member** (non-leader): looks up their IP in
     *   [NsdHelper.discoveredMembers] and POSTs to port [MemberNotifyServer.PORT].
     * - If the target is the **leader**: POSTs to the stored host IP on [syncPort].
     *
     * Returns `true` when the push was accepted (HTTP 2xx). Callers should still
     * store the reminder in [PrayerReminderStore] as a sync-based fallback.
     */
    suspend fun sendDirectReminder(targetUserId: String, reminder: PrayerReminderDto): Boolean {
        // Try member server first (advertised via NSD)
        val memberDevice = nsdHelper.discoveredMembers.value.firstOrNull { device ->
            device.serviceName == "FamilyHome_Member_$targetUserId"
        }
        if (memberDevice != null) {
            return syncClient.pushNotification(memberDevice.hostAddress, MemberNotifyServer.PORT, reminder)
        }

        // Fallback: try the leader's server at the stored host IP
        val hostIp = context.dataStore.data.first()[hostIpKey] ?: return false
        return syncClient.pushNotification(hostIp, syncPort, reminder)
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private suspend fun buildLocalPayload(): SyncPayload {
        val currentUserId = sessionRepository.getCurrentUserId()
        val deleted = deletionTracker.getDeletedUserIds()
        return SyncPayload(
            pusherId              = currentUserId,
            // Filter out locally-known deleted users so they are never re-pushed to leader
            users                 = userRepository.getAllUsers().first()
                .filter { it.id !in deleted }
                .map { it.toDto() },
            stockItems            = stockRepository.getAllItems().first().map { it.toDto() },
            choreLogs             = choreRepository.getChoreHistory(0L).first().map { it.toDto() },
            recurringTasks        = choreRepository.getRecurringTasks().first()
                .filter { it.id !in deletionTracker.getDeletedRecurringTaskIds() }
                .map { it.toDto() },
            choreAssignments      = choreRepository.getAllAssignments().first().map { it.toAssignmentDto() },
            expenses              = expenseRepository.getAllExpenses().first().map { it.toDto() },
            // Filter out locally-known deleted items so they are never re-pushed to the host
            budgets               = budgetRepository.getAllBudgets().first()
                .filter { it.id !in deletionTracker.getDeletedBudgetIds() }
                .map { it.toDto() },
            deletedBudgetIds          = deletionTracker.getDeletedBudgetIds().toList().ifEmpty { null },
            deletedRecurringTaskIds   = deletionTracker.getDeletedRecurringTaskIds().toList().ifEmpty { null },
            customStockCategories = customStockCategoryRepository.getAllCategories().first()
                .map { CustomStockCategoryDto(it.id, it.name, it.iconName) },
            customExpenseCategories = customExpenseCategoryRepository.getAllCategories().first()
                .map { CustomExpenseCategoryDto(it.id, it.name, it.iconName) },
            prayerGoalSettings = prayerRepository.getAllGoalSettings().first()
                .map { com.familyhome.app.domain.model.PrayerGoalSettingDto(
                    id = it.id, sunnahKey = it.sunnahKey, isEnabled = it.isEnabled,
                    assignedTo = it.assignedUserIds?.joinToString(","),
                    createdBy = it.createdBy, createdAt = it.createdAt,
                    reminderEnabled = it.reminderEnabled,
                ) },
            prayerLogs = prayerRepository.getLogsSince(0L).first()
                .map { com.familyhome.app.domain.model.PrayerLogDto(it.id, it.userId, it.sunnahKey, it.epochDay, it.completedCount, it.loggedAt) },
            deletedPrayerGoalIds = deletionTracker.getDeletedPrayerGoalIds().toList().ifEmpty { null },
            prayerReminders      = prayerReminderStore.getActiveReminders().ifEmpty { null },
        )
    }

    private suspend fun mergeRemotePayload(payload: SyncPayload) {
        // 1. Apply leader deletions first — leader is authoritative for user membership
        payload.deletedUserIds?.let { ids ->
            ids.forEach { userId ->
                userRepository.deleteUser(userId)
                // Record locally so this device never re-pushes the deleted user
                deletionTracker.recordUserDeletion(userId)
            }
        }

        // 2. Upsert data (skip any users that are now known to be deleted)
        val deleted = deletionTracker.getDeletedUserIds()
        payload.users?.let { dtos ->
            val toUpsert = dtos.filter { it.id !in deleted }
            if (toUpsert.isNotEmpty()) userRepository.upsertAll(toUpsert.map { it.toDomain() })
        }
        payload.stockItems?.let            { stockRepository.upsertAll(it.map { dto -> dto.toDomain() }) }
        payload.choreLogs?.let             { choreRepository.upsertAllLogs(it.map { dto -> dto.toDomain() }) }
        // Apply recurring task deletions from any device before upserting
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
        // Apply budget deletions from leader before upserting
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
                dtos.map { dto -> CustomExpenseCategory(dto.id, dto.name, dto.iconName) }
            )
        }
        // Apply prayer-goal deletions sent by the leader before upserting new data
        payload.deletedPrayerGoalIds?.let { ids ->
            ids.forEach { id ->
                prayerRepository.deleteGoalSetting(id)
                deletionTracker.recordPrayerGoalDeletion(id)
            }
        }

        val deletedGoalIds = deletionTracker.getDeletedPrayerGoalIds()
        payload.prayerGoalSettings?.let { dtos ->
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

        // Schedule or cancel prayer alarms on this device based on received goal settings.
        // This ensures reminders work for ALL assigned family members, not just the leader.
        val currentUserId = sessionRepository.getCurrentUserId()
        if (currentUserId != null) {
            payload.prayerGoalSettings?.forEach { dto ->
                val sunnah    = SunnahGoal.entries.firstOrNull { it.name == dto.sunnahKey } ?: return@forEach
                val startHour = sunnah.reminderHour ?: return@forEach // no fixed window → no alarm
                val startMin  = sunnah.reminderMinute ?: 0
                val assignedIds = dto.assignedTo?.split(",")?.filter { it.isNotBlank() }
                val isAssignedToMe = assignedIds == null || currentUserId in assignedIds
                if (dto.reminderEnabled && dto.isEnabled && isAssignedToMe) {
                    prayerReminderScheduler.schedule(
                        sunnahKey         = dto.sunnahKey,
                        hour              = startHour,
                        minute            = startMin,
                        windowStartHour   = startHour,
                        windowStartMinute = startMin,
                        windowEndHour     = sunnah.reminderEndHour ?: -1,
                        windowEndMinute   = sunnah.reminderEndMinute ?: 0,
                    )
                } else {
                    prayerReminderScheduler.cancel(dto.sunnahKey)
                }
            }
        }

        // 3. Update local presence tracker from leader's presence map (enables all-device status)
        payload.presenceMap?.forEach { (userId, lastSeenAt) ->
            presenceTracker.updateWithTimestamp(userId, lastSeenAt)
        }

        // 4. Schedule any alarms for tasks assigned to this user (skip deleted tasks)
        payload.recurringTasks?.let { dtos ->
            val currentUserId = sessionRepository.getCurrentUserId()
            if (currentUserId != null) {
                val deletedTaskIds = deletionTracker.getDeletedRecurringTaskIds()
                val now = System.currentTimeMillis()
                dtos.filter { it.id !in deletedTaskIds }.forEach { dto ->
                    val task = dto.toDomain()
                    if (task.assignedTo == currentUserId &&
                        task.scheduledAt != null &&
                        task.scheduledAt > now &&
                        task.reminderMinutesBefore != null) {
                        alarmScheduler.schedule(task)
                    }
                }
            }
        }

        // 5. Low stock notifications
        payload.stockItems?.let { dtos ->
            dtos.map { it.toDomain() }.forEach { item -> lowStockNotifier.notifyIfLow(item) }
        }

        // 6. Family prayer reminders — merge received reminders and notify if targeted at this user
        payload.prayerReminders?.let { reminders ->
            prayerReminderStore.mergeReminders(reminders)
            val currentUserId = sessionRepository.getCurrentUserId()
            if (currentUserId != null) {
                prayerReminderStore.processForCurrentUser(currentUserId)
            }
        }
    }
}
