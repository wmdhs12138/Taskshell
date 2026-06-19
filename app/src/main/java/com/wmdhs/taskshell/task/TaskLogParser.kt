package com.wmdhs.taskshell.task

data class ParsedTaskLogs(
    val stdout: String? = null,
    val stderr: String? = null
)

fun String.parseTaskLogs(): ParsedTaskLogs {
    val stdoutMarker = "--- stdout ---\n"
    val stderrMarker = "--- stderr ---\n"
    val stdoutStart = indexOf(stdoutMarker)
    val stderrStart = indexOf(stderrMarker)
    if (stdoutStart < 0 || stderrStart < 0 || stderrStart < stdoutStart) {
        return ParsedTaskLogs(stdout = this, stderr = null)
    }
    val stdout = substring(stdoutStart + stdoutMarker.length, stderrStart).trimEnd()
    val stderr = substring(stderrStart + stderrMarker.length).trimEnd()
    return ParsedTaskLogs(stdout = stdout, stderr = stderr)
}

fun String.parseKeyValueLines(): Map<String, String> {
    return lineSequence()
        .mapNotNull { line ->
            val index = line.indexOf('=')
            if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
        }
        .toMap()
}
