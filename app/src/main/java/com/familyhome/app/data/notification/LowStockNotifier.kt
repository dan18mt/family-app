package com.familyhome.app.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.familyhome.app.MainActivity
import com.familyhome.app.R
import com.familyhome.app.domain.model.AppNotification
import com.familyhome.app.domain.model.NotificationType
import com.familyhome.app.domain.model.StockItem
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fires low-stock alerts through two channels:
 *
 * 1. **In-app [NotificationCenter]** — deduplicates by sourceId, persists
 *    snooze / silence state across sessions.
 *
 * 2. **OS-level notification** — shown only once per "low stock event":
 *    - Never re-posted on subsequent syncs while the item is still low.
 *    - Suppressed when the user has silenced or snoozed the alert.
 *    - Automatically cancelled when the item is restocked.
 *    - Re-posted after a snooze expires (on the next sync cycle).
 *    - Includes inline "Snooze 1h" and "Silence" action buttons so the
 *      user can act directly from the notification tray.
 */
@Singleton
class LowStockNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationCenter: NotificationCenter,
) {
    companion object {
        const val CHANNEL_ID = "low_stock_alerts"
    }

    /**
     * SourceIds for which an OS notification is currently active in this
     * process lifetime. Thread-safe: accessed from IO (sync) and main thread
     * (BroadcastReceiver callbacks).
     */
    private val activeOsNotifications: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap())

    // ── Public API ────────────────────────────────────────────────────────────

    fun notifyIfLow(item: StockItem) {
        val sourceId = "low_stock_${item.id}"
        val notifId  = osNotifId(sourceId)

        if (!item.isLowStock) {
            // Item restocked — clear OS notification and stop tracking
            if (activeOsNotifications.remove(sourceId)) {
                nm().cancel(notifId)
            }
            return
        }

        // Post to in-app center (handles dedup, snooze, silence for in-app list)
        notificationCenter.post(
            AppNotification(
                type     = NotificationType.LOW_STOCK,
                title    = "Low stock: ${item.name}",
                message  = "${item.quantity} ${item.unit} remaining (min: ${item.minQuantity})",
                sourceId = sourceId,
            )
        )

        // If a previous snooze has expired, allow re-notification by clearing the
        // active-tracking entry.  The next sync will then re-post the OS notification.
        if (notificationCenter.hasSnoozeExpired(sourceId)) {
            activeOsNotifications.remove(sourceId)
        }

        // Gate: only post a new OS notification when genuinely new (not already
        // shown this session) AND the user hasn't silenced / snoozed / dismissed it.
        if (sourceId !in activeOsNotifications && !notificationCenter.isSuppressed(sourceId)) {
            activeOsNotifications.add(sourceId)
            ensureChannel()
            showOsNotification(item, sourceId, notifId)
        }
    }

    /**
     * Cancels the OS notification for [sourceId] and removes it from the
     * active-tracking set.  Called by [StockNotificationActionReceiver] when
     * the user taps "Snooze 1h" or "Silence" directly from the notification tray.
     */
    fun cancelOsNotification(sourceId: String) {
        activeOsNotifications.remove(sourceId)
        nm().cancel(osNotifId(sourceId))
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun showOsNotification(item: StockItem, sourceId: String, notifId: Int) {
        // Tap → open app
        val tapIntent = PendingIntent.getActivity(
            context,
            notifId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Inline action: Snooze 1 hour
        val snoozeIntent = PendingIntent.getBroadcast(
            context,
            notifId + 1,
            Intent(context, StockNotificationActionReceiver::class.java).apply {
                action = StockNotificationActionReceiver.ACTION_SNOOZE_1H
                putExtra(StockNotificationActionReceiver.EXTRA_SOURCE_ID, sourceId)
                putExtra(StockNotificationActionReceiver.EXTRA_NOTIF_ID,  notifId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Inline action: Silence indefinitely
        val silenceIntent = PendingIntent.getBroadcast(
            context,
            notifId + 2,
            Intent(context, StockNotificationActionReceiver::class.java).apply {
                action = StockNotificationActionReceiver.ACTION_SILENCE
                putExtra(StockNotificationActionReceiver.EXTRA_SOURCE_ID, sourceId)
                putExtra(StockNotificationActionReceiver.EXTRA_NOTIF_ID,  notifId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Low stock: ${item.name}")
            .setContentText("${item.quantity} ${item.unit} left (min ${item.minQuantity})")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "${item.name} is running low — " +
                        "${item.quantity} ${item.unit} remaining " +
                        "(minimum threshold: ${item.minQuantity} ${item.unit}).\n" +
                        "Tap to restock or use the buttons below."
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(tapIntent)
            .setAutoCancel(false) // Stays visible so the user can act on it
            .addAction(R.drawable.ic_notification, "Snooze 1h", snoozeIntent)
            .addAction(R.drawable.ic_notification, "Silence",   silenceIntent)
            .build()

        nm().notify(notifId, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = nm()
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Low stock alerts",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Notifies when pantry items run low"
                enableVibration(false)
            }
        )
    }

    private fun nm(): NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    private fun osNotifId(sourceId: String): Int =
        sourceId.hashCode() and 0x7FFFFFFF
}
