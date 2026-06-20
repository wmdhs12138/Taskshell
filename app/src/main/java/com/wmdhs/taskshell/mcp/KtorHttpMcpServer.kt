package com.wmdhs.taskshell.mcp

import com.wmdhs.taskshell.service.ServiceEventLogger
import com.wmdhs.taskshell.service.ServiceHeartbeatController
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.options
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Modifier
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

class KtorHttpMcpServer(
    private val registry: McpToolRegistry,
    private val host: String = LocalHttpMcpServer.DEFAULT_HOST,
    private val port: Int = LocalHttpMcpServer.DEFAULT_PORT,
    private val tokenProvider: () -> String
) {
    private val running = AtomicBoolean(false)
    private var engine: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        engine = embeddedServer(CIO, host = host, port = port) {
            routing {
                options("/{...}") {
                    call.respondCors(HttpStatusCode.NoContent, "")
                }
                get("/health") {
                    ServiceHeartbeatController.notifyActivity("http_GET_/health")
                    call.respondJson(
                        JSONObject()
                            .put("name", "Taskshell")
                            .put("status", "ok")
                            .put("host", host)
                            .put("port", port)
                            .put("time", Instant.now().toString())
                            .put("backend", "ktor-cio")
                            .put("diagnostics", ServiceEventLogger.diagnostics().toJsonValue())
                    )
                }
                get("/tools") {
                    ServiceHeartbeatController.notifyActivity("http_GET_/tools")
                    if (!call.isAuthorized()) return@get call.respondUnauthorized()
                    call.respondJson(JSONObject().put("tools", JSONArray(registry.listTools().map { it.toJson() })))
                }
                post("/tools/call") {
                    ServiceHeartbeatController.notifyActivity("http_POST_/tools/call")
                    if (!call.isAuthorized()) return@post call.respondUnauthorized()
                    val body = call.receiveText()
                    val json = JSONObject(body.ifBlank { "{}" })
                    val name = json.optString("name")
                    val args = json.optJSONObject("arguments")?.toMap() ?: emptyMap()
                    call.respondJson(JSONObject().put("result", callToolSafely(name, args)))
                }
                post("/mcp") {
                    ServiceHeartbeatController.notifyActivity("http_POST_/mcp")
                    if (!call.isAuthorized()) return@post call.respondUnauthorized()
                    val response = handleJsonRpc(call.receiveText())
                    if (response == null) {
                        call.respondCors(HttpStatusCode.Accepted, "")
                    } else {
                        call.respondJson(response)
                    }
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        running.set(false)
        runCatching { engine?.stop(gracePeriodMillis = 500, timeoutMillis = 2_000) }
        engine = null
    }

    private fun handleJsonRpc(body: String): JSONObject? {
        val request = try {
            JSONObject(body.ifBlank { "{}" })
        } catch (throwable: Throwable) {
            return jsonRpcError(JSONObject.NULL, -32700, "Parse error", throwable.message)
        }
        val id = if (request.has("id")) request.opt("id") else JSONObject.NULL
        val method = request.optString("method")
        val params = request.optJSONObject("params") ?: JSONObject()

        if (method.isBlank()) {
            return jsonRpcError(id, -32600, "Invalid Request", "Missing JSON-RPC method")
        }
        if (method.startsWith("notifications/")) {
            return null
        }

        val result = when (method) {
            "initialize" -> {
                val requestedVersion = params.optString("protocolVersion").takeIf { it.isNotBlank() } ?: "2024-11-05"
                JSONObject()
                    .put("protocolVersion", requestedVersion)
                    .put("serverInfo", JSONObject().put("name", "Taskshell").put("version", "0.1.0"))
                    .put("capabilities", JSONObject().put("tools", JSONObject()))
            }
            "ping" -> JSONObject()
            "tools/list" -> JSONObject().put("tools", JSONArray(registry.listTools().map { it.toMcpJson() }))
            "tools/call" -> {
                val name = params.optString("name")
                val args = params.optJSONObject("arguments")?.toMap() ?: emptyMap()
                callToolSafely(name, args)
            }
            else -> return jsonRpcError(id, -32601, "Method not found", "Unsupported JSON-RPC method: $method")
        }
        return JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", result)
    }

    private fun callToolSafely(name: String, args: Map<String, Any?>): JSONObject {
        if (name.isBlank()) return toolCallError("Missing tool name", "InvalidParams")
        return try {
            toolCallText(registry.callTool(name, args).toJsonValue().toString(), isError = false)
        } catch (throwable: Throwable) {
            toolCallError(throwable.message ?: throwable::class.java.simpleName, throwable::class.java.simpleName)
        }
    }

    private fun toolCallText(text: String, isError: Boolean): JSONObject = JSONObject()
        .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", text)))
        .put("isError", isError)

    private fun toolCallError(message: String, errorType: String): JSONObject {
        return toolCallText(JSONObject().put("error", message).put("type", errorType).toString(), isError = true)
    }

    private fun jsonRpcError(id: Any?, code: Int, message: String, details: String? = null): JSONObject {
        val error = JSONObject()
            .put("code", code)
            .put("message", message)
            .also { if (!details.isNullOrBlank()) it.put("data", details) }
        return JSONObject().put("jsonrpc", "2.0").put("id", id ?: JSONObject.NULL).put("error", error)
    }

    private fun ApplicationCall.isAuthorized(): Boolean {
        val expected = tokenProvider()
        val authorization = request.header(HttpHeaders.Authorization).orEmpty()
        val bearer = authorization.removePrefix("Bearer ").takeIf { it != authorization }
        val tokenHeader = request.header("X-Taskshell-Token")
        return constantTimeEquals(bearer, expected) || constantTimeEquals(tokenHeader, expected)
    }

    private suspend fun ApplicationCall.respondUnauthorized() {
        respondJson(JSONObject().put("error", "Unauthorized"), HttpStatusCode.Unauthorized)
    }

    private suspend fun ApplicationCall.respondJson(json: JSONObject, status: HttpStatusCode = HttpStatusCode.OK) {
        respondCors(status, json.toString())
    }

    private suspend fun ApplicationCall.respondCors(status: HttpStatusCode, text: String) {
        response.headers.append("Access-Control-Allow-Origin", "*")
        response.headers.append("Access-Control-Allow-Methods", "GET,POST,OPTIONS")
        response.headers.append("Access-Control-Allow-Headers", "content-type,authorization,x-taskshell-token")
        respondText(text = text, contentType = ContentType.Application.Json, status = status)
    }

    private fun constantTimeEquals(a: String?, b: String): Boolean {
        if (a.isNullOrBlank()) return false
        val aBytes = a.toByteArray()
        val bBytes = b.toByteArray()
        var diff = aBytes.size xor bBytes.size
        val max = maxOf(aBytes.size, bBytes.size)
        for (i in 0 until max) {
            val av = if (i < aBytes.size) aBytes[i].toInt() else 0
            val bv = if (i < bBytes.size) bBytes[i].toInt() else 0
            diff = diff or (av xor bv)
        }
        return diff == 0
    }

    private fun JSONObject.toMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        keys().forEach { key -> map[key] = get(key).unwrapJson() }
        return map
    }

    private fun Any?.unwrapJson(): Any? = when (this) {
        JSONObject.NULL -> null
        is JSONObject -> this.toMap()
        is JSONArray -> (0 until length()).map { get(it).unwrapJson() }
        else -> this
    }

    private fun McpTool.toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("title", title)
        .put("description", description)

    private fun McpTool.toMcpJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("title", title)
        .put("description", description)
        .put("annotations", JSONObject()
            .put("title", title)
            .put("readOnlyHint", readOnlyHint)
            .put("destructiveHint", destructiveHint)
            .put("openWorldHint", openWorldHint)
        )
        .put("inputSchema", inputSchemaFor(name))

    private fun inputSchemaFor(toolName: String): JSONObject {
        fun stringProp(description: String) = JSONObject().put("type", "string").put("description", description)
        fun intProp(description: String, defaultValue: Int? = null, minimum: Int? = null, maximum: Int? = null) = JSONObject()
            .put("type", "integer")
            .put("description", description)
            .also { if (defaultValue != null) it.put("default", defaultValue) }
            .also { if (minimum != null) it.put("minimum", minimum) }
            .also { if (maximum != null) it.put("maximum", maximum) }
        fun boolProp(description: String, defaultValue: Boolean? = null) = JSONObject()
            .put("type", "boolean")
            .put("description", description)
            .also { if (defaultValue != null) it.put("default", defaultValue) }
        fun objectProp(description: String, additionalProperties: JSONObject? = null) = JSONObject()
            .put("type", "object")
            .put("description", description)
            .also { if (additionalProperties != null) it.put("additionalProperties", additionalProperties) }
        fun schema(properties: JSONObject, required: List<String> = emptyList()) = JSONObject()
            .put("type", "object")
            .put("properties", properties)
            .put("required", JSONArray(required))
            .put("additionalProperties", false)

        return when (toolName) {
            "shell_exec" -> schema(
                JSONObject()
                    .put("command", stringProp("Shell command to execute in Termux."))
                    .put("cwd", stringProp("Working directory. Recommended default: /data/data/com.termux/files/home."))
                    .put("workingDirectory", stringProp("Alias of cwd."))
                    .put("env", objectProp("Environment variables to export before executing the command. Variable names must match ^[A-Za-z_][A-Za-z0-9_]*$.", JSONObject().put("type", "string")))
                    .put("stdin", stringProp("Optional stdin content passed to the command. Limited to 256 KiB."))
                    .put("input", stringProp("Alias of stdin."))
                    .put("timeoutMillis", intProp("Optional command timeout. The task fails if the command exceeds this duration.", null, 1, 86400000))
                    .put("waitMillis", intProp("Short wait time before returning background task result.", 10000, 0, 30000)),
                required = listOf("command")
            )
            "shell_task_start" -> schema(
                JSONObject()
                    .put("command", stringProp("Shell command to start as a tmux-backed background task."))
                    .put("cwd", stringProp("Working directory. Use /data/data/com.termux/files/home unless the user requests another allowed Termux path."))
                    .put("workingDirectory", stringProp("Alias of cwd."))
                    .put("workdir", stringProp("Alias of cwd."))
                    .put("working_dir", stringProp("Alias of cwd."))
                    .put("env", objectProp("Environment variables to export before executing the command. Variable names must match ^[A-Za-z_][A-Za-z0-9_]*$.", JSONObject().put("type", "string")))
                    .put("stdin", stringProp("Optional stdin content passed to the command. Limited to 256 KiB."))
                    .put("input", stringProp("Alias of stdin."))
                    .put("timeoutMillis", intProp("Optional command timeout. The task fails if the command exceeds this duration.", null, 1, 86400000))
                    .put("waitMillis", intProp("Optional short wait before returning. If the task finishes within this time, returns inline result; otherwise returns taskId plus pollAfterMillis. Prefer this over immediate status polling.", 0, 0, 30000))
                    .put("includeCommand", boolProp("Return the full command in the result. Defaults to false; commandPreview, commandLength, and commandSha256 are returned instead.")),
                required = listOf("command")
            )
            "shell_task_status" -> schema(
                JSONObject()
                    .put("taskId", stringProp("Task id returned by shell_task_start or shell_exec."))
                    .put("includeCommand", boolProp("Return the full command in the result. Defaults to false; commandPreview, commandLength, and commandSha256 are returned instead."))
                    .put("waitMillis", intProp("Optional long-poll wait time. The server waits up to this duration for task completion, reducing repeated status calls.", 0, 0, 60000)),
                required = listOf("taskId")
            )
            "shell_task_logs" -> schema(
                JSONObject()
                    .put("taskId", stringProp("Task id returned by shell_task_start or shell_exec."))
                    .put("maxLines", intProp("Maximum log lines to return.", 200, 1, 5000))
                    .put("maxBytes", intProp("Maximum bytes to return for each output stream after line tailing.", 65536, 1024, 1048576)),
                required = listOf("taskId")
            )
            "shell_task_stop" -> schema(JSONObject().put("taskId", stringProp("Task id to stop.")), required = listOf("taskId"))
            "shell_task_list" -> schema(JSONObject().put("includeCommand", boolProp("Return full commands in task summaries. Defaults to false.")))
            "shell_task_cleanup" -> schema(
                JSONObject()
                    .put("olderThanHours", intProp("Clean finished task directories older than this many hours.", 24, 1, 8760))
                    .put("keepLatest", intProp("Always keep the latest N finished task records/directories.", 20, 0, 500))
                    .put("dryRun", boolProp("Preview cleanup without deleting files.", true))
            )
            "shell_task_recover" -> schema(JSONObject())
            "shell_task_debug" -> schema(JSONObject().put("taskId", stringProp("Task id to inspect with advanced diagnostics.")), required = listOf("taskId"))
            "audit_logs" -> schema(JSONObject().put("limit", intProp("Maximum audit events to return.", 50)))
            "audit_clear" -> schema(JSONObject())
            "service_diagnostics" -> schema(JSONObject())
            else -> schema(JSONObject())
        }
    }

    private fun Any?.toJsonValue(): Any? = when (this) {
        null -> JSONObject.NULL
        is JSONObject, is JSONArray, is String, is Number, is Boolean -> this
        is Instant -> toString()
        is Map<*, *> -> JSONObject().also { obj -> this.forEach { (k, v) -> obj.put(k.toString(), v.toJsonValue()) } }
        is Iterable<*> -> JSONArray().also { arr -> this.forEach { arr.put(it.toJsonValue()) } }
        is Enum<*> -> name
        else -> JSONObject().also { obj ->
            this::class.java.declaredFields
                .asSequence()
                .filterNot { it.isSynthetic }
                .filterNot { Modifier.isStatic(it.modifiers) }
                .filterNot { it.name.startsWith("$") }
                .forEach { field ->
                    runCatching {
                        field.isAccessible = true
                        obj.put(field.name, field.get(this).toJsonValue())
                    }
                }
        }
    }
}
