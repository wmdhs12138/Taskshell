package com.wmdhs.taskshell.mcp

data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: String? = null,
    val method: String,
    val params: Map<String, Any?> = emptyMap()
)

data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: String? = null,
    val result: Any? = null,
    val error: JsonRpcError? = null
)

data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: Any? = null
)
