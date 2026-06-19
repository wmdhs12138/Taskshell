package com.wmdhs.taskshell

import com.wmdhs.taskshell.mcp.McpServer
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class TestCommandStartResult(
    val taskId: String?,
    val displayText: String
)

class TaskshellLocalClient {
    fun checkHealth(): Pair<Boolean, String> {
        return try {
            val connection = URL(McpServer.HEALTH_ENDPOINT).openConnection() as HttpURLConnection
            connection.connectTimeout = 800
            connection.readTimeout = 800
            connection.requestMethod = "GET"
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            true to text
        } catch (throwable: Throwable) {
            false to (throwable.message ?: throwable::class.java.simpleName)
        }
    }

    fun startTestCommand(token: String): TestCommandStartResult {
        val response = callTool(
            token = token,
            name = "shell_task_start",
            arguments = JSONObject()
                .put("command", "echo hello from taskshell; date; uname -a")
                .put("cwd", "/data/data/com.termux/files/home")
        )
        val taskId = runCatching {
            JSONObject(response.substringAfter('\n'))
                .optJSONObject("result")
                ?.optString("taskId")
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
        return TestCommandStartResult(
            taskId = taskId,
            displayText = buildString {
                append(response)
                append("\n\n")
                if (taskId != null) {
                    append("Parsed taskId: ").append(taskId).append('\n')
                    append("Next: tap Status or Logs after 1-2 seconds.\n")
                } else {
                    append("taskId not found in response. Ensure service is running.\n")
                }
                append("Termux check: ls -R ~/.taskshell/tasks")
            }
        )
    }

    fun callTool(token: String, name: String, arguments: JSONObject): String {
        return try {
            val payload = JSONObject()
                .put("name", name)
                .put("arguments", arguments)
                .toString()

            val connection = URL(McpServer.DEFAULT_TOOLS_CALL_ENDPOINT).openConnection() as HttpURLConnection
            connection.connectTimeout = 2_000
            connection.readTimeout = 5_000
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            formatToolResponse(code, text)
        } catch (throwable: Throwable) {
            "Request failed: ${throwable.message ?: throwable::class.java.simpleName}"
        }
    }

    private fun formatToolResponse(code: Int, text: String): String {
        val pretty = runCatching { prettyJson(text) }.getOrElse { text }
        return "HTTP $code\n$pretty"
    }

    private fun prettyJson(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return trimmed
        return when {
            trimmed.startsWith("{") -> JSONObject(trimmed).toString(2)
            trimmed.startsWith("[") -> JSONArray(trimmed).toString(2)
            else -> trimmed
        }
    }
}
