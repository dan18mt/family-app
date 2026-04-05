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
import dagger.hilt.EntryPointAccessors
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

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
    }

    override fun onReceive(context: Context, intent: Intent) {
        val sunnahKey    = intent.getStringExtra(PrayerReminderScheduler.EXTRA_SUNNAH_KEY) ?: return
        val hour         = intent.getIntExtra(PrayerReminderScheduler.EXTRA_REMINDER_HOUR, -1)
        val minute       = intent.getIntExtra(PrayerReminderScheduler.EXTRA_REMINDER_MIN, 0)

        val sunnah = SunnahGoal.entries.firstOrNull { it.name == sunnahKey } ?: return

        ensureChannel(context)
        showNotification(context, sunnah)

        // Reschedule for tomorrow to keep the daily cycle alive
        if (hour >= 0) {
            val ep = EntryPointAccessors.fromApplication(
                context.applicationContext,
                PrayerAlarmEntryPoint::class.java,
            )
            ep.prayerReminderScheduler().schedule(sunnahKey, hour, minute)
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
