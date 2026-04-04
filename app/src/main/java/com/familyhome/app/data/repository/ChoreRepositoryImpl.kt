package com.familyhome.app.data.repository

import com.familyhome.app.data.local.dao.ChoreAssignmentDao
import com.familyhome.app.data.local.dao.ChoreLogDao
import com.familyhome.app.data.local.dao.RecurringTaskDao
import com.familyhome.app.data.mapper.*
import com.familyhome.app.domain.model.AssignmentStatus
import com.familyhome.app.domain.model.ChoreAssignment
import com.familyhome.app.domain.model.ChoreLog
import com.familyhome.app.domain.model.RecurringTask
import com.familyhome.app.domain.repository.ChoreRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ChoreRepositoryImpl @Inject constructor(
    private val choreLogDao: ChoreLogDao,
    private val recurringTaskDao: RecurringTaskDao,
    private val choreAssignmentDao: ChoreAssignmentDao,
) : ChoreRepository {

    override fun getChoreHistory(sinceTimestamp: Long): Flow<List<ChoreLog>> =
        choreLogDao.getChoreHistory(sinceTimestamp).map { list -> list.map { it.toDomain() } }

    override fun getChoreHistoryByUser(userId: String, sinceTimestamp: Long): Flow<List<ChoreLog>> =
        choreLogDao.getChoreHistoryByUser(userId, sinceTimestamp).map { list -> list.map { it.toDomain() } }

    override suspend fun logChore(chore: ChoreLog) =
        choreLogDao.insertLog(chore.toEntity())

    override suspend fun deleteChoreLog(id: String) =
        choreLogDao.deleteLog(id)

    override suspend fun upsertAllLogs(logs: List<ChoreLog>) =
        choreLogDao.upsertAll(logs.map { it.toEntity() })

    override fun getRecurringTasks(): Flow<List<RecurringTask>> =
        recurringTaskDao.getAllTasks().map { list -> list.map { it.toDomain() } }

    override suspend fun insertRecurringTask(task: RecurringTask) =
        recurringTaskDao.insertTask(task.toEntity())

    override suspend fun updateRecurringTask(task: RecurringTask) =
        recurringTaskDao.updateTask(task.toEntity())

    override suspend fun deleteRecurringTask(id: String) =
        recurringTaskDao.deleteTask(id)

    override suspend fun upsertAllRecurring(tasks: List<RecurringTask>) =
        recurringTaskDao.upsertAll(tasks.map { it.toEntity() })

    override fun getAllAssignments(): Flow<List<ChoreAssignment>> =
        choreAssignmentDao.getAllAssignments().map { list -> list.map { it.toDomain() } }

    override fun getAssignmentsForUser(userId: String): Flow<List<ChoreAssignment>> =
        choreAssignmentDao.getAssignmentsForUser(userId).map { list -> list.map { it.toDomain() } }

    override fun getPendingAssignmentsForUser(userId: String): Flow<List<ChoreAssignment>> =
        choreAssignmentDao.getPendingAssignmentsForUser(userId).map { list -> list.map { it.toDomain() } }

    override suspend fun insertAssignment(assignment: ChoreAssignment) =
        choreAssignmentDao.insertAssignment(assignment.toEntity())

    override suspend fun updateAssignment(assignment: ChoreAssignment) =
        choreAssignmentDao.updateAssignment(assignment.toEntity())

    /**
     * Status-preserving merge: never overwrite a responded (ACCEPTED/DECLINED) assignment
     * with a stale PENDING copy from another device.  Last-write-wins is correct for
     * all other fields; the status field uses "most-advanced wins" semantics.
     */
    override suspend fun upsertAllAssignments(assignments: List<ChoreAssignment>) {
        val existingMap = choreAssignmentDao.getAllAssignmentsOneShot().associateBy { it.id }
        val toUpsert = assignments.filter { incoming ->
            val existing = existingMap[incoming.id]
            // Allow upsert unless: existing is responded AND incoming is still PENDING
            existing == null ||
            incoming.status != AssignmentStatus.PENDING ||
            existing.status == AssignmentStatus.PENDING.name
        }
        if (toUpsert.isNotEmpty()) {
            choreAssignmentDao.upsertAll(toUpsert.map { it.toEntity() })
        }
    }
}
