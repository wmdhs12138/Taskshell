package com.wmdhs.taskshell

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wmdhs.taskshell.security.TokenManager
import com.wmdhs.taskshell.service.TaskshellServiceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

data class TaskshellUiState(
    val apiToken: String = "",
    val serviceReachable: Boolean = false,
    val statusText: String = "Not checked",
    val testResultText: String = "No test has been run yet",
    val auditText: String = "No audit logs loaded",
    val lastTestTaskId: String? = null
)

class TaskshellViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenManager = TokenManager(application.applicationContext)
    private val localClient = TaskshellLocalClient()

    private val _uiState = MutableStateFlow(
        TaskshellUiState(apiToken = tokenManager.getOrCreateToken())
    )
    val uiState: StateFlow<TaskshellUiState> = _uiState.asStateFlow()

    fun setTestResultText(text: String) {
        _uiState.update { it.copy(testResultText = text) }
    }

    fun markStartRequested() {
        _uiState.update { it.copy(statusText = "Start requested. Waiting for /health...") }
    }

    fun markStopRequested() {
        _uiState.update { it.copy(statusText = "Stop requested") }
    }

    fun refreshStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = localClient.checkHealth()
            updateServiceStatus(result)
        }
    }

    fun refreshStatusDelayed() {
        viewModelScope.launch(Dispatchers.IO) {
            delay(700)
            val result = localClient.checkHealth()
            updateServiceStatus(result)
        }
    }

    fun regenerateToken() {
        val newToken = tokenManager.regenerateToken()
        _uiState.update {
            it.copy(
                apiToken = newToken,
                testResultText = "API token regenerated. Update RikkaHub Authorization header."
            )
        }
    }

    fun runTestCommand() {
        val token = _uiState.value.apiToken
        _uiState.update { it.copy(testResultText = "Running test command...") }
        viewModelScope.launch(Dispatchers.IO) {
            val result = localClient.startTestCommand(token)
            _uiState.update {
                it.copy(
                    lastTestTaskId = result.taskId,
                    testResultText = result.displayText
                )
            }
            refreshStatus()
        }
    }

    fun checkLastTaskStatus() {
        val state = _uiState.value
        val taskId = state.lastTestTaskId
        if (taskId.isNullOrBlank()) {
            _uiState.update { it.copy(testResultText = "No taskId yet. Run test command first.") }
            return
        }
        _uiState.update { it.copy(testResultText = "Checking status for $taskId...") }
        viewModelScope.launch(Dispatchers.IO) {
            val text = localClient.callTool(
                token = state.apiToken,
                name = "shell_task_status",
                arguments = JSONObject().put("taskId", taskId)
            )
            _uiState.update { it.copy(testResultText = text) }
        }
    }

    fun checkLastTaskLogs() {
        val state = _uiState.value
        val taskId = state.lastTestTaskId
        if (taskId.isNullOrBlank()) {
            _uiState.update { it.copy(testResultText = "No taskId yet. Run test command first.") }
            return
        }
        _uiState.update { it.copy(testResultText = "Checking logs for $taskId...") }
        viewModelScope.launch(Dispatchers.IO) {
            val text = localClient.callTool(
                token = state.apiToken,
                name = "shell_task_logs",
                arguments = JSONObject().put("taskId", taskId).put("maxLines", 200)
            )
            _uiState.update { it.copy(testResultText = text) }
        }
    }

    fun loadAuditLogs() {
        val token = _uiState.value.apiToken
        _uiState.update { it.copy(auditText = "Loading audit logs...") }
        viewModelScope.launch(Dispatchers.IO) {
            val text = localClient.callTool(
                token = token,
                name = "audit_logs",
                arguments = JSONObject().put("limit", 30)
            )
            _uiState.update { it.copy(auditText = text) }
        }
    }

    fun clearAuditLogs() {
        val token = _uiState.value.apiToken
        _uiState.update { it.copy(auditText = "Clearing audit logs...") }
        viewModelScope.launch(Dispatchers.IO) {
            val text = localClient.callTool(
                token = token,
                name = "audit_clear",
                arguments = JSONObject()
            )
            _uiState.update { it.copy(auditText = text) }
        }
    }

    private fun updateServiceStatus(result: Pair<Boolean, String>) {
        _uiState.update {
            it.copy(
                serviceReachable = result.first,
                statusText = buildServiceStatusText(result.first, result.second)
            )
        }
    }

    private fun buildServiceStatusText(reachable: Boolean, healthText: String): String {
        return buildString {
            append("reachable=").append(reachable).append('\n')
            append("serviceState.running=").append(TaskshellServiceState.running).append('\n')
            append("lastEvent=").append(TaskshellServiceState.lastEvent).append('\n')
            TaskshellServiceState.lastError?.let { append("lastError=").append(it).append('\n') }
            append("health=").append(healthText)
        }
    }
}
