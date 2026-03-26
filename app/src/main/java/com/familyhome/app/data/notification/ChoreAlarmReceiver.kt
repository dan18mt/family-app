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

/**
 * Receives AlarmManager broadcasts for chore reminders and overdue checks.
 *
 * Two types of alarms:
 *  - Reminder (isOverdue=false): fires X minutes before the scheduled time.
 *  - Overdue check (isOverdue=true): fires 1 hour after the scheduled time.
 *    Shows a pop-up notification with Done / Reschedule / Remind Later actions.
 */
class ChoreAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID_REMINDERS = "chore_reminders"
        const val CHANNEL_ID_OVERDUE   = "chore_overdue"

        // Action constants for notification action buttons
        const val ACTION_DONE          = "com.familyhome.app.CHORE_DONE"
        const val ACTION_REMIND_LATER  = "com.familyhome.app.CHORE_REMIND_LATER"
        const val EXTRA_TASK_ID        = AlarmScheduler.EXTRA_TASK_ID
        const val EXTRA_TASK_NAME      = AlarmScheduler.EXTRA_TASK_NAME
        const val EXTRA_IS_OVERDUE     = AlarmScheduler.EXTRA_IS_OVERDUE
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId   = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val taskName = intent.getStringExtra(EXTRA_TASK_NAME) ?: "Chore"
        val isOverdue = intent.getBooleanExtra(EXTRA_IS_OVERDUE, false)

        ensureChannels(context)

        if (isOverdue) {
            showOverdueNotification(context, taskId, taskName)
        } else {
            showReminderNotification(context, taskId, taskName)
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

        // "Done" action
        val doneIntent = PendingIntent.getBroadcast(
            context,
            (taskId.hashCode() xor 0x0A0A0A0A) and 0x7FFFFFFF,
            Intent(ACTION_DONE).apply { putExtra(EXTRA_TASK_ID, taskId) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // "Remind in 30 min" action
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

        nm.notify((taskId.hashCode() xor 0x00FF0000) and 0x7FFFFFFF, notification)
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
}
