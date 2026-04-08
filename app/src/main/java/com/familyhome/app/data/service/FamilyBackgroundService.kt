package com.familyhome.app.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.familyhome.app.MainActivity
import com.familyhome.app.R
import com.familyhome.app.data.onboarding.NsdHelper
import com.familyhome.app.data.sync.MemberNotifyServer
import com.familyhome.app.data.sync.MemberPresenceTracker
import com.familyhome.app.data.sync.SyncRepositoryImpl
import com.familyhome.app.domain.model.Role
import com.familyhome.app.domain.repository.SessionRepository
import com.familyhome.app.domain.repository.UserRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FamilyBackgroundService : Service() {

    companion object {
        private const val SERVICE_NOTIFICATION_ID = 2001
        const val CHANNEL_ID = "family_background_service"
        private const val AUTO_SYNC_INTERVAL_MS = 5 * 60 * 1000L

        fun start(context: Context) {
            val intent = Intent(context, FamilyBackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    @Inject lateinit var sessionRepository: SessionRepository
    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var syncRepository: SyncRepositoryImpl
    @Inject lateinit var nsdHelper: NsdHelper
    @Inject lateinit var presenceTracker: MemberPresenceTracker
    @Inject lateinit var memberNotifyServer: MemberNotifyServer

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var networkingJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(SERVICE_NOTIFICATION_ID, buildNotification())
        observeSession()
    }

    private fun observeSession() {
        serviceScope.launch {
            sessionRepository.currentUserIdFlow.collect { userId ->
                networkingJob?.cancel()
                networkingJob = null

                if (userId == null) {
                    syncRepository.stopHostServer()
                    nsdHelper.stopAdvertising()
                    return@collect
                }

                val user = userRepository.getUserById(userId) ?: return@collect
                networkingJob = launch {
                    when (user.role) {
                        Role.FATHER -> {
                            syncRepository.startHostServer()
                            nsdHelper.startAdvertising(
                                serviceName = "FamilyHome_Host",
                                port        = 8765,
                                serviceType = NsdHelper.FATHER_SERVICE_TYPE,
                            )
                            // Browse for member devices — update online status in real time
                            nsdHelper.startMemberBrowsing()
                            launch {
                                nsdHelper.discoveredMembers.collect { members ->
                                    val onlineUserIds = members.mapNotNull { device ->
                                        // Service name convention: "FamilyHome_Member_{userId}"
                                        device.serviceName
                                            .removePrefix("FamilyHome_Member_")
                                            .takeIf { it.isNotEmpty() && !it.startsWith("FamilyHome") }
                                    }.toSet()
                                    presenceTracker.setNetworkOnlineUsers(onlineUserIds)
                                }
                            }
                        }
                        else -> {
                            // Start member notify server so the leader can push directly
                            memberNotifyServer.start()
                            // Advertise this member on NSD so the leader can discover our IP
                            nsdHelper.startAdvertising(
                                serviceName = "FamilyHome_Member_${user.id}",
                                port        = MemberNotifyServer.PORT,
                                serviceType = NsdHelper.MEMBER_SERVICE_TYPE,
                            )
                            // Also mark the leader as online if reachable (via sync ping)
                            while (isActive) {
                                syncRepository.syncWithHost()
                                delay(AUTO_SYNC_INTERVAL_MS)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        syncRepository.stopHostServer()
        memberNotifyServer.stop()
        nsdHelper.stopAll()
        presenceTracker.setNetworkOnlineUsers(emptySet())
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("FamilyHome")
            .setContentText("Connected and syncing in background")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(NotificationChannel(
                    CHANNEL_ID,
                    "Background sync",
                    NotificationManager.IMPORTANCE_MIN,
                ).apply {
                    description = "Keeps FamilyHome connected and syncing in the background"
                    setShowBadge(false)
                })
            }
        }
    }
}
