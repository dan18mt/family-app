package com.familyhome.app.data.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.familyhome.app.data.mapper.*
import com.familyhome.app.data.notification.AlarmScheduler
import com.familyhome.app.data.notification.LowStockNotifier
import com.familyhome.app.domain.model.CustomExpenseCategory
import com.familyhome.app.domain.model.CustomExpenseCategoryDto
import com.familyhome.app.domain.model.CustomStockCategory
import com.familyhome.app.domain.model.CustomStockCategoryDto
import com.familyhome.app.domain.model.SyncPayload
import com.familyhome.app.domain.model.SyncResult
import com.familyhome.app.domain.repository.*
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
    private val alarmScheduler: AlarmScheduler,
    private val sessionRepository: SessionRepository,
    private val lowStockNotifier: LowStockNotifier,
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

        return runCatching {
            // 1. Push local snapshot to host
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

    // ── Internal helpers ─────────────────────────────────────────────────────

    private suspend fun buildLocalPayload(): SyncPayload {
        val currentUserId = sessionRepository.getCurrentUserId()
        return SyncPayload(
            pusherId              = currentUserId,
            users                 = userRepository.getAllUsers().first().map { it.toDto() },
            stockItems            = stockRepository.getAllItems().first().map { it.toDto() },
            choreLogs             = choreRepository.getChoreHistory(0L).first().map { it.toDto() },
            recurringTasks        = choreRepository.getRecurringTasks().first().map { it.toDto() },
            choreAssignments      = choreRepository.getAllAssignments().first().map { it.toAssignmentDto() },
            expenses              = expenseRepository.getAllExpenses().first().map { it.toDto() },
            budgets               = budgetRepository.getAllBudgets().first().map { it.toDto() },
            customStockCategories = customStockCategoryRepository.getAllCategories().first()
                .map { CustomStockCategoryDto(it.id, it.name, it.iconName) },
            customExpenseCategories = customExpenseCategoryRepository.getAllCategories().first()
                .map { CustomExpenseCategoryDto(it.id, it.name, it.iconName) },
        )
    }

    private suspend fun mergeRemotePayload(payload: SyncPayload) {
        payload.users?.let                 { userRepository.upsertAll(it.map { dto -> dto.toDomain() }) }
        payload.stockItems?.let            { stockRepository.upsertAll(it.map { dto -> dto.toDomain() }) }
        payload.choreLogs?.let             { choreRepository.upsertAllLogs(it.map { dto -> dto.toDomain() }) }
        payload.recurringTasks?.let        { choreRepository.upsertAllRecurring(it.map { dto -> dto.toDomain() }) }
        payload.choreAssignments?.let      { choreRepository.upsertAllAssignments(it.map { dto -> dto.toAssignmentDomain() }) }
        payload.expenses?.let              { expenseRepository.upsertAll(it.map { dto -> dto.toDomain() }) }
        payload.budgets?.let               { budgetRepository.upsertAll(it.map { dto -> dto.toDomain() }) }
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

        payload.recurringTasks?.let { dtos ->
            val currentUserId = sessionRepository.getCurrentUserId()
            if (currentUserId != null) {
                val now = System.currentTimeMillis()
                dtos.forEach { dto ->
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

        payload.stockItems?.let { dtos ->
            dtos.map { it.toDomain() }.forEach { item ->
                lowStockNotifier.notifyIfLow(item)
            }
        }
    }
}
