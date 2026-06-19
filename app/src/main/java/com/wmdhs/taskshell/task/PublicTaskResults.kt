package com.wmdhs.taskshell.task

import java.security.MessageDigest
import java.time.Instant

data class PublicTaskSummary(
    val taskId: String,
    val status: String,
    val command: String?,
    val commandPreview: String?,
    val commandLength: Int?,
    val commandSha256: String?,
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
    val stdoutTruncated: Boolean = false,
    val stderrTruncated: Boolean = false,
    val message: String? = null,
    val nextActions: List<String> = emptyList()
)

data class PublicTaskStatusResult(
    val taskId: String,
    val status: String,
    val command: String?,
    val commandPreview: String? = null,
    val commandLength: Int? = null,
    val commandSha256: String? = null,
    val cwd: String?,
    val createdAt: String,
    val updatedAt: String,
    val startedAt: String? = null,
    val endedAt: String? = null,
    val durationMillis: Long? = null,
    val running: Boolean? = null,
    val exitCode: Int? = null,
    val message: String? = null,
    val nextActions: List<String> = emptyList()
)

data class PublicTaskLogsResult(
    val taskId: String,
    val status: String,
    val stdout: String? = null,
    val stderr: String? = null,
    val exitCode: Int? = null,
    val maxLines: Int? = null,
    val maxBytes: Int? = null,
    val stdoutTruncated: Boolean = false,
    val stderrTruncated: Boolean = false,
    val message: String? = null,
    val nextActions: List<String> = emptyList()
)

data class PublicTaskStopResult(
    val taskId: String,
    val status: String,
    val updatedAt: String
)

fun ShellTask.toPublicSummary(includeCommand: Boolean = false): PublicTaskSummary = PublicTaskSummary(
    taskId = taskId,
    status = status.publicName(),
    command = command.takeIf { includeCommand },
    commandPreview = command.preview(),
    commandLength = command.length,
    commandSha256 = command.sha256(),
    cwd = workingDirectory,
    createdAt = createdAt.toIsoString(),
    updatedAt = updatedAt.toIsoString()
)

fun ShellTaskStatus.publicName(): String = name.lowercase()

fun Instant.toIsoString(): String = toString()
fun String.preview(maxLength: Int = 120): String =
    redactSensitiveText()
        .replace(Regex("\\s+"), " ")
        .trim()
        .let { normalized ->
            if (normalized.length <= maxLength) normalized else normalized.take(maxLength) + "…"
        }

fun String.redactSensitiveText(): String =
    replace(Regex("""(?i)(authorization\s*[:=]\s*bearer\s+)[^\s'"]+"""), "$1<redacted>")
        .replace(Regex("""(?i)(api[_-]?key|token|password|passwd|secret)(\s*[:=]\s*)[^\s'"]+"""), "$1$2<redacted>")
        .replace(Regex("""sk-[A-Za-z0-9_-]{12,}"""), "sk-<redacted>")
        .replace(Regex("""gh[pousr]_[A-Za-z0-9_]{12,}"""), "gh<redacted>")

fun String.sha256(): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}
