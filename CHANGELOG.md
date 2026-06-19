# Changelog

## 1.1.0

Feature release focused on MCP shell tool ergonomics, Ktor/CIO transport, automated tests, and privacy-aware task/audit output.

### Added

- Added structured stdout/stderr log parsing with `maxBytes`, truncation flags, and `nextActions` hints.
- Added command input options for `shell_exec` and `shell_task_start`: `env`, `stdin`/`input`, and `timeoutMillis`.
- Added command privacy fields: redacted `commandPreview`, `commandLength`, and `commandSha256`.
- Added optional `includeCommand` for task start, status, and list results.
- Added Ktor/CIO MCP HTTP server backend.
- Added JVM unit tests for command policy, task log parsing, command preview redaction, and hashing helpers.

### Changed

- Switched the active MCP HTTP backend from the custom socket server to Ktor/CIO.
- Improved MCP tool error semantics so tool failures return `isError=true` instead of surfacing as HTTP 500.
- Improved JSON-RPC protocol error responses for parse errors, invalid requests, and unknown methods.
- Improved MCP input schemas with parameter limits and new input/privacy options.
- Updated README, usage, and architecture documentation for the new tool behavior and test workflow.
- Audit events now store command summaries by default instead of full command text.

### Fixed

- Fixed task log output so normal results return split stdout/stderr rather than mixed metadata and log sections.
- Fixed short command output handling to reuse the same structured log parsing path.
- Fixed failed task reporting to prefer the task exit code over the status/log query command exit code.

## 1.0.1

Maintenance release covering all changes since the `v1.0.0` automated release build.

### Added

- Added `shell_task_debug` for advanced per-task diagnostics when normal task tools fail.
- Added `service_diagnostics` for Taskshell service lifecycle and Termux transport troubleshooting.
- Added persistent service event logging and heartbeat diagnostics for foreground-service stability analysis.
- Added a local client and ViewModel-backed UI state layer to decouple the Compose UI from direct service/tool calls.
- Added reusable Compose UI components for service controls, setup instructions, security/token actions, feature summary, audit logs, test commands, output blocks, and confirmation dialogs.
- Added CORS support for the `x-taskshell-token` authentication header.

### Changed

- Refined default MCP task tool outputs to return concise, stable, task-oriented results instead of Termux transport/debug internals.
- Improved MCP tool descriptions for agent clients and clarified which tools are intended for normal use versus diagnostics.
- Refactored the Taskshell UI architecture from a large `MainActivity` implementation into smaller ViewModel and component modules.
- Improved task concurrency handling and active-task cleanup to reduce stale limiter state.
- Improved background task status/log polling behavior and stale-result messaging.
- Updated README and usage documentation for the new MCP output model and diagnostic-tool split.

### Fixed

- Fixed task concurrency edge cases where finished, failed, or stopped tasks could continue to affect task limits.
- Fixed service lifecycle observability gaps by recording start, stop, failure, and heartbeat-related events.
- Fixed browser/client preflight compatibility for `X-Taskshell-Token` authentication.

## 1.0.0

Initial public self-use/share release.

### Added

- Kotlin + Jetpack Compose Android app.
- Foreground service.
- Local MCP HTTP endpoint.
- API Token authentication.
- Termux RUN_COMMAND bridge.
- tmux-backed shell tasks.
- Task status/logs/stop/list/cleanup/recover tools.
- Audit log tools.
- Basic command safety policy.
- Task concurrency limits.
- Scrollable UI output boxes with copy buttons.
