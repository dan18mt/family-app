package com.familyhome.app.data.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.familyhome.app.MainActivity
import com.familyhome.app.R
import com.familyhome.app.domain.model.PrayerReminderDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages family-sent prayer reminders.
 *
 * Outgoing flow:  any member calls [addReminder] → stored locally → included in next sync push.
 * Incoming flow:  sync receives reminders → [mergeReminders] stores them →
 *                 [processForCurrentUser] checks & fires OS notifications for unseen ones.
 *
 * All reminders expire after 24 hours so old ones never re-surface.
 */
@Singleton
class PrayerReminderStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val remindersKey = stringPreferencesKey("prayer_reminders_json")
        private val seenIdsKey   = stringSetPreferencesKey("prayer_seen_reminder_ids")

        private const val CHANNEL_ID = "prayer_family_reminders"
        private const val EXPIRY_MS  = 24 * 60 * 60 * 1000L
    }

    private val json = Json { ignoreUnknownKeys = true }

    // ── Write ─────────────────────────────────────────────────────────────��───

    /** Queue a reminder to be sent to [reminder.targetUserId] on the next sync. */
    suspend fun addReminder(reminder: PrayerReminderDto) {
        dataStore.edit { prefs ->
            val current = decode(prefs[remindersKey])
            prefs[remindersKey] = encode(current + reminder)
        }
    }

    /**
     * Merge [incoming] reminders received from a sync payload.
     * Deduplicates by ID and drops entries older than [EXPIRY_MS].
     */
    suspend fun mergeReminders(incoming: List<PrayerReminderDto>) {
        if (incoming.isEmpty()) return
        dataStore.edit { prefs ->
            val existing = decode(prefs[remindersKey])
            val existingIds = existing.map { it.id }.toSet()
            val merged = existing + incoming.filter { it.id !in existingIds }
            prefs[remindersKey] = encode(pruneExpired(merged))
        }
    }

    /**
     * Returns all active (non-expired) reminders — used when building a sync payload
     * so this device's reminders are distributed to all other family devices.
     */
    suspend fun getActiveReminders(): List<PrayerReminderDto> {
        val prefs = dataStore.data.first()
        return pruneExpired(decode(prefs[remindersKey]))
    }

    /**
     * Checks for unseen reminders targeting [userId], shows an OS notification for each,
     * and records them as seen so they never fire again.
     */
    suspend fun processForCurrentUser(userId: String) {
        val prefs   = dataStore.data.first()
        val seenIds = prefs[seenIdsKey] ?: emptySet()
        val unseen  = pruneExpired(decode(prefs[remindersKey]))
            .filter { it.targetUserId == userId && it.id !in seenIds }

        if (unseen.isEmpty()) return

        ensureChannel()
        unseen.forEach { showNotification(it) }

        dataStore.edit { p ->
            p[seenIdsKey] = (p[seenIdsKey] ?: emptySet()) + unseen.map { it.id }.toSet()
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun pruneExpired(list: List<PrayerReminderDto>): List<PrayerReminderDto> {
        val cutoff = System.currentTimeMillis() - EXPIRY_MS
        return list.filter { it.sentAt >= cutoff }
    }

    private fun decode(raw: String?): List<PrayerReminderDto> {
        if (raw.isNullOrBlank()) return emptyList()
        return try { json.decodeFromString(raw) } catch (_: Exception) { emptyList() }
    }

    private fun encode(list: List<PrayerReminderDto>): String =
        json.encodeToString(list)

    private fun showNotification(reminder: PrayerReminderDto) {
        val nm = context.getSystemService(NotificationManager::class.java)

        val tapIntent = PendingIntent.getActivity(
            context,
            reminder.id.hashCode() and 0x7FFFFFFF,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🔔 ${context.getString(R.string.prayer_reminder_from, reminder.sentByName)}")
            .setContentText(context.getString(R.string.prayer_reminder_body))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.prayer_reminder_body_long, reminder.sentByName))
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(reminder.id.hashCode() and 0x7FFFFFFF, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.prayer_reminder_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.prayer_reminder_channel_desc)
                enableVibration(true)
            }
        )
    }
}
