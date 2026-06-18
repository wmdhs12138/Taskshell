package com.wmdhs.taskshell.task

import java.time.Instant

data class ShellTask(
    val taskId: String,
    val command: String,
    val workingDirectory: String?,
    val status: ShellTaskStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val transportCommand: String? = null,
    val lastRequestId: String? = null
)

enum class ShellTaskStatus {
    Queued,
    Running,
    Finished,
    Failed,
    Stopped
}

data class ShellExecResult(
    val mode: String,
    val task: ShellTask,
    val stdout: String? = null,
    val stderr: String? = null,
    val exitCode: Int? = null
)
