package com.wmdhs.taskshell.task

import java.time.Instant

data class PublicTaskSummary(
    val taskId: String,
    val status: String,
    val command: String?,
    val cwd: String?,
    val createdAt: String,
    val updatedAt: String
)

data class PublicShellExecResult(
    val status: String,
    val taskId: String,
    val exitCode: Int? = null,
    val stdout: String? = null,
    val stderr: String? = null,
    val message: String? = null
)

data class PublicTaskStatusResult(
    val taskId: String,
    val status: String,
    val command: String?,
    val cwd: String?,
    val createdAt: String,
    val updatedAt: String,
    val exitCode: Int? = null,
    val message: String? = null
)

data class PublicTaskLogsResult(
    val taskId: String,
    val status: String,
    val stdout: String? = null,
    val stderr: String? = null,
    val exitCode: Int? = null,
    val message: String? = null
)

data class PublicTaskStopResult(
    val taskId: String,
    val status: String,
    val updatedAt: String
)

fun ShellTask.toPublicSummary(): PublicTaskSummary = PublicTaskSummary(
    taskId = taskId,
    status = status.publicName(),
    command = command,
    cwd = workingDirectory,
    createdAt = createdAt.toIsoString(),
    updatedAt = updatedAt.toIsoString()
)

fun ShellTaskStatus.publicName(): String = name.lowercase()

fun Instant.toIsoString(): String = toString()
