package com.wmdhs.taskshell.service

import android.content.Context
import android.os.Process
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

object ServiceEventLogger {
    private val initialized = AtomicBoolean(false)
    private val lock = Any()
    private lateinit var logFile: File

    @Volatile
    var processStartedAt: Instant? = null
        private set

    @Volatile
    var lastHeartbeatAt: Instant? = null
        private set

    @Volatile
    var possiblePreviousProcessKill: Boolean = false
        private set

    fun init(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        synchronized(lock) {
            val dir = File(context.filesDir, "taskshell")
            dir.mkdirs()
            logFile = File(dir, "service-events.log")
            val previousEvent = readLastEventNameLocked()
            possiblePreviousProcessKill = previousEvent != null && previousEvent !in GRACEFUL_EVENTS
            processStartedAt = Instant.now()
            appendLocked(
                event = "process_start",
                fields = mapOf(
                    "pid" to Process.myPid().toString(),
                    "previousEvent" to previousEvent.orEmpty(),
                    "possiblePreviousProcessKill" to possiblePreviousProcessKill.toString()
                )
            )
        }
    }

    fun record(event: String, fields: Map<String, String> = emptyMap()) {
        if (!initialized.get()) return
        synchronized(lock) {
            appendLocked(event, fields + ("pid" to Process.myPid().toString()))
        }
    }

    fun heartbeat() {
        lastHeartbeatAt = Instant.now()
        record("heartbeat", mapOf("running" to TaskshellServiceState.running.toString()))
    }

    fun tail(limit: Int = 80): List<String> {
        if (!initialized.get()) return emptyList()
        synchronized(lock) {
            return runCatching {
                logFile.readLines().takeLast(limit.coerceIn(1, 500))
            }.getOrDefault(emptyList())
        }
    }

    fun diagnostics(): Map<String, Any?> {
        return mapOf(
            "pid" to Process.myPid(),
            "processStartedAt" to processStartedAt?.toString(),
            "lastHeartbeatAt" to lastHeartbeatAt?.toString(),
            "possiblePreviousProcessKill" to possiblePreviousProcessKill,
            "eventLogPath" to if (initialized.get()) logFile.absolutePath else null,
            "heartbeat" to ServiceHeartbeatController.diagnostics(),
            "lastEvents" to tail(20)
        )
    }

    private fun appendLocked(event: String, fields: Map<String, String>) {
        rotateIfNeededLocked()
        val line = buildString {
            append("time=").append(Instant.now())
            append(" event=").append(sanitize(event))
            fields.toSortedMap().forEach { (key, value) ->
                append(' ')
                append(sanitize(key)).append('=').append(sanitize(value))
            }
        }
        logFile.appendText(line + "\n")
    }

    private fun rotateIfNeededLocked() {
        if (!::logFile.isInitialized || !logFile.exists() || logFile.length() <= MAX_LOG_BYTES) return
        val kept = logFile.readLines().takeLast(KEEP_LINES_AFTER_ROTATE)
        logFile.writeText(kept.joinToString(separator = "\n", postfix = "\n"))
    }

    private fun readLastEventNameLocked(): String? {
        if (!::logFile.isInitialized || !logFile.exists()) return null
        return logFile.useLines { lines ->
            lines.filter { it.isNotBlank() }.lastOrNull()
        }?.split(' ')
            ?.firstOrNull { it.startsWith("event=") }
            ?.substringAfter("event=")
            ?.takeIf { it.isNotBlank() }
    }

    private fun sanitize(value: String): String = value
        .replace('\n', '_')
        .replace('\r', '_')
        .replace('\t', '_')
        .replace(' ', '_')
        .take(500)

    private val GRACEFUL_EVENTS = setOf(
        "service_destroy",
        "boot_completed"
    )
    private const val MAX_LOG_BYTES = 256 * 1024L
    private const val KEEP_LINES_AFTER_ROTATE = 500
}
