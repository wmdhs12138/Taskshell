package com.wmdhs.taskshell.audit

import java.time.Instant
import java.util.UUID

data class AuditEvent(
    val id: String = newId(),
    val time: Instant = Instant.now(),
    val toolName: String,
    val command: String? = null,
    val cwd: String? = null,
    val taskId: String? = null,
    val success: Boolean,
    val error: String? = null,
    val durationMillis: Long,
    val resultSummary: String? = null,
    val source: String = "local-http"
)

object AuditLogStore {
    private val lock = Any()
    private val events = ArrayDeque<AuditEvent>()

    fun add(event: AuditEvent) {
        synchronized(lock) {
            events.addFirst(event)
            while (events.size > MAX_EVENTS) {
                events.removeLast()
            }
        }
    }

    fun list(limit: Int = DEFAULT_LIMIT): List<AuditEvent> {
        synchronized(lock) {
            return events.take(limit.coerceIn(1, MAX_EVENTS))
        }
    }

    fun clear() {
        synchronized(lock) {
            events.clear()
        }
    }

    private const val DEFAULT_LIMIT = 50
    private const val MAX_EVENTS = 300
}

private fun newId(): String = "audit_" + UUID.randomUUID().toString().replace("-", "").take(16)
