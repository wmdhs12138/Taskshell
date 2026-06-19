package com.wmdhs.taskshell.mcp

import com.wmdhs.taskshell.audit.AuditEvent
import com.wmdhs.taskshell.audit.AuditLogStore
import com.wmdhs.taskshell.service.ServiceEventLogger
import com.wmdhs.taskshell.task.ShellExecResult
import com.wmdhs.taskshell.task.ShellTask
import com.wmdhs.taskshell.task.ShellTaskManager
import com.wmdhs.taskshell.task.toPublicSummary

class McpToolRegistry(
    private val taskManager: ShellTaskManager
) {
    private val tools = listOf(
        McpTool(
            name = "shell_exec",
            description = "Run a shell command in Termux. Returns concise stdout, stderr, exit code, and taskId; if the command keeps running, returns the taskId for follow-up."
        ),
        McpTool(
            name = "shell_task_start",
            description = "Start a shell command as a background task. Returns a concise task summary for follow-up with shell_task_status or shell_task_logs."
        ),
        McpTool(
            name = "shell_task_status",
            description = "Get the current status of a background shell task by taskId. Returns a concise status, timestamps, and exit code when available."
        ),
        McpTool(
            name = "shell_task_logs",
            description = "Read stdout and stderr from a background shell task by taskId."
        ),
        McpTool(
            name = "shell_task_stop",
            description = "Stop a background shell task by taskId and return the final concise status."
        ),
        McpTool(
            name = "shell_task_list",
            description = "List known shell tasks with concise task summaries."
        ),
        McpTool(
            name = "shell_task_cleanup",
            description = "Cleanup finished task records and old Termux task directories. Intended for maintenance; dryRun defaults to true."
        ),
        McpTool(
            name = "shell_task_recover",
            description = "Recover known task records from Termux ~/.taskshell/tasks directories. Use after service restart or when a task is missing from app memory."
        ),
        McpTool(
            name = "shell_task_debug",
            description = "Advanced diagnostics for a task, including Termux transport metadata and raw callback details. Use only when normal task tools fail."
        ),
        McpTool(
            name = "audit_logs",
            description = "List recent Taskshell audit events for troubleshooting."
        ),
        McpTool(
            name = "audit_clear",
            description = "Clear in-memory audit events."
        ),
        McpTool(
            name = "service_diagnostics",
            description = "Advanced diagnostics for troubleshooting Taskshell service lifecycle and Termux transport. Use only when normal tools fail."
        )
    )

    fun listTools(): List<McpTool> = tools

    fun callTool(name: String, arguments: Map<String, Any?>): Any {
        val startedAt = System.currentTimeMillis()
        return try {
            val result = when (name) {
                "shell_task_start" -> taskManager.start(
                    command = arguments["command"] as? String ?: error("Missing command"),
                    workingDirectory = arguments.workingDirectory()
                ).toPublicSummary()
                "shell_task_status" -> taskManager.statusPublic(
                    taskId = arguments["taskId"] as? String ?: error("Missing taskId")
                )
                "shell_task_logs" -> taskManager.logsPublic(
                    taskId = arguments["taskId"] as? String ?: error("Missing taskId"),
                    maxLines = (arguments["maxLines"] as? Number)?.toInt() ?: 200
                )
                "shell_task_stop" -> taskManager.stopPublic(
                    taskId = arguments["taskId"] as? String ?: error("Missing taskId")
                )
                "shell_task_list" -> taskManager.listPublic()
                "shell_task_cleanup" -> taskManager.cleanup(
                    olderThanHours = (arguments["olderThanHours"] as? Number)?.toInt() ?: 24,
                    keepLatest = (arguments["keepLatest"] as? Number)?.toInt() ?: 20,
                    dryRun = arguments["dryRun"] as? Boolean ?: true
                )
                "shell_task_recover" -> taskManager.recover()
                "shell_task_debug" -> taskManager.debug(
                    taskId = arguments["taskId"] as? String ?: error("Missing taskId")
                )
                "shell_exec" -> taskManager.execPublic(
                    command = arguments["command"] as? String ?: error("Missing command"),
                    workingDirectory = arguments.workingDirectory(),
                    waitMillis = (arguments["waitMillis"] as? Number)?.toLong() ?: 10_000L
                )
                "audit_logs" -> AuditLogStore.list((arguments["limit"] as? Number)?.toInt() ?: 50)
                "audit_clear" -> mapOf("cleared" to true).also { AuditLogStore.clear() }
                "service_diagnostics" -> ServiceEventLogger.diagnostics()
                else -> error("Unknown tool: $name")
            }
            if (shouldAudit(name)) {
                AuditLogStore.add(
                    AuditEvent(
                        toolName = name,
                        command = arguments["command"] as? String,
                        cwd = arguments["cwd"] as? String,
                        taskId = extractTaskId(result),
                        success = true,
                        durationMillis = System.currentTimeMillis() - startedAt,
                        resultSummary = summarize(result)
                    )
                )
            }
            result
        } catch (throwable: Throwable) {
            if (shouldAudit(name)) {
                AuditLogStore.add(
                    AuditEvent(
                        toolName = name,
                        command = arguments["command"] as? String,
                        cwd = arguments["cwd"] as? String,
                        taskId = arguments["taskId"] as? String,
                        success = false,
                        error = throwable.message ?: throwable::class.java.simpleName,
                        durationMillis = System.currentTimeMillis() - startedAt
                    )
                )
            }
            throw throwable
        }
    }

    private fun shouldAudit(name: String): Boolean = name !in setOf("audit_logs")

    private fun extractTaskId(result: Any): String? {
        return when (result) {
            is ShellTask -> result.taskId
            is ShellExecResult -> result.task.taskId
            is Map<*, *> -> {
                (result["taskId"] as? String)
                    ?: (result["task"] as? ShellTask)?.taskId
                    ?: ((result["task"] as? Map<*, *>)?.get("taskId") as? String)
            }
            else -> runCatching {
                val field = result::class.java.declaredFields.firstOrNull { it.name == "taskId" } ?: return@runCatching null
                field.isAccessible = true
                field.get(result) as? String
            }.getOrNull()
        }
    }

    private fun summarize(result: Any): String {
        return result.toString().take(500)
    }

    private fun Map<String, Any?>.workingDirectory(): String? {
        return (this["cwd"] as? String)
            ?: (this["workingDirectory"] as? String)
            ?: (this["workdir"] as? String)
            ?: (this["working_dir"] as? String)
    }
}

data class McpTool(
    val name: String,
    val description: String
)
