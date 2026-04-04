package com.familyhome.app.domain.repository

import com.familyhome.app.domain.model.PrayerGoalSetting
import com.familyhome.app.domain.model.PrayerLog
import kotlinx.coroutines.flow.Flow

interface PrayerRepository {
    fun getAllGoalSettings(): Flow<List<PrayerGoalSetting>>
    suspend fun insertGoalSetting(setting: PrayerGoalSetting)
    suspend fun updateGoalSetting(setting: PrayerGoalSetting)
    suspend fun deleteGoalSetting(id: String)
    suspend fun upsertAllGoalSettings(settings: List<PrayerGoalSetting>)

    fun getLogsForDay(epochDay: Long): Flow<List<PrayerLog>>
    fun getLogsSince(fromDay: Long): Flow<List<PrayerLog>>
    fun getLogsForUserSince(userId: String, fromDay: Long): Flow<List<PrayerLog>>
    suspend fun upsertLog(log: PrayerLog)
    suspend fun upsertAllLogs(logs: List<PrayerLog>)
}
