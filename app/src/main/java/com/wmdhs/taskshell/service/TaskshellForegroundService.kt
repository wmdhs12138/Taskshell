package com.wmdhs.taskshell.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.wmdhs.taskshell.MainActivity
import com.wmdhs.taskshell.R
import com.wmdhs.taskshell.mcp.McpServer

class TaskshellForegroundService : Service() {
    private val mcpServer by lazy { McpServer(applicationContext) }
    private val notificationHandler by lazy { Handler(Looper.getMainLooper()) }
    private val notificationRefreshRunnable = object : Runnable {
        override fun run() {
            if (TaskshellServiceState.running) {
                updateNotification("Local MCP server is active at 127.0.0.1:8765")
                notificationHandler.postDelayed(this, NOTIFICATION_REFRESH_INTERVAL_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        TaskshellServiceState.lastEvent = "Service created"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Starting local MCP server..."))
        return try {
            mcpServer.start()
            TaskshellServiceState.running = true
            TaskshellServiceState.lastError = null
            TaskshellServiceState.lastEvent = "MCP server started at ${McpServer.DEFAULT_ENDPOINT}"
            updateNotification("Local MCP server is active at 127.0.0.1:8765")
            scheduleNotificationRefresh()
            START_STICKY
        } catch (throwable: Throwable) {
            TaskshellServiceState.running = false
            TaskshellServiceState.lastError = throwable.message ?: throwable::class.java.name
            TaskshellServiceState.lastEvent = "Failed to start MCP server"
            updateNotification("Failed: ${TaskshellServiceState.lastError}")
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        notificationHandler.removeCallbacks(notificationRefreshRunnable)
        runCatching { mcpServer.stop() }
        TaskshellServiceState.running = false
        TaskshellServiceState.lastEvent = "Service destroyed"
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Taskshell Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the local MCP shell task service running."
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun scheduleNotificationRefresh() {
        notificationHandler.removeCallbacks(notificationRefreshRunnable)
        notificationHandler.postDelayed(notificationRefreshRunnable, NOTIFICATION_REFRESH_INTERVAL_MS)
    }

    private fun buildNotification(text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Taskshell")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setLocalOnly(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(true)
            .setAutoCancel(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setSilent(true)
            .build().apply {
                flags = flags or Notification.FLAG_ONGOING_EVENT or Notification.FLAG_NO_CLEAR
            }
    }

    companion object {
        private const val CHANNEL_ID = "taskshell_service"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_REFRESH_INTERVAL_MS = 30_000L
    }
}
