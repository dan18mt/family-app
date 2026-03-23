package com.familyhome.app.data.local.dao

import androidx.room.*
import com.familyhome.app.data.local.entity.ChoreLogEntity
import com.familyhome.app.data.local.entity.RecurringTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChoreLogDao {
    @Query("SELECT * FROM chore_logs WHERE doneAt >= :sinceTimestamp ORDER BY doneAt DESC")
    fun getChoreHistory(sinceTimestamp: Long): Flow<List<ChoreLogEntity>>

    @Query("""
        SELECT * FROM chore_logs
        WHERE doneBy = :userId AND doneAt >= :sinceTimestamp
        ORDER BY doneAt DESC
    """)
    fun getChoreHistoryByUser(userId: String, sinceTimestamp: Long): Flow<List<ChoreLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ChoreLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(logs: List<ChoreLogEntity>)
}

@Dao
interface RecurringTaskDao {
    @Query("SELECT * FROM recurring_tasks ORDER BY nextDueAt ASC")
    fun getAllTasks(): Flow<List<RecurringTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: RecurringTaskEntity)

    @Update
    suspend fun updateTask(task: RecurringTaskEntity)

    @Query("DELETE FROM recurring_tasks WHERE id = :id")
    suspend fun deleteTask(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tasks: List<RecurringTaskEntity>)
}
