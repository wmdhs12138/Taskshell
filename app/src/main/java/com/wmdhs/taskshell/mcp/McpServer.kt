package com.wmdhs.taskshell.mcp

import android.content.Context
import com.wmdhs.taskshell.security.TokenManager
import com.wmdhs.taskshell.task.ShellTaskManager
import com.wmdhs.taskshell.termux.TermuxCommandExecutor

class McpServer(context: Context) {
    private val taskManager = ShellTaskManager(
        executor = TermuxCommandExecutor(context.applicationContext)
    )
    private val tokenManager = TokenManager(context.applicationContext)
    private val registry = McpToolRegistry(taskManager)
    private val httpServer = LocalHttpMcpServer(
        registry = registry,
        tokenProvider = { tokenManager.getOrCreateToken() }
    )

    @Volatile
    var isRunning: Boolean = false
        private set

    fun start() {
        if (isRunning) return
        httpServer.start()
        isRunning = true
    }

    fun stop() {
        httpServer.stop()
        isRunning = false
    }

    fun tools(): List<McpTool> = registry.listTools()

    fun handleToolCall(name: String, arguments: Map<String, Any?>): Any {
        check(isRunning) { "MCP server is not running" }
        return registry.callTool(name, arguments)
    }

    companion object {
        const val DEFAULT_ENDPOINT = "http://127.0.0.1:8765/mcp"
        const val HEALTH_ENDPOINT = "http://127.0.0.1:8765/health"
        const val DEFAULT_TOOLS_CALL_ENDPOINT = "http://127.0.0.1:8765/tools/call"
    }
}
