# Taskshell Usage Guide

## 1. Prepare Termux

```bash
pkg update
pkg install tmux
mkdir -p ~/.termux
printf 'allow-external-apps = true\n' >> ~/.termux/termux.properties
```

Fully restart Termux after editing `termux.properties`.

## 2. Start Taskshell

1. Open Taskshell.
2. Tap **Start**.
3. Grant notification permission if Android asks.
4. Grant Termux RUN_COMMAND permission if Android asks.
5. Confirm the service status is running.

## 3. Configure an MCP client

MCP URL:

```text
http://127.0.0.1:8765/mcp
```

Header:

```http
Authorization: Bearer <token>
```

Use **Copy header** in Taskshell. RikkaHub is one supported client; other Streamable HTTP MCP clients may also work.

## 4. Test command

Ask your MCP client to call `shell_exec` for short commands:

```json
{
  "command": "pwd; echo hello; date",
  "cwd": "/data/data/com.termux/files/home",
  "waitMillis": 10000
}
```

Expected concise result:

```json
{
  "status": "finished",
  "taskId": "taskshell_xxxxxxxxxxxxxxxx",
  "exitCode": 0,
  "stdout": "...",
  "stderr": ""
}
```

For long-running commands, call `shell_task_start`:

```json
{
  "command": "sleep 20 && echo done",
  "cwd": "/data/data/com.termux/files/home"
}
```

Then use the returned `taskId` with:

- `shell_task_status`
- `shell_task_logs`
- `shell_task_stop`

## 5. Output and diagnostics

Normal tools return concise, stable results:

```text
shell_exec
shell_task_start
shell_task_status
shell_task_logs
shell_task_stop
shell_task_list
```

Troubleshooting tools may expose Termux transport details and should be used only when normal tools fail:

```text
shell_task_debug
shell_task_recover
shell_task_cleanup
audit_logs
service_diagnostics
```

## 6. Check Termux manually

```bash
ls -R ~/.taskshell/tasks
cat ~/.taskshell/tasks/<taskId>/status
cat ~/.taskshell/tasks/<taskId>/exit.code
cat ~/.taskshell/tasks/<taskId>/stdout.log
cat ~/.taskshell/tasks/<taskId>/stderr.log
```
