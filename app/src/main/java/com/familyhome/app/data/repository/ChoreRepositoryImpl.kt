package com.familyhome.app.data.repository

import com.familyhome.app.data.local.dao.ChoreLogDao
import com.familyhome.app.data.local.dao.RecurringTaskDao
import com.familyhome.app.data.mapper.*
import com.familyhome.app.domain.model.ChoreLog
import com.familyhome.app.domain.model.RecurringTask
import com.familyhome.app.domain.repository.ChoreRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ChoreRepositoryImpl @Inject constructor(
    private val choreLogDao: ChoreLogDao,
    private val recurringTaskDao: RecurringTaskDao,
) : ChoreRepository {

    override fun getChoreHistory(sinceTimestamp: Long): Flow<List<ChoreLog>> =
        choreLogDao.getChoreHistory(sinceTimestamp).map { list -> list.map { it.toDomain() } }

    override fun getChoreHistoryByUser(userId: String, sinceTimestamp: Long): Flow<List<ChoreLog>> =
        choreLogDao.getChoreHistoryByUser(userId, sinceTimestamp).map { list -> list.map { it.toDomain() } }

    override suspend fun logChore(chore: ChoreLog) =
        choreLogDao.insertLog(chore.toEntity())

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
}
