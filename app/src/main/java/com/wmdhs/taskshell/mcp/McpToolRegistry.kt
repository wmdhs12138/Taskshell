package com.wmdhs.taskshell.mcp

import com.wmdhs.taskshell.audit.AuditEvent
import com.wmdhs.taskshell.audit.AuditLogStore
import com.wmdhs.taskshell.service.ServiceEventLogger
import com.wmdhs.taskshell.task.ShellExecResult
import com.wmdhs.taskshell.task.ShellTask
import com.wmdhs.taskshell.task.ShellTaskInput
import com.wmdhs.taskshell.task.ShellTaskManager
import com.wmdhs.taskshell.task.preview
import com.wmdhs.taskshell.task.redactSensitiveText
import com.wmdhs.taskshell.task.sha256
import com.wmdhs.taskshell.task.toPublicSummary

class McpToolRegistry(
    private val taskManager: ShellTaskManager
) {
    private val tools = listOf(
        McpTool(
            name = "shell_exec",
            title = "执行 Termux 命令",
            description = "Run a shell command in Termux. Returns stdout/stderr/exitCode when it finishes within waitMillis; otherwise returns taskId with pollAfterMillis. Avoid frequent follow-up polling."
        ),
        McpTool(
            name = "shell_task_start",
            title = "启动后台任务",
            description = "Start a shell command as a background task. Supports optional waitMillis to wait briefly before returning. If still running, respect pollAfterMillis/recommendedNextCheckAt; do not poll status immediately unless the user asks."
        ),
        McpTool(
            name = "shell_task_status",
            title = "查询任务状态",
            description = "Get task status. Avoid frequent polling. Prefer waitMillis for long-polling, and respect pollAfterMillis/recommendedNextCheckAt returned by previous calls.",
            readOnlyHint = true
        ),
        McpTool(
            name = "shell_task_logs",
            title = "读取任务日志",
            description = "Read stdout and stderr from a background shell task. Avoid using this as a polling loop; respect pollAfterMillis while the task is running.",
            readOnlyHint = true
        ),
        McpTool(
            name = "shell_task_stop",
            title = "停止后台任务",
            description = "Stop a background shell task by taskId and return the final concise status."
        ),
        McpTool(
            name = "shell_task_list",
            title = "列出后台任务",
            description = "List known shell tasks with concise task summaries.",
            readOnlyHint = true
        ),
        McpTool(
            name = "shell_task_cleanup",
            title = "清理任务记录",
            description = "Cleanup finished task records and old Termux task directories. Intended for maintenance; dryRun defaults to true.",
            destructiveHint = true
        ),
        McpTool(
            name = "shell_task_recover",
            title = "恢复任务记录",
            description = "Recover known task records from Termux ~/.taskshell/tasks directories. Use after service restart or when a task is missing from app memory.",
            readOnlyHint = true
        ),
        McpTool(
            name = "shell_task_debug",
            title = "任务高级诊断",
            description = "Advanced diagnostics for a task, including Termux transport metadata and raw callback details. Use only when normal task tools fail.",
            readOnlyHint = true
        ),
        McpTool(
            name = "audit_logs",
            title = "查看审计日志",
            description = "List recent Taskshell audit events for troubleshooting.",
            readOnlyHint = true
        ),
        McpTool(
            name = "audit_clear",
            title = "清空审计日志",
            description = "Clear in-memory audit events.",
            destructiveHint = true,
            openWorldHint = false
        ),
        McpTool(
            name = "service_diagnostics",
            title = "服务诊断",
            description = "Advanced diagnostics for troubleshooting Taskshell service lifecycle and Termux transport. Use only when normal tools fail.",
            readOnlyHint = true,
            openWorldHint = false
        )
    )

    fun listTools(): List<McpTool> = tools

    fun callTool(name: String, arguments: Map<String, Any?>): Any {
        val startedAt = System.currentTimeMillis()
        return try {
            val result = when (name) {
                "shell_task_start" -> {
                    val waitMillis = (arguments["waitMillis"] as? Number)?.toLong() ?: 0L
                    if (waitMillis > 0L) {
                        taskManager.execPublic(
                            command = arguments["command"] as? String ?: error("Missing command"),
                            workingDirectory = arguments.workingDirectory(),
                            waitMillis = waitMillis,
                            input = arguments.taskInput()
                        )
                    } else {
                        taskManager.start(
                            command = arguments["command"] as? String ?: error("Missing command"),
                            workingDirectory = arguments.workingDirectory(),
                            input = arguments.taskInput()
                        ).toPublicSummary(includeCommand = arguments.includeCommand())
                    }
                }
                "shell_task_status" -> taskManager.statusPublic(
                    taskId = arguments["taskId"] as? String ?: error("Missing taskId"),
                    includeCommand = arguments.includeCommand(),
                    waitMillis = (arguments["waitMillis"] as? Number)?.toLong() ?: 0L
                )
                "shell_task_logs" -> taskManager.logsPublic(
                    taskId = arguments["taskId"] as? String ?: error("Missing taskId"),
                    maxLines = (arguments["maxLines"] as? Number)?.toInt() ?: 200,
                    maxBytes = (arguments["maxBytes"] as? Number)?.toInt() ?: 64 * 1024
                )
                "shell_task_stop" -> taskManager.stopPublic(
                    taskId = arguments["taskId"] as? String ?: error("Missing taskId")
                )
                "shell_task_list" -> taskManager.listPublic(includeCommand = arguments.includeCommand())
                "shell_task_cleanup" -> taskManager.cleanup(
                    olderThanHours = (arguments["olderThanHours"] as? Number)?.toInt() ?: 24,
                    keepLatest = (arguments["keepLatest"] as? Number)?.toInt() ?: 20,
                    dryRun = arguments["dryRun"] as? Boolean ?: true
                ).withDisplayPreview("清理任务记录", cleanupSummary(arguments))
                "shell_task_recover" -> taskManager.recover().withDisplayPreview("恢复任务记录", "扫描 Termux ~/.taskshell/tasks 任务目录")
                "shell_task_debug" -> taskManager.debug(
                    taskId = arguments["taskId"] as? String ?: error("Missing taskId")
                ).withDisplayPreview("任务高级诊断", "taskId=${arguments["taskId"]}")
                "shell_exec" -> taskManager.execPublic(
                    command = arguments["command"] as? String ?: error("Missing command"),
                    workingDirectory = arguments.workingDirectory(),
                    waitMillis = (arguments["waitMillis"] as? Number)?.toLong() ?: 10_000L,
                    input = arguments.taskInput()
                )
                "audit_logs" -> mapOf(
                    "displayTitle" to "查看审计日志",
                    "displaySummary" to "最近 ${(arguments["limit"] as? Number)?.toInt() ?: 50} 条审计事件",
                    "events" to AuditLogStore.list((arguments["limit"] as? Number)?.toInt() ?: 50)
                )
                "audit_clear" -> mapOf("displayTitle" to "清空审计日志", "displaySummary" to "清空内存中的审计事件", "cleared" to true).also { AuditLogStore.clear() }
                "service_diagnostics" -> mapOf(
                    "displayTitle" to "服务诊断",
                    "displaySummary" to "检查 Taskshell 服务生命周期与 Termux transport 状态",
                    "diagnostics" to ServiceEventLogger.diagnostics()
                )
                else -> error("Unknown tool: $name")
            }
            if (shouldAudit(name)) {
                AuditLogStore.add(
                    AuditEvent(
                        toolName = name,
                        command = null,
                        commandPreview = (arguments["command"] as? String)?.preview(),
                        commandLength = (arguments["command"] as? String)?.length,
                        commandSha256 = (arguments["command"] as? String)?.sha256(),
                        cwd = arguments.workingDirectory(),
                        taskId = extractTaskId(result),
                        success = true,
                        durationMillis = System.currentTimeMillis() - startedAt,
                        resultSummary = summarize(result).redactForAudit()
                    )
                )
            }
            result
        } catch (throwable: Throwable) {
            if (shouldAudit(name)) {
                AuditLogStore.add(
                    AuditEvent(
                        toolName = name,
                        command = null,
                        commandPreview = (arguments["command"] as? String)?.preview(),
                        commandLength = (arguments["command"] as? String)?.length,
                        commandSha256 = (arguments["command"] as? String)?.sha256(),
                        cwd = arguments.workingDirectory(),
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

    private fun Map<String, Any?>.withDisplayPreview(title: String, summary: String): Map<String, Any?> =
        linkedMapOf<String, Any?>(
            "displayTitle" to title,
            "displaySummary" to summary
        ).also { it.putAll(this) }

    private fun cleanupSummary(arguments: Map<String, Any?>): String {
        val dryRun = arguments["dryRun"] as? Boolean ?: true
        val olderThanHours = (arguments["olderThanHours"] as? Number)?.toInt() ?: 24
        val keepLatest = (arguments["keepLatest"] as? Number)?.toInt() ?: 20
        return "${if (dryRun) "预览清理" else "执行清理"}: olderThanHours=$olderThanHours, keepLatest=$keepLatest"
    }

    private fun Map<String, Any?>.includeCommand(): Boolean = this["includeCommand"] as? Boolean ?: false

    private fun String.redactForAudit(): String {
        return redactSensitiveText()
            .replace(Regex("command=([^,)]{0,500})"), "command=<redacted>")
            .replace(Regex("transportCommand=([^,)]{0,500})"), "transportCommand=<redacted>")
            .replace(Regex("command\":\"[^\"]{0,500}\""), "command\":\"<redacted>\"")
    }

    private fun Map<String, Any?>.taskInput(): ShellTaskInput {
        val rawEnv = this["env"] as? Map<*, *>
        val env = rawEnv
            ?.mapKeys { it.key.toString() }
            ?.mapValues { it.value?.toString().orEmpty() }
            ?: emptyMap()
        return ShellTaskInput(
            env = env,
            stdin = this["stdin"] as? String ?: this["input"] as? String,
            timeoutMillis = (this["timeoutMillis"] as? Number)?.toLong()
        )
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
    val title: String,
    val description: String,
    val readOnlyHint: Boolean = false,
    val destructiveHint: Boolean = false,
    val openWorldHint: Boolean = true
)
