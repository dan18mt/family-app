package com.familyhome.app.domain.repository

import com.familyhome.app.domain.model.ChoreAssignment
import com.familyhome.app.domain.model.ChoreLog
import com.familyhome.app.domain.model.RecurringTask
import kotlinx.coroutines.flow.Flow

interface ChoreRepository {
    // Chore logs
    fun getChoreHistory(sinceTimestamp: Long): Flow<List<ChoreLog>>
    fun getChoreHistoryByUser(userId: String, sinceTimestamp: Long): Flow<List<ChoreLog>>
    suspend fun logChore(chore: ChoreLog)
    suspend fun deleteChoreLog(id: String)
    suspend fun upsertAllLogs(logs: List<ChoreLog>)

    // Recurring tasks
    fun getRecurringTasks(): Flow<List<RecurringTask>>
    suspend fun insertRecurringTask(task: RecurringTask)
    suspend fun updateRecurringTask(task: RecurringTask)
    suspend fun deleteRecurringTask(id: String)
    suspend fun upsertAllRecurring(tasks: List<RecurringTask>)

    // Assignments
    fun getAllAssignments(): Flow<List<ChoreAssignment>>
    fun getAssignmentsForUser(userId: String): Flow<List<ChoreAssignment>>
    fun getPendingAssignmentsForUser(userId: String): Flow<List<ChoreAssignment>>
    suspend fun insertAssignment(assignment: ChoreAssignment)
    suspend fun updateAssignment(assignment: ChoreAssignment)
    suspend fun upsertAllAssignments(assignments: List<ChoreAssignment>)
}
