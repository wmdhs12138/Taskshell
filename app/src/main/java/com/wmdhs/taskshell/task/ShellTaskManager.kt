package com.wmdhs.taskshell.task

import com.wmdhs.taskshell.security.CommandPolicy
import com.wmdhs.taskshell.termux.TermuxCommandExecutor
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ShellTaskManager(
    private val executor: TermuxCommandExecutor
) {
    private val tasks = ConcurrentHashMap<String, ShellTask>()
    private val commandPolicy = CommandPolicy()
    private val taskLimiter = TaskLimiter()

    fun exec(command: String, workingDirectory: String?, waitMillis: Long): ShellExecResult {
        val task = start(command, workingDirectory)
        return waitForShortTask(task, waitMillis)
    }

    @Synchronized
    fun start(command: String, workingDirectory: String?): ShellTask {
        refreshActiveTasks()
        taskLimiter.cleanupFinished(tasks)
        val policy = commandPolicy.validate(command, workingDirectory)
        require(policy.allowed) { policy.reason ?: "Command blocked by safety policy" }
        val limit = taskLimiter.canStart(tasks.values, policy.taskKind)
        require(limit.allowed) { limit.reason ?: "Task limit reached" }

        val now = Instant.now()
        val taskId = newTaskId()
        val result = executor.startTask(taskId, command, workingDirectory)
        val callback = result.awaitCallback(START_CALLBACK_TIMEOUT_MS)
        val task = ShellTask(
            taskId = taskId,
            command = command,
            workingDirectory = workingDirectory,
            status = if (result.accepted) ShellTaskStatus.Queued else ShellTaskStatus.Failed,
            createdAt = now,
            updatedAt = Instant.now(),
            transportCommand = result.command,
            lastRequestId = result.requestId
        )
        tasks[taskId] = task
        if (task.status == ShellTaskStatus.Queued || task.status == ShellTaskStatus.Running) {
            taskLimiter.register(taskId, policy.taskKind)
        }
        return if (callback == null) task else task.copy(updatedAt = Instant.now()).also { tasks[taskId] = it }
    }

    fun status(taskId: String): ShellTask {
        requireSafeTaskId(taskId)
        return tasks[taskId]
            ?: error("Task not found in app memory: $taskId. If this task was created before service restart, call shell_task_recover explicitly; automatic recovery is intentionally skipped to avoid blocking status polling.")
    }

    fun statusDetails(taskId: String): Map<String, Any?> {
        val task = status(taskId)
        val result = executor.statusTask(taskId)
        val callback = if (result.accepted) result.awaitCallback(QUERY_CALLBACK_TIMEOUT_MS) else null
        val queryFailed = !result.accepted || callback == null
        val parsed = callback?.stdout?.parseKeyValueLines().orEmpty()
        val statusFromTermux = parsed["status"]
        val running = parsed["running"]?.toBooleanStrictOrNull()
        val exitCode = parsed["exitCode"]?.takeIf { it.isNotBlank() }?.toIntOrNull()
        val updated = if (queryFailed) {
            task.copy(lastRequestId = result.requestId)
        } else {
            task.copy(
                status = inferStatus(task.status, statusFromTermux, running, exitCode),
                updatedAt = Instant.now(),
                lastRequestId = result.requestId
            )
        }
        tasks[taskId] = updated
        if (updated.status.isTerminal()) {
            taskLimiter.unregister(taskId)
        }
        return mapOf(
            "task" to updated,
            "accepted" to result.accepted,
            "transport" to result.transport,
            "requestId" to result.requestId,
            "callbackReceived" to (callback != null),
            "queryFailed" to queryFailed,
            "stale" to queryFailed,
            "message" to if (queryFailed) staleStatusMessage(taskId) else null,
            "stdout" to callback?.stdout,
            "stderr" to callback?.stderr,
            "exitCode" to callback?.exitCode,
            "parsed" to parsed,
            "rawExtras" to callback?.rawExtras,
            "error" to (result.error ?: if (queryFailed) "Status query did not return a callback before timeout" else null)
        )
    }

    fun logs(taskId: String, maxLines: Int): Map<String, Any?> {
        val task = status(taskId)
        val result = executor.logsTask(taskId, maxLines)
        val callback = if (result.accepted) result.awaitCallback(QUERY_CALLBACK_TIMEOUT_MS) else null
        val queryFailed = !result.accepted || callback == null
        val parsed = callback?.stdout?.parseKeyValueLines().orEmpty()
        val statusFromTermux = parsed["status"]
        val exitCodeFromTermux = parsed["exitCode"]?.takeIf { it.isNotBlank() }?.toIntOrNull()
        val updated = if (queryFailed) {
            task.copy(lastRequestId = result.requestId)
        } else {
            task.copy(
                status = inferStatus(task.status, statusFromTermux, null, exitCodeFromTermux),
                updatedAt = Instant.now(),
                lastRequestId = result.requestId
            )
        }
        tasks[taskId] = updated
        if (updated.status.isTerminal()) {
            taskLimiter.unregister(taskId)
        }
        return mapOf(
            "taskId" to task.taskId,
            "status" to updated.status.name,
            "maxLines" to maxLines.coerceIn(1, 5000),
            "accepted" to result.accepted,
            "transport" to result.transport,
            "requestId" to result.requestId,
            "callbackReceived" to (callback != null),
            "queryFailed" to queryFailed,
            "stale" to queryFailed,
            "message" to if (queryFailed) staleStatusMessage(taskId) else null,
            "stdout" to callback?.stdout,
            "stderr" to callback?.stderr,
            "exitCode" to (callback?.exitCode ?: exitCodeFromTermux),
            "parsed" to parsed,
            "rawExtras" to callback?.rawExtras,
            "command" to result.command,
            "error" to (result.error ?: if (queryFailed) "Log query did not return a callback before timeout" else null)
        )
    }

    fun stop(taskId: String): ShellTask {
        val current = status(taskId)
        val result = executor.stopTask(taskId)
        result.awaitCallback(QUERY_CALLBACK_TIMEOUT_MS)
        val stopped = current.copy(
            status = ShellTaskStatus.Stopped,
            updatedAt = Instant.now(),
            lastRequestId = result.requestId
        )
        tasks[taskId] = stopped
        taskLimiter.unregister(taskId)
        return stopped
    }

    fun recover(): Map<String, Any?> {
        val result = executor.recoverTasks()
        val callback = if (result.accepted) result.awaitCallback(RECOVER_CALLBACK_TIMEOUT_MS) else null
        val recoverStdout = callback?.stdout ?: callback?.rawExtras?.extractEmbeddedStdout()
        val recovered = recoverStdout?.let { parseRecoveredTasks(it) }.orEmpty()
        recovered.forEach { task ->
            tasks[task.taskId] = task
        }
        return mapOf(
            "accepted" to result.accepted,
            "requestId" to result.requestId,
            "callbackReceived" to (callback != null),
            "recoveredCount" to recovered.size,
            "tasks" to recovered.sortedByDescending { it.updatedAt },
            "stdout" to recoverStdout,
            "stderr" to callback?.stderr,
            "error" to result.error
        )
    }

    fun cleanup(olderThanHours: Int, keepLatest: Int, dryRun: Boolean): Map<String, Any?> {
        taskLimiter.cleanupFinished(tasks)
        val before = tasks.size
        val cutoffSeconds = Instant.now().epochSecond - olderThanHours.coerceIn(1, 24 * 365) * 3600L
        val ended = tasks.values
            .filter { it.status == ShellTaskStatus.Finished || it.status == ShellTaskStatus.Failed || it.status == ShellTaskStatus.Stopped }
            .sortedByDescending { it.updatedAt }
        val keepIds = ended.take(keepLatest.coerceIn(0, 500)).map { it.taskId }.toSet()
        val removable = ended.filter { it.taskId !in keepIds && it.updatedAt.epochSecond < cutoffSeconds }
        if (!dryRun) {
            removable.forEach { task ->
                tasks.remove(task.taskId)
                taskLimiter.unregister(task.taskId)
            }
        }

        val result = executor.cleanupTasks(
            olderThanHours = olderThanHours,
            keepLatest = keepLatest,
            dryRun = dryRun
        )
        val callback = result.awaitCallback(QUERY_CALLBACK_TIMEOUT_MS)
        return mapOf(
            "dryRun" to dryRun,
            "olderThanHours" to olderThanHours.coerceIn(1, 24 * 365),
            "keepLatest" to keepLatest.coerceIn(0, 500),
            "appTasksBefore" to before,
            "appTasksRemoved" to if (dryRun) 0 else removable.size,
            "appTasksWouldRemove" to removable.map { it.taskId },
            "appTasksAfter" to tasks.size,
            "accepted" to result.accepted,
            "requestId" to result.requestId,
            "callbackReceived" to (callback != null),
            "stdout" to callback?.stdout,
            "stderr" to callback?.stderr,
            "exitCode" to callback?.exitCode,
            "rawExtras" to callback?.rawExtras,
            "error" to result.error
        )
    }

    fun list(): List<ShellTask> {
        taskLimiter.cleanupFinished(tasks)
        return tasks.values.sortedByDescending { it.createdAt }
    }

    private fun waitForShortTask(task: ShellTask, waitMillis: Long): ShellExecResult {
        val timeout = waitMillis.coerceIn(0L, MAX_EXEC_WAIT_TIMEOUT_MS)
        if (timeout == 0L || task.status == ShellTaskStatus.Failed) {
            return ShellExecResult(mode = "background", task = task)
        }

        val deadline = System.currentTimeMillis() + timeout
        var latest = task
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(EXEC_POLL_INTERVAL_MS.coerceAtMost((deadline - System.currentTimeMillis()).coerceAtLeast(1L)))
            val details = runCatching { statusDetails(task.taskId) }.getOrNull() ?: break
            latest = details["task"] as? ShellTask ?: latest
            if (latest.status == ShellTaskStatus.Finished || latest.status == ShellTaskStatus.Failed || latest.status == ShellTaskStatus.Stopped) {
                val logDetails = runCatching { logs(task.taskId, MAX_EXEC_INLINE_LOG_LINES) }.getOrNull()
                val stdout = logDetails?.get("stdout") as? String
                val stderr = logDetails?.get("stderr") as? String
                val exitCode = logDetails?.get("exitCode") as? Int
                return ShellExecResult(
                    mode = "foreground",
                    task = latest,
                    stdout = stdout?.substringAfter("--- stdout ---\n")?.substringBefore("--- stderr ---")?.trimEnd(),
                    stderr = stderr,
                    exitCode = exitCode
                )
            }
        }
        return ShellExecResult(mode = "background", task = latest)
    }

    private fun refreshActiveTasks() {
        val activeTasks = tasks.values.filter { it.status == ShellTaskStatus.Queued || it.status == ShellTaskStatus.Running }
        activeTasks.forEach { task ->
            runCatching {
                val result = executor.statusTask(task.taskId)
                val callback = if (result.accepted) result.awaitCallback(REFRESH_CALLBACK_TIMEOUT_MS) else null
                if (callback == null) {
                    tasks[task.taskId] = task.copy(lastRequestId = result.requestId)
                    return@runCatching
                }
                val parsed = callback.stdout?.parseKeyValueLines().orEmpty()
                val updatedStatus = inferStatus(
                    current = task.status,
                    termuxStatus = parsed["status"],
                    running = parsed["running"]?.toBooleanStrictOrNull(),
                    exitCode = parsed["exitCode"]?.takeIf { it.isNotBlank() }?.toIntOrNull()
                )
                val updated = task.copy(
                    status = updatedStatus,
                    updatedAt = Instant.now(),
                    lastRequestId = result.requestId
                )
                tasks[task.taskId] = updated
                if (updated.status.isTerminal()) {
                    taskLimiter.unregister(task.taskId)
                }
            }
        }
    }

    private fun staleStatusMessage(taskId: String): String =
        "Task status/log query failed; cached status may be stale. In Termux, check: cat ~/.taskshell/tasks/$taskId/status && tail -100 ~/.taskshell/tasks/$taskId/stdout.log"

    private fun requireSafeTaskId(taskId: String): String {
        require(Regex("^[a-zA-Z0-9_.-]{1,80}$").matches(taskId)) { "Invalid taskId" }
        return taskId
    }

    private fun ShellTaskStatus.isTerminal(): Boolean =
        this == ShellTaskStatus.Finished || this == ShellTaskStatus.Failed || this == ShellTaskStatus.Stopped

    private fun String.extractEmbeddedStdout(): String? {
        // Fallback for Termux versions that return a nested Bundle only as a flattened raw string.
        // rawExtras may contain: result={exitCode=0, ..., stdout=line1\nline2, stderr=...}
        val marker = "stdout="
        val start = indexOf(marker)
        if (start < 0) return null
        val after = substring(start + marker.length)
        val endCandidates = listOf(
            after.indexOf(", stderr="),
            after.indexOf(", stderr_original_length="),
            after.indexOf(", err="),
            after.indexOf("}\n")
        ).filter { it >= 0 }
        val raw = if (endCandidates.isEmpty()) after else after.substring(0, endCandidates.minOrNull() ?: after.length)
        return raw
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .takeIf { it.isNotBlank() }
    }

    private fun parseRecoveredTasks(stdout: String): List<ShellTask> {
        val now = Instant.now()
        return stdout.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val fields = line.split('	').ifEmpty { line.split(' ') }
                    .flatMap { segment -> segment.split(' ') }
                    .mapNotNull { part ->
                        val idx = part.indexOf('=')
                        if (idx <= 0) null else part.substring(0, idx) to part.substring(idx + 1)
                    }
                    .toMap()
                val taskId = fields["taskId"] ?: return@mapNotNull null
                val exitCode = fields["exitCode"]?.takeIf { it.isNotBlank() }?.toIntOrNull()
                val running = fields["running"]?.toBooleanStrictOrNull()
                val status = inferStatus(ShellTaskStatus.Queued, fields["status"], running, exitCode)
                val startedAt = fields["startedAt"]?.toLongOrNull()?.let { Instant.ofEpochSecond(it) } ?: now
                val endedAt = fields["endedAt"]?.toLongOrNull()?.let { Instant.ofEpochSecond(it) }
                ShellTask(
                    taskId = taskId,
                    command = "<recovered from Termux task directory>",
                    workingDirectory = null,
                    status = status,
                    createdAt = startedAt,
                    updatedAt = endedAt ?: now,
                    transportCommand = null,
                    lastRequestId = null
                )
            }
            .toList()
    }

    private fun inferStatus(
        current: ShellTaskStatus,
        termuxStatus: String?,
        running: Boolean?,
        exitCode: Int?
    ): ShellTaskStatus {
        return when {
            termuxStatus.equals("stopped", ignoreCase = true) -> ShellTaskStatus.Stopped
            termuxStatus.equals("running", ignoreCase = true) -> ShellTaskStatus.Running
            termuxStatus.equals("queued", ignoreCase = true) -> ShellTaskStatus.Queued
            termuxStatus.equals("finished", ignoreCase = true) -> ShellTaskStatus.Finished
            termuxStatus.equals("failed", ignoreCase = true) -> ShellTaskStatus.Failed
            running == true -> ShellTaskStatus.Running
            exitCode == 0 -> ShellTaskStatus.Finished
            exitCode != null -> ShellTaskStatus.Failed
            else -> current
        }
    }

    private fun String.parseKeyValueLines(): Map<String, String> {
        return lineSequence()
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
            }
            .toMap()
    }

    private fun newTaskId(): String = "taskshell_" + UUID.randomUUID().toString().replace("-", "").take(16)

    companion object {
        private const val START_CALLBACK_TIMEOUT_MS = 800L
        private const val QUERY_CALLBACK_TIMEOUT_MS = 2_000L
        private const val RECOVER_CALLBACK_TIMEOUT_MS = 8_000L
        private const val REFRESH_CALLBACK_TIMEOUT_MS = 500L
        private const val MAX_EXEC_WAIT_TIMEOUT_MS = 30_000L
        private const val EXEC_POLL_INTERVAL_MS = 250L
        private const val MAX_EXEC_INLINE_LOG_LINES = 500
    }
}
