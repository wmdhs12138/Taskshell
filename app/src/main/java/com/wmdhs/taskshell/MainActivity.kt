package com.wmdhs.taskshell

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ScrollState
import androidx.core.content.ContextCompat
import com.wmdhs.taskshell.mcp.McpServer
import com.wmdhs.taskshell.security.TokenManager
import com.wmdhs.taskshell.service.TaskshellForegroundService
import com.wmdhs.taskshell.service.TaskshellServiceState
import com.wmdhs.taskshell.ui.theme.TaskshellTheme
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TaskshellTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TaskshellApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskshellApp() {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context.applicationContext) }
    var apiToken by remember { mutableStateOf(tokenManager.getOrCreateToken()) }
    var serviceReachable by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Not checked") }
    var testResultText by remember { mutableStateOf("No test has been run yet") }
    var auditText by remember { mutableStateOf("No audit logs loaded") }
    var lastTestTaskId by remember { mutableStateOf<String?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    val termuxPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        testResultText = if (granted) {
            "Termux RUN_COMMAND permission granted. Tap Run again."
        } else {
            "Termux RUN_COMMAND permission denied. Grant it in system settings or reinstall after Termux is installed."
        }
    }

    fun refreshStatus() {
        Thread {
            val result = checkHealth()
            serviceReachable = result.first
            statusText = buildServiceStatusText(result.first, result.second)
        }.start()
    }

    fun refreshStatusDelayed() {
        Thread {
            Thread.sleep(700)
            val result = checkHealth()
            serviceReachable = result.first
            statusText = buildServiceStatusText(result.first, result.second)
        }.start()
    }

    fun runTestCommand() {
        if (ContextCompat.checkSelfPermission(context, TERMUX_RUN_COMMAND_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            testResultText = "Requesting Termux RUN_COMMAND permission..."
            termuxPermissionLauncher.launch(TERMUX_RUN_COMMAND_PERMISSION)
            return
        }
        testResultText = "Running test command..."
        Thread {
            val result = startTestCommand(apiToken)
            lastTestTaskId = result.taskId
            testResultText = result.displayText
            refreshStatus()
        }.start()
    }

    fun checkLastTaskStatus() {
        val taskId = lastTestTaskId
        if (taskId.isNullOrBlank()) {
            testResultText = "No taskId yet. Run test command first."
            return
        }
        testResultText = "Checking status for $taskId..."
        Thread {
            testResultText = callTool(
                token = apiToken,
                name = "shell_task_status",
                arguments = JSONObject().put("taskId", taskId)
            )
        }.start()
    }

    fun checkLastTaskLogs() {
        val taskId = lastTestTaskId
        if (taskId.isNullOrBlank()) {
            testResultText = "No taskId yet. Run test command first."
            return
        }
        testResultText = "Checking logs for $taskId..."
        Thread {
            testResultText = callTool(
                token = apiToken,
                name = "shell_task_logs",
                arguments = JSONObject().put("taskId", taskId).put("maxLines", 200)
            )
        }.start()
    }

    fun loadAuditLogs() {
        auditText = "Loading audit logs..."
        Thread {
            auditText = callTool(
                token = apiToken,
                name = "audit_logs",
                arguments = JSONObject().put("limit", 30)
            )
        }.start()
    }

    fun clearAuditLogs() {
        auditText = "Clearing audit logs..."
        Thread {
            auditText = callTool(
                token = apiToken,
                name = "audit_clear",
                arguments = JSONObject()
            )
        }.start()
    }

    LaunchedEffect(Unit) {
        refreshStatus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Taskshell") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Android MCP service for Termux shell tasks",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "本机 MCP 地址：${McpServer.DEFAULT_ENDPOINT}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text("MCP") })
                AssistChip(onClick = {}, label = { Text("Termux") })
                AssistChip(onClick = {}, label = { Text("tmux") })
            }

            ServiceControlCard(
                reachable = serviceReachable,
                statusText = statusText,
                onStart = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (ContextCompat.checkSelfPermission(context, TERMUX_RUN_COMMAND_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
                        termuxPermissionLauncher.launch(TERMUX_RUN_COMMAND_PERMISSION)
                    }
                    statusText = context.startTaskshellServiceSafely()
                    refreshStatusDelayed()
                },
                onStop = {
                    context.stopTaskshellService()
                    statusText = "Stop requested"
                    refreshStatusDelayed()
                },
                onRefresh = { refreshStatus() },
                onOpenBatterySettings = { context.openBatteryOptimizationSettings() },
                onCopyStatus = { context.copyToClipboard("Taskshell Service Status", statusText) }
            )

            SecurityCard(
                token = apiToken,
                onCopy = { context.copyToClipboard("Taskshell API Token", apiToken) },
                onCopyHeader = { context.copyToClipboard("Taskshell Authorization", "Authorization: Bearer $apiToken") },
                onRegenerate = {
                    apiToken = tokenManager.regenerateToken()
                    testResultText = "API token regenerated. Update RikkaHub Authorization header."
                }
            )

            TestCommandCard(
                taskId = lastTestTaskId,
                resultText = testResultText,
                onRunTest = { runTestCommand() },
                onCheckStatus = { checkLastTaskStatus() },
                onCheckLogs = { checkLastTaskLogs() },
                onCopyOutput = { context.copyToClipboard("Taskshell Local Test Output", testResultText) }
            )

            AuditCard(
                auditText = auditText,
                onLoad = { loadAuditLogs() },
                onClear = { clearAuditLogs() },
                onCopy = { context.copyToClipboard("Taskshell Audit Logs", auditText) }
            )

            SetupCard(apiToken = apiToken)

            FeatureCard(
                icon = Icons.Outlined.Hub,
                title = "MCP Service",
                description = "监听 127.0.0.1:8765，提供 /health、/tools、/tools/call、/mcp。",
                accent = MaterialTheme.colorScheme.primary
            )
            FeatureCard(
                icon = Icons.Outlined.Terminal,
                title = "Termux Bridge",
                description = "通过 Termux RUN_COMMAND 执行 shell 命令。",
                accent = Color(0xFF2E7D32)
            )
            FeatureCard(
                icon = Icons.Outlined.TaskAlt,
                title = "Task Shell",
                description = "命令进入 tmux 后台任务，输出保存到 ~/.taskshell/tasks/<taskId>/。",
                accent = Color(0xFFEF6C00)
            )
        }
    }
}

