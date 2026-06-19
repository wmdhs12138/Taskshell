package com.wmdhs.taskshell.service

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
import com.wmdhs.taskshell.MainActivity
import com.wmdhs.taskshell.R
import com.wmdhs.taskshell.mcp.McpServer

class TaskshellForegroundService : Service() {
    private val mcpServer by lazy { McpServer(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        ServiceEventLogger.init(applicationContext)
        ServiceEventLogger.record("service_create")
        createNotificationChannel()
        TaskshellServiceState.lastEvent = "Service created"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceEventLogger.record("service_start_command", mapOf("startId" to startId.toString(), "flags" to flags.toString()))
        startForeground(NOTIFICATION_ID, buildNotification("Starting local MCP server..."))
        return try {
            mcpServer.start()
            TaskshellServiceState.running = true
            TaskshellServiceState.lastError = null
            TaskshellServiceState.lastEvent = "MCP server started at ${McpServer.DEFAULT_ENDPOINT}"
            ServiceEventLogger.record("mcp_server_start", mapOf("endpoint" to McpServer.DEFAULT_ENDPOINT))
            updateNotification("Local MCP server is active at 127.0.0.1:8765")
            START_STICKY
        } catch (throwable: Throwable) {
            TaskshellServiceState.running = false
            TaskshellServiceState.lastError = throwable.message ?: throwable::class.java.name
            TaskshellServiceState.lastEvent = "Failed to start MCP server"
            ServiceEventLogger.record("mcp_server_start_failed", mapOf("error" to (TaskshellServiceState.lastError ?: throwable::class.java.name)))
            updateNotification("Failed: ${TaskshellServiceState.lastError}")
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        ServiceHeartbeatController.stop()
        ServiceEventLogger.record("service_destroy_begin")
        runCatching { mcpServer.stop() }
            .onSuccess { ServiceEventLogger.record("mcp_server_stop") }
            .onFailure { ServiceEventLogger.record("mcp_server_stop_failed", mapOf("error" to (it.message ?: it::class.java.name))) }
        TaskshellServiceState.running = false
        TaskshellServiceState.lastEvent = "Service destroyed"
        ServiceEventLogger.record("service_destroy")
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
    }
}
