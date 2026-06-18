# Security Policy

Taskshell is powerful because it allows MCP clients to execute shell commands in Termux.

## Supported security measures

- Local-only HTTP binding: `127.0.0.1`.
- API Token authorization.
- Basic command policy.
- Working directory restriction.
- Concurrent task limits.
- Audit logs.

## Important warnings

Taskshell is not a sandbox. If an MCP client can access Taskshell and has a valid token, it can request shell command execution within the configured policy.

Do not expose Taskshell directly to the public Internet.

## Recommended deployment

- Keep- Regenerate the token if it may have leaked.

## Reporting vulnerabilities

Until a formal security contact is published, please open a private report or contact the maintainer directly.
Do not publish exploitable command execution bypasses publicly before giving the maintainer time to respond.