@Composable
private fun ServiceControlCard(
    reachable: Boolean,
    statusText: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRefresh: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onCopyStatus: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Service", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (reachable) "Running" else "Stopped or unreachable",
                        color = if (reachable) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                    )
                }
                AssistChip(onClick = onRefresh, label = { Text("Refresh") })
            }

            OutputTextBlock(
                title = "Status output",
                text = statusText,
                onCopy = onCopyStatus,
                textColor = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onStart) {
                    Icon(Icons.Outlined.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Start")
                }
                FilledTonalButton(onClick = onStop) {
                    Icon(Icons.Outlined.StopCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Stop")
                }
            }

            OutlinedButton(onClick = onOpenBatterySettings) {
                Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Open battery optimization settings")
            }
        }
    }
}

@Composable
private fun TestCommandCard(
    taskId: String?,
    resultText: String,
    onRunTest: () -> Unit,
    onCheckStatus: () -> Unit,
    onCheckLogs: () -> Unit,
    onCopyOutput: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Local test", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = "先启动测试任务，再按 taskId 查询状态和日志请求结果。",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRunTest) {
                    Icon(Icons.Outlined.TaskAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Run")
                }
                FilledTonalButton(onClick = onCheckStatus, enabled = !taskId.isNullOrBlank()) {
                    Text("Status")
                }
                FilledTonalButton(onClick = onCheckLogs, enabled = !taskId.isNullOrBlank()) {
                    Text("Logs")
                }
            }
            Text(
                text = "Last taskId: ${taskId ?: "-"}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
            OutputTextBlock(
                title = "Output",
                text = resultText,
                onCopy = onCopyOutput,
                textColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun OutputTextBlock(
    title: String,
    text: String,
    onCopy: () -> Unit,
    textColor: Color
) {
    val scrollState = rememberScrollState()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = onCopy, modifier = Modifier.height(30.dp)) {
                Text("Copy", style = MaterialTheme.typography.labelSmall)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                .verticalScrollbar(scrollState)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            SelectionContainer {
                Text(
                    text = text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(end = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = textColor
                )
            }
        }
    }
}

private fun Modifier.verticalScrollbar(
    scrollState: ScrollState,
    widthDp: Float = 2.5f,
    minThumbHeightPx: Float = 24f
): Modifier = drawWithContent {
    drawContent()
    val maxScroll = scrollState.maxValue
    if (maxScroll <= 0) return@drawWithContent

    val viewportHeight = size.height
    val totalContentHeight = viewportHeight + maxScroll
    val thumbHeight = (viewportHeight * viewportHeight / totalContentHeight).coerceAtLeast(minThumbHeightPx)
    val thumbOffsetY = (scrollState.value.toFloat() / maxScroll.toFloat()) * (viewportHeight - thumbHeight)
    val barWidth = widthDp.dp.toPx()

    drawRoundRect(
        color = Color.Gray.copy(alpha = 0.22f),
        topLeft = Offset(size.width - barWidth, 0f),
        size = Size(barWidth, viewportHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth, barWidth)
    )
    drawRoundRect(
        color = Color.Gray.copy(alpha = 0.75f),
        topLeft = Offset(size.width - barWidth, thumbOffsetY),
        size = Size(barWidth, thumbHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth, barWidth)
    )
}

@Composable
private fun AuditCard(
    auditText: String,
    onLoad: () -> Unit,
    onClear: () -> Unit,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Audit logs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("记录工具调用、命令、taskId、耗时与错误信息。", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onLoad) { Text("Load") }
                OutlinedButton(onClick = onClear) { Text("Clear") }
            }
            OutputTextBlock(
                title = "Logs",
                text = auditText,
                onCopy = onCopy,
                textColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SecurityCard(
    token: String,
    onCopy: () -> Unit,
    onCopyHeader: () -> Unit,
    onRegenerate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("API Token", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            SelectionContainer {
                Text(
                    text = token,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                text = "RikkaHub 请求需带 Authorization: Bearer <token>。/health 不需要 Token。",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onCopy) { Text("Copy token") }
                FilledTonalButton(onClick = onCopyHeader) { Text("Copy header") }
            }
            OutlinedButton(onClick = onRegenerate) {
                Text("Regenerate token")
            }
        }
    }
}

@Composable
private fun SetupCard(apiToken: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Termux setup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = "pkg install tmux\nmkdir -p ~/.termux\nprintf 'allow-external-apps = true\\n' >> ~/.termux/termux.properties",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "完成后重启 Termux。首次 Run 时请授予 Termux RUN_COMMAND 权限。RikkaHub MCP 地址填写：${McpServer.DEFAULT_ENDPOINT}",
                style = MaterialTheme.typography.bodyMedium
            )
            SelectionContainer {
                Text(
                    text = "Authorization: Bearer $apiToken",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun FeatureCard(
    icon: ImageVector,
    title: String,
    description: String,
    accent: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(32.dp)
            )
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun Context.startTaskshellServiceSafely(): String {
    return try {
        val intent = Intent(this, TaskshellForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        "Start requested. Waiting for /health..."
    } catch (throwable: Throwable) {
        "Start failed: ${throwable.message ?: throwable::class.java.simpleName}"
    }
}

private fun Context.stopTaskshellService() {
    stopService(Intent(this, TaskshellForegroundService::class.java))
}

private fun Context.openBatteryOptimizationSettings() {
    runCatching {
        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.recoverCatching {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun Context.copyToClipboard(label: String, text: String) {
    val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
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

private fun checkHealth(): Pair<Boolean, String> {
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

private data class TestCommandStartResult(
    val taskId: String?,
    val displayText: String
)

private fun startTestCommand(token: String): TestCommandStartResult {
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

private fun callTool(token: String, name: String, arguments: JSONObject): String {
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

private const val TERMUX_RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"

@Preview(showBackground = true)
@Composable
private fun TaskshellAppPreview() {
    TaskshellTheme {
        TaskshellApp()
    }
}
