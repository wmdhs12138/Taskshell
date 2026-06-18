package com.wmdhs.taskshell.task

import com.wmdhs.taskshell.security.TaskKind
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class TaskLimiterDecision(
    val allowed: Boolean,
    val reason: String? = null
)

class TaskLimiter {
    private val kinds = ConcurrentHashMap<String, TaskKind>()

    fun register(taskId: String, kind: TaskKind) {
        kinds[taskId] = kind
    }

    fun unregister(taskId: String) {
        kinds.remove(taskId)
    }

    fun canStart(tasks: Collection<ShellTask>, kind: TaskKind): TaskLimiterDecision {
        val runningTasks = tasks.filter { it.status == ShellTaskStatus.Queued || it.status == ShellTaskStatus.Running }
        if (runningTasks.size >= MAX_ACTIVE_TASKS) {
            return TaskLimiterDecision(false, "Too many active tasks. Max active tasks: $MAX_ACTIVE_TASKS")
        }

        val heavyCount = runningTasks.count { task -> kinds[task.taskId] == TaskKind.Heavy }
        if (kind == TaskKind.Heavy && heavyCount >= MAX_HEAVY_TASKS) {
            return TaskLimiterDecision(false, "Heavy task limit reached. Max heavy tasks: $MAX_HEAVY_TASKS")
        }

        if (tasks.size >= MAX_TOTAL_KNOWN_TASKS) {
            return TaskLimiterDecision(false, "Too many known tasks. Please cleanup old tasks first. Max known tasks: $MAX_TOTAL_KNOWN_TASKS")
        }

        return TaskLimiterDecision(true)
    }

    fun cleanupFinished(tasks: MutableMap<String, ShellTask>, now: Instant = Instant.now()) {
        val expiredIds = tasks.values
            .filter { task -> task.status == ShellTaskStatus.Finished || task.status == ShellTaskStatus.Failed || task.status == ShellTaskStatus.Stopped }
            .filter { task -> now.epochSecond - task.updatedAt.epochSecond > FINISHED_TASK_KEEP_SECONDS }
            .map { it.taskId }
        expiredIds.forEach { id ->
            tasks.remove(id)
            unregister(id)
        }
    }

    companion object {
        const val MAX_ACTIVE_TASKS = 3
        const val MAX_HEAVY_TASKS = 1
        const val MAX_TOTAL_KNOWN_TASKS = 50
        const val FINISHED_TASK_KEEP_SECONDS = 24 * 60 * 60
    }
}
