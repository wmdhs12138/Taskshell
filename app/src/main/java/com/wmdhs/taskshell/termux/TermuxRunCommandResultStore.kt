package com.wmdhs.taskshell.termux

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Result delivered by Termux through RUN_COMMAND_PENDING_INTENT.
 *
 * Different Termux versions may use slightly different extra keys, so Taskshell keeps
 * both a flattened map and raw text for inspection.
 */
data class TermuxRunCommandCallbackResult(
    val requestId: String,
    val receivedAt: Instant,
    val resultCode: Int?,
    val extras: Map<String, String?>,
    val rawExtras: String
) {
    val stdout: String?
        get() = firstExtraValue(
            "stdout",
            "com.termux.RUN_COMMAND_STDOUT",
            "com.termux.app.RUN_COMMAND_STDOUT"
        )

    val stderr: String?
        get() = firstExtraValue(
            "stderr",
            "com.termux.RUN_COMMAND_STDERR",
            "com.termux.app.RUN_COMMAND_STDERR",
            "errmsg",
            "error"
        )

    val exitCode: Int?
        get() = firstExtraValue(
            "exitCode",
            "exit_code",
            "com.termux.RUN_COMMAND_EXIT_CODE",
            "com.termux.app.RUN_COMMAND_EXIT_CODE"
        )?.toIntOrNull()

    private fun firstExtraValue(vararg keys: String): String? {
        for (key in keys) {
            extras[key]?.let { return it }
        }
        return null
    }
}

object TermuxRunCommandResultStore {
    private val monitor = Object()
    private val results = ConcurrentHashMap<String, TermuxRunCommandCallbackResult>()

    fun put(result: TermuxRunCommandCallbackResult) {
        results[result.requestId] = result
        synchronized(monitor) {
            monitor.notifyAll()
        }
    }

    fun get(requestId: String?): TermuxRunCommandCallbackResult? {
        if (requestId.isNullOrBlank()) return null
        return results[requestId]
    }

    fun await(requestId: String?, timeoutMillis: Long): TermuxRunCommandCallbackResult? {
        if (requestId.isNullOrBlank()) return null
        get(requestId)?.let { return it }

        val deadline = System.currentTimeMillis() + timeoutMillis.coerceAtLeast(0)
        synchronized(monitor) {
            while (true) {
                get(requestId)?.let { return it }
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) return null
                monitor.wait(remaining)
            }
        }
    }

    fun latest(limit: Int = 20): List<TermuxRunCommandCallbackResult> {
        return results.values.sortedByDescending { it.receivedAt }.take(limit.coerceIn(1, 200))
    }
}
