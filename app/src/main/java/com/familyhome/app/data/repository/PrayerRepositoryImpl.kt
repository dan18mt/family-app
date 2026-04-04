package com.familyhome.app.data.repository

import com.familyhome.app.data.local.dao.PrayerGoalSettingDao
import com.familyhome.app.data.local.dao.PrayerLogDao
import com.familyhome.app.data.local.entity.PrayerGoalSettingEntity
import com.familyhome.app.data.local.entity.PrayerLogEntity
import com.familyhome.app.domain.model.PrayerGoalSetting
import com.familyhome.app.domain.model.PrayerLog
import com.familyhome.app.domain.repository.PrayerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PrayerRepositoryImpl @Inject constructor(
    private val goalSettingDao: PrayerGoalSettingDao,
    private val prayerLogDao: PrayerLogDao,
) : PrayerRepository {

    override fun getAllGoalSettings(): Flow<List<PrayerGoalSetting>> =
        goalSettingDao.getAllSettings().map { list -> list.map { it.toDomain() } }

    override suspend fun insertGoalSetting(setting: PrayerGoalSetting) =
        goalSettingDao.insertSetting(setting.toEntity())

    override suspend fun updateGoalSetting(setting: PrayerGoalSetting) =
        goalSettingDao.updateSetting(setting.toEntity())

    override suspend fun deleteGoalSetting(id: String) =
        goalSettingDao.deleteSetting(id)

    override suspend fun upsertAllGoalSettings(settings: List<PrayerGoalSetting>) =
        goalSettingDao.upsertAll(settings.map { it.toEntity() })

    override fun getLogsForDay(epochDay: Long): Flow<List<PrayerLog>> =
        prayerLogDao.getLogsForDay(epochDay).map { list -> list.map { it.toDomain() } }

    override fun getLogsSince(fromDay: Long): Flow<List<PrayerLog>> =
        prayerLogDao.getLogsSince(fromDay).map { list -> list.map { it.toDomain() } }

    override fun getLogsForUserSince(userId: String, fromDay: Long): Flow<List<PrayerLog>> =
        prayerLogDao.getLogsForUserSince(userId, fromDay).map { list -> list.map { it.toDomain() } }

    override suspend fun upsertLog(log: PrayerLog) =
        prayerLogDao.insertLog(log.toEntity())

    override suspend fun upsertAllLogs(logs: List<PrayerLog>) =
        prayerLogDao.upsertAll(logs.map { it.toEntity() })
}

// ── Mappers ────────────────────────────────────────────────────────────────────

private fun PrayerGoalSettingEntity.toDomain() = PrayerGoalSetting(
    id = id, sunnahKey = sunnahKey, isEnabled = isEnabled,
    assignedTo = assignedTo, createdBy = createdBy, createdAt = createdAt,
)

private fun PrayerGoalSetting.toEntity() = PrayerGoalSettingEntity(
    id = id, sunnahKey = sunnahKey, isEnabled = isEnabled,
    assignedTo = assignedTo, createdBy = createdBy, createdAt = createdAt,
)

private fun PrayerLogEntity.toDomain() = PrayerLog(
    id = id, userId = userId, sunnahKey = sunnahKey,
    epochDay = epochDay, completedCount = completedCount, loggedAt = loggedAt,
)

private fun PrayerLog.toEntity() = PrayerLogEntity(
    id = id, userId = userId, sunnahKey = sunnahKey,
    epochDay = epochDay, completedCount = completedCount, loggedAt = loggedAt,
)
