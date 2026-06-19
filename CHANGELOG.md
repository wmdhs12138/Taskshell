# Changelog

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
