package com.wmdhs.taskshell.mcp

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.lang.reflect.Modifier
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class LocalHttpMcpServer(
    private val registry: McpToolRegistry,
    private val host: String = DEFAULT_HOST,
    private val port: Int = DEFAULT_PORT,
    private val tokenProvider: () -> String
) {
    private val running = AtomicBoolean(false)
    private val clientPool: ExecutorService = Executors.newCachedThreadPool()
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val address = InetAddress.getByName(host)
        val socket = ServerSocket(port, 50, address)
        serverSocket = socket
        acceptThread = thread(name = "Taskshell-MCP-HTTP", isDaemon = true) {
            while (running.get()) {
                try {
                    val client = socket.accept()
                    clientPool.execute { handleClient(client) }
                } catch (_: Throwable) {
                    if (running.get()) continue
                }
            }
        }
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 30_000
            val input = client.getInputStream()
            val headerBytes = ByteArrayOutputStream()
            val tail = ArrayDeque<Int>()
            while (true) {
                val next = input.read()
                if (next < 0) return
                headerBytes.write(next)
                tail.addLast(next)
                if (tail.size > 4) tail.removeFirst()
                if (tail.size == 4 && tail.toList() == listOf(13, 10, 13, 10)) break
                if (headerBytes.size() > MAX_HEADER_BYTES) {
                    writeResponse(client, HttpResponse(431, "Request Header Fields Too Large", JSONObject().put("error", "Headers too large").toString()))
                    return
                }
            }

            val headerText = headerBytes.toString(StandardCharsets.ISO_8859_1.name())
            val headerLines = headerText.split("\r\n")
            val requestLine = headerLines.firstOrNull().orEmpty()
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                writeResponse(client, HttpResponse(400, "Bad Request", JSONObject().put("error", "Invalid request line").toString()))
                return
            }
            val method = parts[0].uppercase()
            val rawPath = parts[1]

            val headers = mutableMapOf<String, String>()
            headerLines.drop(1).forEach { line ->
                if (line.isNotBlank()) {
                    val idx = line.indexOf(':')
                    if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
                }
            }

            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            if (contentLength > MAX_BODY_BYTES) {
                writeResponse(client, HttpResponse(413, "Payload Too Large", JSONObject().put("error", "Request body too large").toString()))
                return
            }
            val body = if (contentLength > 0) {
                val bodyBytes = ByteArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val n = input.read(bodyBytes, read, contentLength - read)
                    if (n < 0) break
                    read += n
                }
                if (read != contentLength) {
                    writeResponse(client, HttpResponse(400, "Bad Request", JSONObject().put("error", "Request body truncated").put("expected", contentLength).put("actual", read).toString()))
                    return
                }
                String(bodyBytes, StandardCharsets.UTF_8)
            } else {
                ""
            }

            val response = route(method, rawPath, body, headers)
            writeResponse(client, response)
        }
    }

    private fun route(method: String, rawPath: String, body: String, headers: Map<String, String>): HttpResponse {
        return try {
            val path = rawPath.substringBefore('?')
            if (method != "OPTIONS" && path != "/health" && !isAuthorized(headers)) {
                return HttpResponse(
                    statusCode = 401,
                    statusText = "Unauthorized",
                    body = JSONObject().put("error", "Unauthorized").toString()
                )
            }
            when {
                method == "GET" && path == "/health" -> ok(
                    JSONObject()
                        .put("name", "Taskshell")
                        .put("status", "ok")
                        .put("host", host)
                        .put("port", port)
                        .put("time", Instant.now().toString())
                )

                method == "GET" && path == "/tools" -> ok(
                    JSONObject().put("tools", JSONArray(registry.listTools().map { it.toJson() }))
                )

                method == "POST" && path == "/tools/call" -> {
                    val json = JSONObject(body.ifBlank { "{}" })
                    val name = json.optString("name")
                    val args = json.optJSONObject("arguments")?.toMap() ?: emptyMap()
                    require(name.isNotBlank()) { "Missing tool name" }
                    ok(JSONObject().put("result", registry.callTool(name, args).toJsonValue()))
                }

                method == "POST" && path == "/mcp" -> handleJsonRpc(body)

                method == "OPTIONS" -> HttpResponse(204, "No Content", "")

                else -> HttpResponse(404, "Not Found", JSONObject().put("error", "Not found").toString())
            }
        } catch (throwable: Throwable) {
            HttpResponse(
                statusCode = 500,
                statusText = "Internal Server Error",
                body = JSONObject()
                    .put("error", throwable.message ?: throwable::class.java.name)
                    .toString()
            )
        }
    }

    private fun handleJsonRpc(body: String): HttpResponse {
        val request = JSONObject(body.ifBlank { "{}" })
        val id = request.opt("id")
        val method = request.optString("method")
        val params = request.optJSONObject("params") ?: JSONObject()

        // JSON-RPC notifications have no id and must not produce a JSON-RPC response.
        // RikkaHub sends this after initialize when using Streamable HTTP.
        if (method.startsWith("notifications/")) {
            return HttpResponse(202, "Accepted", "")
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

            "tools/list" -> JSONObject().put(
                "tools",
                JSONArray(registry.listTools().map { it.toMcpJson() })
            )

            "tools/call" -> {
                val name = params.optString("name")
                val args = params.optJSONObject("arguments")?.toMap() ?: emptyMap()
                JSONObject()
                    .put(
                        "content",
                        JSONArray().put(
                            JSONObject()
                                .put("type", "text")
                                .put("text", registry.callTool(name, args).toJsonValue().toString())
                        )
                    )
                    .put("isError", false)
            }

            else -> throw IllegalArgumentException("Unsupported JSON-RPC method: $method")
        }
        return ok(JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", result))
    }

    private fun isAuthorized(headers: Map<String, String>): Boolean {
        val expected = tokenProvider()
        val authorization = headers["authorization"].orEmpty()
        val bearer = authorization.removePrefix("Bearer ").takeIf { it != authorization }
        val tokenHeader = headers["x-taskshell-token"]
        return constantTimeEquals(bearer, expected) || constantTimeEquals(tokenHeader, expected)
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

    private fun writeResponse(socket: Socket, response: HttpResponse) {
        val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
        val bytes = response.body.toByteArray(StandardCharsets.UTF_8)
        writer.write("HTTP/1.1 ${response.statusCode} ${response.statusText}\r\n")
        writer.write("Content-Type: application/json; charset=utf-8\r\n")
        writer.write("Content-Length: ${bytes.size}\r\n")
        writer.write("Access-Control-Allow-Origin: *\r\n")
        writer.write("Access-Control-Allow-Methods: GET,POST,OPTIONS\r\n")
        writer.write("Access-Control-Allow-Headers: content-type,authorization\r\n")
        writer.write("Connection: close\r\n")
        writer.write("\r\n")
        writer.flush()
        socket.getOutputStream().write(bytes)
        socket.getOutputStream().flush()
    }

    private fun ok(json: JSONObject): HttpResponse = HttpResponse(200, "OK", json.toString())

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
        .put("description", description)

    private fun McpTool.toMcpJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("description", description)
        .put("inputSchema", inputSchemaFor(name))

    private fun inputSchemaFor(toolName: String): JSONObject {
        fun stringProp(description: String) = JSONObject()
            .put("type", "string")
            .put("description", description)
        fun intProp(description: String, defaultValue: Int? = null) = JSONObject()
            .put("type", "integer")
            .put("description", description)
            .also { if (defaultValue != null) it.put("default", defaultValue) }
        fun boolProp(description: String, defaultValue: Boolean? = null) = JSONObject()
            .put("type", "boolean")
            .put("description", description)
            .also { if (defaultValue != null) it.put("default", defaultValue) }
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
                    .put("waitMillis", intProp("Short wait time before returning background task result.", 10000)),
                required = listOf("command")
            )
            "shell_task_start" -> schema(
                JSONObject()
                    .put("command", stringProp("Shell command to start as a tmux-backed background task."))
                    .put("cwd", stringProp("Working directory. Use /data/data/com.termux/files/home unless the user requests another allowed Termux path."))
                    .put("workingDirectory", stringProp("Alias of cwd."))
                    .put("workdir", stringProp("Alias of cwd."))
                    .put("working_dir", stringProp("Alias of cwd.")),
                required = listOf("command")
            )
            "shell_task_status" -> schema(
                JSONObject().put("taskId", stringProp("Task id returned by shell_task_start or shell_exec.")),
                required = listOf("taskId")
            )
            "shell_task_logs" -> schema(
                JSONObject()
                    .put("taskId", stringProp("Task id returned by shell_task_start or shell_exec."))
                    .put("maxLines", intProp("Maximum log lines to return.", 200)),
                required = listOf("taskId")
            )
            "shell_task_stop" -> schema(
                JSONObject().put("taskId", stringProp("Task id to stop.")),
                required = listOf("taskId")
            )
            "shell_task_list" -> schema(JSONObject())
            "shell_task_cleanup" -> schema(
                JSONObject()
                    .put("olderThanHours", intProp("Clean finished task directories older than this many hours.", 24))
                    .put("keepLatest", intProp("Always keep the latest N finished task records/directories.", 20))
                    .put("dryRun", boolProp("Preview cleanup without deleting files.", true))
            )
            "shell_task_recover" -> schema(JSONObject())
            "audit_logs" -> schema(
                JSONObject().put("limit", intProp("Maximum audit events to return.", 50))
            )
            "audit_clear" -> schema(JSONObject())
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

    data class HttpResponse(val statusCode: Int, val statusText: String, val body: String)

    companion object {
        const val DEFAULT_HOST = "127.0.0.1"
        const val DEFAULT_PORT = 8765
        private const val MAX_HEADER_BYTES = 64 * 1024
        private const val MAX_BODY_BYTES = 1024 * 1024
    }
}
