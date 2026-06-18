# Architecture

Taskshell has five main layers.

## 1. Android UI

`MainActivity.kt` provides:

- foreground service controls;
- API token display/copy;
- local test command controls;
- audit log view;
- fixed-size scrollable output boxes.

## 2. Foreground service

`TaskshellForegroundService` owns the MCP server lifecycle and shows a foreground service notification.

## 3. MCP` implements a lightweight local JSON-RPC HTTP server bound to `127.0.0.1:8765`.

Supported methods include:

- `initialize`
- `ping`
- `notifications/*`
- `tools/list`
- `tools/call`

## 4. Tool registry and task manager

`McpToolRegistry` maps MCP tools to `ShellTaskManager` operations.

`ShellTaskManager` handles:

- safety checks;
- task limits;
- state updates;
- cleanup;
- recovery.

## 5. Termux bridge

`TermuxCommandExecutor` sends commands to Termux through `com.termux.RUN_COMMAND` and `RunCommandService`.

The command is wrapped into a tmux-backed task runner:

```text
RUN_COMMAND -> bash -> tmux new-session -> command.sh
```

Task files live under:

```text
~/.taskshell/tasks/<taskId>/
```
