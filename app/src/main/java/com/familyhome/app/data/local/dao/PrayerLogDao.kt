package com.familyhome.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.familyhome.app.data.local.entity.PrayerLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PrayerLogDao {
    @Query("SELECT * FROM prayer_logs WHERE epochDay = :epochDay ORDER BY loggedAt DESC")
    fun getLogsForDay(epochDay: Long): Flow<List<PrayerLogEntity>>

    @Query("SELECT * FROM prayer_logs WHERE epochDay >= :fromDay ORDER BY epochDay DESC")
    fun getLogsSince(fromDay: Long): Flow<List<PrayerLogEntity>>

    @Query("SELECT * FROM prayer_logs WHERE userId = :userId AND epochDay >= :fromDay ORDER BY epochDay DESC")
    fun getLogsForUserSince(userId: String, fromDay: Long): Flow<List<PrayerLogEntity>>

    @Query("SELECT * FROM prayer_logs")
    suspend fun getAllLogsOneShot(): List<PrayerLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: PrayerLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(logs: List<PrayerLogEntity>)

    @Query("DELETE FROM prayer_logs WHERE id = :id")
    suspend fun deleteLog(id: String)
}
