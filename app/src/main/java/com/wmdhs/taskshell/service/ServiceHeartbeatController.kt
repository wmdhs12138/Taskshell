package com.wmdhs.taskshell.service

import android.os.Handler
import android.os.Looper
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

object ServiceHeartbeatController {
    private val handler = Handler(Looper.getMainLooper())
    private val active = AtomicBoolean(false)
    private val runnable = object : Runnable {
        override fun run() {
            if (!TaskshellServiceState.running) {
                active.set(false)
                return
            }
            val idleMillis = System.currentTimeMillis() - lastActivityAtMillis
            if (idleMillis > IDLE_TIMEOUT_MS) {
                if (active.getAndSet(false)) {
                    ServiceEventLogger.record(
                        "heartbeat_suspend",
                        mapOf("idleMillis" to idleMillis.toString())
                    )
                }
                return
            }
            ServiceEventLogger.heartbeat()
            handler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
        }
    }

    @Volatile
    private var lastActivityAtMillis: Long = 0L

    @Volatile
    private var lastActivityAt: Instant? = null

    fun notifyActivity(reason: String) {
        lastActivityAtMillis = System.currentTimeMillis()
        lastActivityAt = Instant.now()
        if (active.compareAndSet(false, true)) {
            ServiceEventLogger.record("heartbeat_resume", mapOf("reason" to reason))
            handler.removeCallbacks(runnable)
            handler.post(runnable)
        }
    }

    fun stop() {
        handler.removeCallbacks(runnable)
        active.set(false)
    }

    fun diagnostics(): Map<String, Any?> = mapOf(
        "heartbeatActive" to active.get(),
        "lastActivityAt" to lastActivityAt?.toString(),
        "idleTimeoutMillis" to IDLE_TIMEOUT_MS,
        "heartbeatIntervalMillis" to HEARTBEAT_INTERVAL_MS
    )

    private const val HEARTBEAT_INTERVAL_MS = 5 * 60 * 1000L
    private const val IDLE_TIMEOUT_MS = 10 * 60 * 1000L
}
