package com.wmdhs.taskshell.security

data class CommandPolicyResult(
    val allowed: Boolean,
    val reason: String? = null,
    val taskKind: TaskKind = TaskKind.Normal
)

enum class TaskKind {
    Light,
    Normal,
    Heavy
}

class CommandPolicy {
    fun validate(command: String, workingDirectory: String?): CommandPolicyResult {
        val trimmed = command.trim()
        if (trimmed.isBlank()) {
            return CommandPolicyResult(false, "Command must not be blank")
        }
        if (trimmed.length > MAX_COMMAND_LENGTH) {
            return CommandPolicyResult(false, "Command is too long. Max length: $MAX_COMMAND_LENGTH")
        }
        if (workingDirectory != null && !isAllowedWorkingDirectory(workingDirectory)) {
            return CommandPolicyResult(false, "Working directory is outside allowed Termux paths")
        }

        val normalized = trimmed.lowercase()
        val dangerous = dangerousPatterns.firstOrNull { it.containsMatchIn(normalized) }
        if (dangerous != null) {
            return CommandPolicyResult(false, "Command blocked by safety policy: ${dangerous.pattern}")
        }

        return CommandPolicyResult(true, taskKind = classify(trimmed))
    }

    private fun isAllowedWorkingDirectory(cwd: String): Boolean {
        return allowedDirectoryPrefixes.any { prefix -> cwd == prefix || cwd.startsWith("$prefix/") }
    }

    private fun classify(command: String): TaskKind {
        val normalized = command.lowercase()
        return when {
            heavyPatterns.any { it.containsMatchIn(normalized) } -> TaskKind.Heavy
            normalPatterns.any { it.containsMatchIn(normalized) } -> TaskKind.Normal
            else -> TaskKind.Light
        }
    }

    companion object {
        const val MAX_COMMAND_LENGTH = 8_000
        val allowedDirectoryPrefixes = listOf(
            "/data/data/com.termux/files/home",
            "/data/data/com.termux/files/usr/tmp"
        )

        private val dangerousPatterns = listOf(
            Regex("(^|[;&|`$()\\s])su([\\s;&|]|$)"),
            Regex("(^|[;&|`$()\\s])reboot([\\s;&|]|$)"),
            Regex("(^|[;&|`$()\\s])shutdown([\\s;&|]|$)"),
            Regex("(^|[;&|`$()\\s])setprop([\\s;&|]|$)"),
            Regex("(^|[;&|`$()\\s])settings([\\s;&|]|$)"),
            Regex("(^|[;&|`$()\\s])pm\\s+uninstall([\\s;&|]|$)"),
            Regex("(^|[;&|`$()\\s])dd\\s+.*\\bof=/dev/"),
            Regex("(^|[;&|`$()\\s])mkfs(\\.|[\\s;&|]|$)"),
            Regex("""rm\s+(-[a-z]*r[a-z]*f|-\w*f\w*r|-rf|-fr)\s+(/|/data|/sdcard|[$]HOME|~)([\s;&|]|$)""", RegexOption.IGNORE_CASE)
        )

        private val heavyPatterns = listOf(
            Regex("(^|[\\s./])gradlew?\\s+"),
            Regex("npm\\s+run\\s+build"),
            Regex("pnpm\\s+run\\s+build"),
            Regex("yarn\\s+build"),
            Regex("cargo\\s+build"),
            Regex("make\\s+(-j\\s*)?"),
            Regex("cmake\\s+--build"),
            Regex("ffmpeg\\s+")
        )

        private val normalPatterns = listOf(
            Regex("git\\s+clone"),
            Regex("npm\\s+install"),
            Regex("pnpm\\s+install"),
            Regex("yarn\\s+install"),
            Regex("pip\\s+install"),
            Regex("pkg\\s+install"),
            Regex("tar\\s+"),
            Regex("unzip\\s+")
        )
    }
}
