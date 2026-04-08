package com.familyhome.app.data.notification

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules and cancels daily prayer reminder alarms.
 *
 * Each alarm fires once at the specified [hour]:[minute] (local time).
 * [PrayerAlarmReceiver] handles the broadcast and reschedules for the next day,
 * creating a self-sustaining daily reminder cycle.
 */
@Singleton
class PrayerReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "PrayerReminderScheduler"

        const val EXTRA_SUNNAH_KEY       = "sunnah_key"
        /** The time this specific alarm slot fires (changes each 15-min cycle). */
        const val EXTRA_REMINDER_HOUR    = "reminder_hour"
        const val EXTRA_REMINDER_MIN     = "reminder_minute"
        /** Original window start — constant, used to reschedule for tomorrow. */
        const val EXTRA_WINDOW_START_HOUR = "window_start_hour"
        const val EXTRA_WINDOW_START_MIN  = "window_start_min"
        /** Window end time — constant. -1 means no end (fire once daily). */
        const val EXTRA_WINDOW_END_HOUR   = "window_end_hour"
        const val EXTRA_WINDOW_END_MIN    = "window_end_min"

        private fun requestCode(sunnahKey: String): Int =
            ("prayer_reminder_$sunnahKey").hashCode() and 0x7FFFFFFF
    }

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /**
     * Schedule (or reschedule) a reminder for [sunnahKey].
     *
     * [hour]/[minute] = the slot to fire at (initial call = window start; subsequent calls =
     * the next 15-min slot).
     *
     * [windowStartHour]/[windowStartMinute] = the original window start; kept constant in the
     * intent so the receiver can reschedule for tomorrow at the right time.
     *
     * [windowEndHour] = -1 means no time window — fires once daily (original behaviour).
     */
    @SuppressLint("MissingPermission")
    fun schedule(
        sunnahKey: String,
        hour: Int,
        minute: Int,
        windowStartHour: Int = hour,
        windowStartMinute: Int = minute,
        windowEndHour: Int = -1,
        windowEndMinute: Int = 0,
    ) {
        val triggerAt = nextOccurrence(hour, minute)
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            putExtra(EXTRA_SUNNAH_KEY,        sunnahKey)
            putExtra(EXTRA_REMINDER_HOUR,     hour)
            putExtra(EXTRA_REMINDER_MIN,      minute)
            putExtra(EXTRA_WINDOW_START_HOUR, windowStartHour)
            putExtra(EXTRA_WINDOW_START_MIN,  windowStartMinute)
            putExtra(EXTRA_WINDOW_END_HOUR,   windowEndHour)
            putExtra(EXTRA_WINDOW_END_MIN,    windowEndMinute)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode(sunnahKey),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
            Log.d(TAG, "Reminder scheduled for '$sunnahKey' at $hour:${"%02d".format(minute)}")
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot schedule exact alarm — permission missing: ${e.message}")
        }
    }

    /** Cancel the daily reminder for [sunnahKey]. */
    fun cancel(sunnahKey: String) {
        val intent = Intent(context, PrayerAlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode(sunnahKey),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pi)
        pi.cancel()
        Log.d(TAG, "Reminder cancelled for '$sunnahKey'")
    }

    /** Returns the epoch-ms of the next occurrence of [hour]:[minute] (today if still in the future, else tomorrow). */
    private fun nextOccurrence(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE,      minute)
            set(Calendar.SECOND,      0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }
}
