# Contributing

Contributions are welcome.

## Development setup

Requirements:

- JDK 17
- Android SDK
- Gradle

Build:

```bash
gradle assembleDebug
```

## Guidelines

- Keep the MCP server bound to `127.0.0.1` by default.
- Avoid adding dangerous command capabilities without policy checks.
- Keep tool names compatible with function-call clients: `^[a-zA-Z0-9_-]+$`.
- Update README and docs when adding tools or changing behavior.
- Prefer small, focused pull requests.

## Testing checklist

- App starts and foreground notification appears.
- `/health` returns OK.
- An MCP client can connect.
- `shell_task_start` works.
- `shell_task_status` returns final state.
- `shell_task_logs` returns stdout/stderr.
- `shell_task_recover` can recover existing task directories.
