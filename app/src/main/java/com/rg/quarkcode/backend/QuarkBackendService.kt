package com.rg.quarkcode.backend

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeper for the on-device opencode server. The process itself is owned by
 * the [LocalBackend] singleton (so a future terminal shell can attach to the
 * same handle); this service only keeps the app alive in the background and
 * gives the user a visible Stop handle.
 */
class QuarkBackendService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            LocalBackend.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat(buildNotification("Starting on-device backend…"))
        scope.launch {
            val ok = LocalBackend.start(applicationContext)
            if (ok) {
                updateNotification("On-device backend running · 127.0.0.1:${LocalBackend.PORT}")
            } else {
                val err = (LocalBackend.state.value as? LocalBackend.State.Stopped)?.error
                    ?: "Could not start backend"
                updateNotification(err)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        LocalBackend.stop()
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            runCatching {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "On-device backend",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        }
        val stopIntent = Intent(this, QuarkBackendService::class.java).apply { action = ACTION_STOP }
        val stopPending = android.app.PendingIntent.getService(
            this, 0, stopIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Quark Code backend")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPending)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.rg.quarkcode.backend.STOP"
        private const val CHANNEL_ID = "quark_backend"
        private const val NOTIFICATION_ID = 41

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, QuarkBackendService::class.java))
        }

        fun stop(context: Context) {
            val intent = Intent(context, QuarkBackendService::class.java).apply { action = ACTION_STOP }
            runCatching { context.startService(intent) }
        }
    }
}
