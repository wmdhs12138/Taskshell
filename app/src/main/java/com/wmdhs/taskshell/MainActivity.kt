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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wmdhs.taskshell.mcp.McpServer
import com.wmdhs.taskshell.service.TaskshellForegroundService
import com.wmdhs.taskshell.ui.components.AuditCard
import com.wmdhs.taskshell.ui.components.ConfirmActionDialog
import com.wmdhs.taskshell.ui.components.FeatureCard
import com.wmdhs.taskshell.ui.components.SecurityCard
import com.wmdhs.taskshell.ui.components.ServiceControlCard
import com.wmdhs.taskshell.ui.components.SetupCard
import com.wmdhs.taskshell.ui.components.TestCommandCard
import com.wmdhs.taskshell.ui.theme.TaskshellTheme

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
fun TaskshellApp(viewModel: TaskshellViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showStopConfirm by remember { mutableStateOf(false) }
    var showRegenerateTokenConfirm by remember { mutableStateOf(false) }
    var showClearAuditConfirm by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(TaskshellTab.Home) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    val termuxPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setTestResultText(
            if (granted) {
                "Termux RUN_COMMAND permission granted. Tap Run again."
            } else {
                "Termux RUN_COMMAND permission denied. Grant it in system settings or reinstall after Termux is installed."
            }
        )
    }

    fun runTestCommand() {
        if (ContextCompat.checkSelfPermission(context, TERMUX_RUN_COMMAND_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            viewModel.setTestResultText("Requesting Termux RUN_COMMAND permission...")
            termuxPermissionLauncher.launch(TERMUX_RUN_COMMAND_PERMISSION)
            return
        }
        viewModel.runTestCommand()
    }

    LaunchedEffect(Unit) {
        viewModel.refreshStatus()
    }

    if (showStopConfirm) {
        ConfirmActionDialog(
            title = "Stop Taskshell service?",
            message = "The local MCP endpoint will become unavailable until you start the service again.",
            confirmText = "Stop",
            onDismiss = { showStopConfirm = false },
            onConfirm = {
                showStopConfirm = false
                context.stopTaskshellService()
                viewModel.markStopRequested()
                viewModel.refreshStatusDelayed()
            }
        )
    }

    if (showRegenerateTokenConfirm) {
        ConfirmActionDialog(
            title = "Regenerate API token?",
            message = "The previous Authorization header will become invalid. Update every MCP client after regeneration.",
            confirmText = "Regenerate",
            onDismiss = { showRegenerateTokenConfirm = false },
            onConfirm = {
                showRegenerateTokenConfirm = false
                viewModel.regenerateToken()
            }
        )
    }

    if (showClearAuditConfirm) {
        ConfirmActionDialog(
            title = "Clear audit logs?",
            message = "This clears the in-memory audit events shown by Taskshell. This action cannot be undone.",
            confirmText = "Clear",
            onDismiss = { showClearAuditConfirm = false },
            onConfirm = {
                showClearAuditConfirm = false
                viewModel.clearAuditLogs()
            }
        )
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

            PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                TaskshellTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.title) }
                    )
                }
            }

            when (selectedTab) {
                TaskshellTab.Home -> {
                    ServiceControlCard(
                        reachable = uiState.serviceReachable,
                        statusText = uiState.statusText,
                        onStart = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            if (ContextCompat.checkSelfPermission(context, TERMUX_RUN_COMMAND_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
                                termuxPermissionLauncher.launch(TERMUX_RUN_COMMAND_PERMISSION)
                            }
                            context.startTaskshellServiceSafely()
                            viewModel.markStartRequested()
                            viewModel.refreshStatusDelayed()
                        },
                        onStop = { showStopConfirm = true },
                        onRefresh = { viewModel.refreshStatus() },
                        onOpenBatterySettings = { context.openBatteryOptimizationSettings() },
                        onCopyStatus = { context.copyToClipboard("Taskshell Service Status", uiState.statusText) }
                    )

                    SecurityCard(
                        token = uiState.apiToken,
                        onCopy = { context.copyToClipboard("Taskshell API Token", uiState.apiToken) },
                        onCopyHeader = { context.copyToClipboard("Taskshell Authorization", "Authorization: Bearer ${uiState.apiToken}") },
                        onRegenerate = { showRegenerateTokenConfirm = true }
                    )

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

                TaskshellTab.Test -> {
                    TestCommandCard(
                        taskId = uiState.lastTestTaskId,
                        resultText = uiState.testResultText,
                        onRunTest = { runTestCommand() },
                        onCheckStatus = { viewModel.checkLastTaskStatus() },
                        onCheckLogs = { viewModel.checkLastTaskLogs() },
                        onCopyOutput = { context.copyToClipboard("Taskshell Local Test Output", uiState.testResultText) }
                    )
                }

                TaskshellTab.Audit -> {
                    AuditCard(
                        auditText = uiState.auditText,
                        onLoad = { viewModel.loadAuditLogs() },
                        onClear = { showClearAuditConfirm = true },
                        onCopy = { context.copyToClipboard("Taskshell Audit Logs", uiState.auditText) }
                    )
                }

                TaskshellTab.Setup -> {
                    SetupCard(
                        apiToken = uiState.apiToken,
                        setupCommands = TERMUX_SETUP_COMMANDS,
                        onCopySetupCommands = { context.copyToClipboard("Taskshell Termux setup", TERMUX_SETUP_COMMANDS) },
                        onCopyHeader = { context.copyToClipboard("Taskshell Authorization", "Authorization: Bearer ${uiState.apiToken}") }
                    )
                }
            }

        }
    }
}

private enum class TaskshellTab(val title: String) {
    Home("Home"),
    Test("Test"),
    Audit("Audit"),
    Setup("Setup")
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

private const val TERMUX_RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"
private const val TERMUX_SETUP_COMMANDS = "pkg install tmux\nmkdir -p ~/.termux\nprintf 'allow-external-apps = true\\n' >> ~/.termux/termux.properties"

@Preview(showBackground = true)
@Composable
private fun TaskshellAppPreview() {
    TaskshellTheme {
        TaskshellApp()
    }
}
