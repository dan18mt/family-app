package com.familyhome.app.data.notification

import android.annotation.SuppressLint
import android.app.AlarmManager
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
import com.familyhome.app.domain.repository.SessionRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AlarmReceiverEntryPoint {
    fun sessionRepository(): SessionRepository
}

/**
 * Receives AlarmManager broadcasts for chore reminders and overdue checks.
 *
 * Two types of alarms:
 *  - Reminder (isOverdue=false): fires X minutes before the scheduled time.
 *  - Overdue check (isOverdue=true): fires 1 hour after the scheduled time.
 *    Shows a pop-up notification with Done / Remind in 30 min / Open App actions.
 *
 * Action buttons handled here (per-device — never crosses to other devices):
 *  - ACTION_DONE: dismiss the overdue notification on this device only.
 *  - ACTION_REMIND_LATER: dismiss + reschedule overdue check 30 min later on this device only.
 */
class ChoreAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID_REMINDERS = "chore_reminders"
        const val CHANNEL_ID_OVERDUE   = "chore_overdue"

        const val ACTION_DONE          = "com.familyhome.app.CHORE_DONE"
        const val ACTION_REMIND_LATER  = "com.familyhome.app.CHORE_REMIND_LATER"
        const val EXTRA_TASK_ID        = AlarmScheduler.EXTRA_TASK_ID
        const val EXTRA_TASK_NAME      = AlarmScheduler.EXTRA_TASK_NAME
        const val EXTRA_IS_OVERDUE     = AlarmScheduler.EXTRA_IS_OVERDUE

        private fun overdueNotifId(taskId: String) = (taskId.hashCode() xor 0x00FF0000) and 0x7FFFFFFF
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DONE -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
                // Dismiss the overdue notification on this device only
                context.getSystemService(NotificationManager::class.java)
                    .cancel(overdueNotifId(taskId))
            }

            ACTION_REMIND_LATER -> {
                val taskId   = intent.getStringExtra(EXTRA_TASK_ID) ?: return
                val taskName = intent.getStringExtra(EXTRA_TASK_NAME) ?: "Chore"
                // Dismiss current overdue notification on this device only
                context.getSystemService(NotificationManager::class.java)
                    .cancel(overdueNotifId(taskId))
                // Re-schedule an overdue check 30 minutes from now on this device only
                scheduleSnooze(context, taskId, taskName, 30)
            }

            else -> {
                // Regular alarm: check assignment and show the appropriate notification
                val taskId    = intent.getStringExtra(EXTRA_TASK_ID) ?: return
                val taskName  = intent.getStringExtra(EXTRA_TASK_NAME) ?: "Chore"
                val isOverdue = intent.getBooleanExtra(EXTRA_IS_OVERDUE, false)

                val assignedTo = intent.getStringExtra(AlarmScheduler.EXTRA_ASSIGNED_TO)
                if (assignedTo != null) {
                    val ep = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        AlarmReceiverEntryPoint::class.java,
                    )
                    val currentUserId = runBlocking { ep.sessionRepository().getCurrentUserId() }
                    if (currentUserId != assignedTo) return
                }

                ensureChannels(context)

                if (isOverdue) showOverdueNotification(context, taskId, taskName)
                else           showReminderNotification(context, taskId, taskName)
            }
        }
    }

    // ── Notification helpers ─────────────────────────────────────────────────

    private fun showReminderNotification(context: Context, taskId: String, taskName: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val tapIntent = mainActivityPendingIntent(context, taskId)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Upcoming chore")
            .setContentText("\"$taskName\" is coming up soon. Don't forget!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(taskId.hashCode() and 0x7FFFFFFF, notification)
    }

    private fun showOverdueNotification(context: Context, taskId: String, taskName: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val tapIntent = mainActivityPendingIntent(context, taskId)

        val doneIntent = PendingIntent.getBroadcast(
            context,
            (taskId.hashCode() xor 0x0A0A0A0A) and 0x7FFFFFFF,
            Intent(ACTION_DONE).apply { putExtra(EXTRA_TASK_ID, taskId) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val remindIntent = PendingIntent.getBroadcast(
            context,
            (taskId.hashCode() xor 0x0B0B0B0B) and 0x7FFFFFFF,
            Intent(ACTION_REMIND_LATER).apply {
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_TASK_NAME, taskName)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_OVERDUE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Chore overdue!")
            .setContentText("\"$taskName\" hasn't been marked done yet.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("\"$taskName\" was scheduled but hasn't been marked as done. What do you want to do?"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(tapIntent)
            .setAutoCancel(false)
            .addAction(0, "Mark Done", doneIntent)
            .addAction(0, "Remind in 30 min", remindIntent)
            .addAction(0, "Open App", tapIntent)
            .build()

        nm.notify(overdueNotifId(taskId), notification)
    }

    private fun mainActivityPendingIntent(context: Context, taskId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getActivity(
            context,
            taskId.hashCode() and 0x7FFFFFFF,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)

        if (nm.getNotificationChannel(CHANNEL_ID_REMINDERS) == null) {
            nm.createNotificationChannel(NotificationChannel(
                CHANNEL_ID_REMINDERS,
                "Chore Reminders",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Reminders before a scheduled chore" })
        }
        if (nm.getNotificationChannel(CHANNEL_ID_OVERDUE) == null) {
            nm.createNotificationChannel(NotificationChannel(
                CHANNEL_ID_OVERDUE,
                "Overdue Chores",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Notifications when a chore hasn't been completed after 1 hour"
                enableVibration(true)
            })
        }
    }

    /** Schedule an overdue-style alarm [delayMinutes] from now for snooze. */
    @SuppressLint("MissingPermission")
    private fun scheduleSnooze(context: Context, taskId: String, taskName: String, delayMinutes: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val triggerAt    = System.currentTimeMillis() + delayMinutes * 60_000L
        val snoozeIntent = Intent(context, ChoreAlarmReceiver::class.java).apply {
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_NAME, taskName)
            putExtra(EXTRA_IS_OVERDUE, true)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            overdueNotifId(taskId),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (_: SecurityException) { /* Exact alarm permission missing — skip */ }
    }
}
