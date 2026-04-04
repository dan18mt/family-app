package com.familyhome.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.familyhome.app.data.local.entity.PrayerGoalSettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PrayerGoalSettingDao {
    @Query("SELECT * FROM prayer_goal_settings ORDER BY createdAt ASC")
    fun getAllSettings(): Flow<List<PrayerGoalSettingEntity>>

    @Query("SELECT * FROM prayer_goal_settings")
    suspend fun getAllSettingsOneShot(): List<PrayerGoalSettingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: PrayerGoalSettingEntity)

    @Update
    suspend fun updateSetting(setting: PrayerGoalSettingEntity)

    @Query("DELETE FROM prayer_goal_settings WHERE id = :id")
    suspend fun deleteSetting(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(settings: List<PrayerGoalSettingEntity>)
}
