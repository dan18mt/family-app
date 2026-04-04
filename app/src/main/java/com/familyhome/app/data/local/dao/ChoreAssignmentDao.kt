package com.familyhome.app.data.local.dao

import androidx.room.*
import com.familyhome.app.data.local.entity.ChoreAssignmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChoreAssignmentDao {

    /** Assignments where this user is the recipient (for the member view). */
    @Query("SELECT * FROM chore_assignments WHERE assignedTo = :userId ORDER BY assignedAt DESC")
    fun getAssignmentsForUser(userId: String): Flow<List<ChoreAssignmentEntity>>

    /** All assignments across the family (for the leader view). */
    @Query("SELECT * FROM chore_assignments ORDER BY assignedAt DESC")
    fun getAllAssignments(): Flow<List<ChoreAssignmentEntity>>

    /** Pending assignments for a specific user (shown as action items). */
    @Query("SELECT * FROM chore_assignments WHERE assignedTo = :userId AND status = 'PENDING' ORDER BY assignedAt DESC")
    fun getPendingAssignmentsForUser(userId: String): Flow<List<ChoreAssignmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssignment(assignment: ChoreAssignmentEntity)

    @Update
    suspend fun updateAssignment(assignment: ChoreAssignmentEntity)

    @Query("DELETE FROM chore_assignments WHERE id = :id")
    suspend fun deleteAssignment(id: String)

    /** One-shot (non-Flow) snapshot used for status-preserving merge during sync. */
    @Query("SELECT * FROM chore_assignments")
    suspend fun getAllAssignmentsOneShot(): List<ChoreAssignmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(assignments: List<ChoreAssignmentEntity>)
}
