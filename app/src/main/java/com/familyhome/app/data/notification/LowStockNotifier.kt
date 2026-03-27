package com.familyhome.app.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.familyhome.app.R
import com.familyhome.app.domain.model.AppNotification
import com.familyhome.app.domain.model.NotificationType
import com.familyhome.app.domain.model.StockItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LowStockNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationCenter: NotificationCenter,
) {
    companion object {
        const val CHANNEL_ID = "low_stock_alerts"
    }

    fun notifyIfLow(item: StockItem) {
        if (!item.isLowStock) return

        notificationCenter.post(AppNotification(
            type     = NotificationType.LOW_STOCK,
            title    = "Low stock: ${item.name}",
            message  = "${item.quantity} ${item.unit} remaining (threshold: ${item.minQuantity})",
            sourceId = "low_stock_${item.id}",
        ))

        ensureChannel()
        val nm = context.getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Low stock: ${item.name}")
            .setContentText("Only ${item.quantity} ${item.unit} remaining")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        nm.notify(("low_stock_${item.id}").hashCode() and 0x7FFFFFFF, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(NotificationChannel(
                    CHANNEL_ID,
                    "Low stock alerts",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "Notifies when pantry items run low" })
            }
        }
    }
}
