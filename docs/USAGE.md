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

Ask your MCP client to call `shell_task_start`:

```json
{
  "command": "pwd; echo hello; date",
  "cwd": "/data/data/com.termux/files/home"
}
```

Then call `shell_task_logs` with the returned taskId.

## 5. Check Termux manually

```bash
ls -R ~/.taskshell/tasks
cat ~/.taskshell/tasks/<taskId>/status
cat ~/.taskshell/tasks/<taskId>/exit.code
cat ~/.taskshell/tasks/<taskId>/stdout.log
cat ~/.taskshell/tasks/<taskId>/stderr.log
```
