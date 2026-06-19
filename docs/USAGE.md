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
  "stderr": "",
  "stdoutTruncated": false,
  "stderrTruncated": false,
  "nextActions": ["shell_task_logs"]
}
```

Input options are available for both `shell_exec` and `shell_task_start`:

```json
{
  "command": "cat; echo FOO=$FOO",
  "cwd": "/data/data/com.termux/files/home",
  "env": {
    "FOO": "bar"
  },
  "stdin": "hello from stdin\n",
  "timeoutMillis": 5000,
  "waitMillis": 10000
}
```

For long-running commands, call `shell_task_start`:

```json
{
  "command": "sleep 20 && echo done",
  "cwd": "/data/data/com.termux/files/home",
  "includeCommand": false
}
```

Then use the returned `taskId` with:

- `shell_task_status`
- `shell_task_logs`
- `shell_task_stop`

Example status call:

```json
{
  "taskId": "taskshell_xxxxxxxxxxxxxxxx",
  "includeCommand": false
}
```

Example logs call:

```json
{
  "taskId": "taskshell_xxxxxxxxxxxxxxxx",
  "maxLines": 200,
  "maxBytes": 65536
}
```

`maxBytes` limits each output stream after line tailing. If output is truncated, the result includes `stdoutTruncated` or `stderrTruncated`.

## 5. Privacy and audit behavior

Task summaries and status results do not return full command text by default. Instead, they return command metadata:

```json
{
  "command": null,
  "commandPreview": "sleep 20 && echo done",
  "commandLength": 21,
  "commandSha256": "..."
}
```

Set `includeCommand=true` only when a client really needs the full command.

Audit logs are in-memory and privacy-aware by default:

- full command text is not stored in the audit event;
- audit events store redacted `commandPreview`, `commandLength`, and `commandSha256`;
- result summaries redact common command fields.

Input limits:

| Parameter | Limit |
|---|---|
| `env` | Up to 64 variables; names must match `^[A-Za-z_][A-Za-z0-9_]*$`; each value up to 8192 characters. |
| `stdin` / `input` | Up to 256 KiB. |
| `timeoutMillis` | `1` to `86400000` ms. Requires `timeout` in Termux; install `coreutils` if unavailable. |
| `maxLines` | `1` to `5000`. |
| `maxBytes` | `1024` to `1048576` bytes per output stream. |
| `waitMillis` | `0` to `30000` ms. |

## 6. Output and diagnostics

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

## 7. Check Termux manually

```bash
ls -R ~/.taskshell/tasks
cat ~/.taskshell/tasks/<taskId>/status
cat ~/.taskshell/tasks/<taskId>/exit.code
cat ~/.taskshell/tasks/<taskId>/stdout.log
cat ~/.taskshell/tasks/<taskId>/stderr.log
```
