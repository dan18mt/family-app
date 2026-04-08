package com.familyhome.app.data.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Handles inline action buttons on low-stock OS notifications:
 *  - "Snooze 1h"  ([ACTION_SNOOZE_1H]) — snoozes the alert for one hour
 *  - "Silence"    ([ACTION_SILENCE])   — silences the alert indefinitely
 *
 * Both actions cancel the OS notification immediately so the user's tray
 * stays clean, and update [NotificationCenter] state so the snooze / silence
 * is respected on future syncs and survives app restarts.
 */
class StockNotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SNOOZE_1H = "com.familyhome.app.STOCK_SNOOZE_1H"
        const val ACTION_SILENCE   = "com.familyhome.app.STOCK_SILENCE"
        const val EXTRA_SOURCE_ID  = "source_id"
        const val EXTRA_NOTIF_ID   = "notif_id"
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReceiverEntryPoint {
        fun notificationCenter(): NotificationCenter
        fun lowStockNotifier(): LowStockNotifier
    }

    override fun onReceive(context: Context, intent: Intent) {
        val sourceId = intent.getStringExtra(EXTRA_SOURCE_ID) ?: return

        val ep = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ReceiverEntryPoint::class.java,
        )
        val center   = ep.notificationCenter()
        val notifier = ep.lowStockNotifier()

        when (intent.action) {
            ACTION_SNOOZE_1H ->
                center.snoozeBySourceId(
                    sourceId,
                    System.currentTimeMillis() + 60 * 60 * 1_000L,
                )
            ACTION_SILENCE ->
                center.silenceBySourceId(sourceId)
        }

        // Remove from OS tray and from the notifier's active-tracking set
        notifier.cancelOsNotification(sourceId)
    }
}
