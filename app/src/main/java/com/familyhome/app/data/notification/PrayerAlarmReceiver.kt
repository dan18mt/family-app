package com.familyhome.app.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.familyhome.app.MainActivity
import com.familyhome.app.R
import com.familyhome.app.domain.model.SunnahGoal
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.Calendar

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PrayerAlarmEntryPoint {
    fun prayerReminderScheduler(): PrayerReminderScheduler
}

/**
 * Receives AlarmManager broadcasts for prayer reminder notifications.
 *
 * After firing the notification it reschedules itself for the next day,
 * creating a self-sustaining daily reminder without setRepeating (which is inexact on modern Android).
 */
class PrayerAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "prayer_reminders"
        private const val INTERVAL_MINUTES = 15
        private const val INTERVAL_MS = INTERVAL_MINUTES * 60 * 1_000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val sunnahKey      = intent.getStringExtra(PrayerReminderScheduler.EXTRA_SUNNAH_KEY) ?: return
        val windowStartH   = intent.getIntExtra(PrayerReminderScheduler.EXTRA_WINDOW_START_HOUR, -1)
        val windowStartMin = intent.getIntExtra(PrayerReminderScheduler.EXTRA_WINDOW_START_MIN, 0)
        val windowEndH     = intent.getIntExtra(PrayerReminderScheduler.EXTRA_WINDOW_END_HOUR, -1)
        val windowEndMin   = intent.getIntExtra(PrayerReminderScheduler.EXTRA_WINDOW_END_MIN, 0)

        val sunnah = SunnahGoal.entries.firstOrNull { it.name == sunnahKey } ?: return

        ensureChannel(context)
        showNotification(context, sunnah)

        if (windowStartH < 0) return  // no start time stored — nothing to reschedule

        val ep = EntryPointAccessors.fromApplication(
            context.applicationContext,
            PrayerAlarmEntryPoint::class.java,
        )
        val scheduler = ep.prayerReminderScheduler()

        if (windowEndH < 0) {
            // No end boundary — fire once daily, reschedule for tomorrow at start
            scheduler.schedule(
                sunnahKey,
                windowStartH, windowStartMin,
                windowStartH, windowStartMin,
            )
            return
        }

        // ── 15-minute cycling within window ─────────────────────────────────
        // Compute the next 15-min slot (~now + 15 min, normalised to the minute)
        val nextCal = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis() + INTERVAL_MS
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nextH   = nextCal.get(Calendar.HOUR_OF_DAY)
        val nextMin = nextCal.get(Calendar.MINUTE)

        // Last permitted slot = window end − 15 min
        val endTotalMin  = windowEndH * 60 + windowEndMin
        val lastSlotMin  = endTotalMin - INTERVAL_MINUTES
        val nextTotalMin = nextH * 60 + nextMin

        if (nextTotalMin <= lastSlotMin) {
            // Still inside the window — schedule the next slot
            scheduler.schedule(
                sunnahKey,
                nextH, nextMin,
                windowStartH, windowStartMin,
                windowEndH, windowEndMin,
            )
        } else {
            // Window exhausted for today — reschedule for tomorrow at window start
            scheduler.schedule(
                sunnahKey,
                windowStartH, windowStartMin,
                windowStartH, windowStartMin,
                windowEndH, windowEndMin,
            )
        }
    }

    private fun showNotification(context: Context, sunnah: SunnahGoal) {
        val nm = context.getSystemService(NotificationManager::class.java)

        val tapIntent = PendingIntent.getActivity(
            context,
            sunnah.name.hashCode() and 0x7FFFFFFF,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val windowLabel = sunnah.reminderWindowLabel()
        val bodyText = if (windowLabel != null)
            "Waktu: $windowLabel  •  ${sunnah.reward}"
        else
            sunnah.reward

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${sunnah.rewardIcon} Waktunya ${sunnah.title}")
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "\"${sunnah.hadith.take(200)}\"\n\n— ${sunnah.source}"
            ))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(sunnah.name.hashCode() and 0x7FFFFFFF, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Prayer Reminders",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Daily reminders for sunnah ibadah time windows"
                enableVibration(true)
            }
        )
    }
}
