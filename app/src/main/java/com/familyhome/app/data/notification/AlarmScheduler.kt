package com.familyhome.app.data.notification

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.familyhome.app.domain.model.RecurringTask
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "AlarmScheduler"
        const val EXTRA_TASK_ID   = "task_id"
        const val EXTRA_TASK_NAME = "task_name"
        const val EXTRA_IS_OVERDUE = "is_overdue"
        const val EXTRA_ASSIGNED_TO = "assigned_to"

        /** RequestCode = positive int derived from taskId hash for reminder alarms. */
        private fun reminderRequestCode(taskId: String) = taskId.hashCode() and 0x7FFFFFFF

        /** RequestCode for the 1-hour overdue check alarm. */
        private fun overdueRequestCode(taskId: String) = (taskId.hashCode() xor 0x00FF00FF) and 0x7FFFFFFF
    }

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** Can the app schedule exact alarms on this device? */
    fun canScheduleExact(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * Schedules a reminder alarm [task.reminderMinutesBefore] minutes before [task.scheduledAt],
     * and a follow-up overdue check 1 hour after [task.scheduledAt].
     */
    @SuppressLint("MissingPermission")
    fun schedule(task: RecurringTask) {
        val scheduledAt = task.scheduledAt ?: return
        val reminderMinutes = task.reminderMinutesBefore ?: return

        val reminderTime = scheduledAt - reminderMinutes * 60_000L
        if (reminderTime > System.currentTimeMillis()) {
            setExactAlarm(
                triggerAtMillis = reminderTime,
                requestCode     = reminderRequestCode(task.id),
                taskId          = task.id,
                taskName        = task.taskName,
                isOverdue       = false,
                assignedTo      = task.assignedTo,
            )
            Log.d(TAG, "Reminder alarm set for '${task.taskName}' at $reminderTime")
        }

        // Always set a 1-hour overdue check
        val overdueTime = scheduledAt + 60 * 60_000L
        if (overdueTime > System.currentTimeMillis()) {
            setExactAlarm(
                triggerAtMillis = overdueTime,
                requestCode     = overdueRequestCode(task.id),
                taskId          = task.id,
                taskName        = task.taskName,
                isOverdue       = true,
                assignedTo      = task.assignedTo,
            )
            Log.d(TAG, "Overdue check alarm set for '${task.taskName}' at $overdueTime")
        }
    }

    /** Cancel both reminder and overdue alarms for a task. */
    fun cancel(taskId: String) {
        cancel(reminderRequestCode(taskId), taskId)
        cancel(overdueRequestCode(taskId), taskId)
    }

    @SuppressLint("MissingPermission")
    private fun setExactAlarm(
        triggerAtMillis: Long,
        requestCode: Int,
        taskId: String,
        taskName: String,
        isOverdue: Boolean,
        assignedTo: String? = null,
    ) {
        val intent = Intent(context, ChoreAlarmReceiver::class.java).apply {
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_NAME, taskName)
            putExtra(EXTRA_IS_OVERDUE, isOverdue)
            putExtra(EXTRA_ASSIGNED_TO, assignedTo)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                )
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot schedule exact alarm — permission missing: ${e.message}")
        }
    }

    private fun cancel(requestCode: Int, taskId: String) {
        val intent = Intent(context, ChoreAlarmReceiver::class.java).apply {
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_ASSIGNED_TO, null as String?)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }
}
