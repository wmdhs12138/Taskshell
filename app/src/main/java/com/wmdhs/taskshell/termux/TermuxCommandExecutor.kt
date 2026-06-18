package com.wmdhs.taskshell.termux

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.UUID

class TermuxCommandExecutor(
    private val context: Context
) {
    fun buildStartTaskScript(taskId: String, command: String, workingDirectory: String?): String {
        val safeTaskId = requireSafeTaskId(taskId)
        val taskDir = "\$HOME/.taskshell/tasks/$safeTaskId"
        val cwdLine = workingDirectory?.let { "cd ${shellQuote(it)}" } ?: "cd \$HOME"
        val commandLiteral = shellQuote(command)

        return """
            set -eu
            TASK_ID=${shellQuote(safeTaskId)}
            TASK_DIR="$taskDir"
            mkdir -p "${'$'}TASK_DIR"
            cat > "${'$'}TASK_DIR/command.sh" <<'TASKSHELL_COMMAND_EOF'
            #!/data/data/com.termux/files/usr/bin/bash
            set +e
            TASK_DIR="$taskDir"
            mkdir -p "${'$'}TASK_DIR"
            date +%s > "${'$'}TASK_DIR/started_at"
            echo running > "${'$'}TASK_DIR/status"
            $cwdLine
            CWD_CODE=${'$'}?
            if [ "${'$'}CWD_CODE" -ne 0 ]; then
              echo "Failed to enter working directory" > "${'$'}TASK_DIR/stderr.log"
              echo "${'$'}CWD_CODE" > "${'$'}TASK_DIR/exit.code"
              date +%s > "${'$'}TASK_DIR/ended_at"
              echo failed > "${'$'}TASK_DIR/status"
              exit "${'$'}CWD_CODE"
            fi
            bash -lc $commandLiteral > "${'$'}TASK_DIR/stdout.log" 2> "${'$'}TASK_DIR/stderr.log"
            CODE=${'$'}?
            echo "${'$'}CODE" > "${'$'}TASK_DIR/exit.code"
            date +%s > "${'$'}TASK_DIR/ended_at"
            if [ "${'$'}CODE" -eq 0 ]; then
              echo finished > "${'$'}TASK_DIR/status"
            else
              echo failed > "${'$'}TASK_DIR/status"
            fi
            exit "${'$'}CODE"
            TASKSHELL_COMMAND_EOF
            chmod +x "${'$'}TASK_DIR/command.sh"
            echo queued > "${'$'}TASK_DIR/status"
            tmux new-session -d -s "${'$'}TASK_ID" "bash '${'$'}TASK_DIR/command.sh'"
        """.trimIndent().normalizeShellScript()
    }

    fun buildStatusScript(taskId: String): String {
        val safeTaskId = requireSafeTaskId(taskId)
        return """
            TASK_DIR="${'$'}HOME/.taskshell/tasks/$safeTaskId"
            if tmux has-session -t ${shellQuote(safeTaskId)} 2>/dev/null; then RUNNING=true; else RUNNING=false; fi
            STATUS="unknown"
            EXIT_CODE=""
            [ -f "${'$'}TASK_DIR/status" ] && STATUS="${'$'}(cat "${'$'}TASK_DIR/status")"
            [ -f "${'$'}TASK_DIR/exit.code" ] && EXIT_CODE="${'$'}(cat "${'$'}TASK_DIR/exit.code")"
            printf 'taskId=%s\nrunning=%s\nstatus=%s\nexitCode=%s\n' ${shellQuote(safeTaskId)} "${'$'}RUNNING" "${'$'}STATUS" "${'$'}EXIT_CODE"
        """.trimIndent().normalizeShellScript()
    }

    fun buildLogsScript(taskId: String, maxLines: Int): String {
        val safeTaskId = requireSafeTaskId(taskId)
        val lines = maxLines.coerceIn(1, 5000)
        return """
            TASK_DIR="${'$'}HOME/.taskshell/tasks/$safeTaskId"
            STATUS=unknown
            EXIT_CODE=
            [ -f "${'$'}TASK_DIR/status" ] && STATUS="${'$'}(cat "${'$'}TASK_DIR/status")"
            [ -f "${'$'}TASK_DIR/exit.code" ] && EXIT_CODE="${'$'}(cat "${'$'}TASK_DIR/exit.code")"
            echo "taskId=$safeTaskId"
            echo "status=${'$'}STATUS"
            echo "exitCode=${'$'}EXIT_CODE"
            echo '--- stdout ---'
            [ -f "${'$'}TASK_DIR/stdout.log" ] && tail -n $lines "${'$'}TASK_DIR/stdout.log" || true
            echo '--- stderr ---'
            [ -f "${'$'}TASK_DIR/stderr.log" ] && tail -n $lines "${'$'}TASK_DIR/stderr.log" || true
        """.trimIndent().normalizeShellScript()
    }

    fun buildStopScript(taskId: String): String {
        val safeTaskId = requireSafeTaskId(taskId)
        return """
            TASK_DIR="${'$'}HOME/.taskshell/tasks/$safeTaskId"
            tmux kill-session -t ${shellQuote(safeTaskId)} 2>/dev/null || true
            mkdir -p "${'$'}TASK_DIR"
            date +%s > "${'$'}TASK_DIR/ended_at"
            echo stopped > "${'$'}TASK_DIR/status"
        """.trimIndent().normalizeShellScript()
    }

    fun buildCleanupScript(olderThanHours: Int, keepLatest: Int, dryRun: Boolean): String {
        val hours = olderThanHours.coerceIn(1, 24 * 365)
        val keep = keepLatest.coerceIn(0, 500)
        val dryRunValue = if (dryRun) "true" else "false"
        return """
            BASE_DIR="${'$'}HOME/.taskshell/tasks"
            NOW="${'$'}(date +%s)"
            OLDER_THAN_SECONDS=$hours
            OLDER_THAN_SECONDS="${'$'}((OLDER_THAN_SECONDS * 3600))"
            KEEP_LATEST=$keep
            DRY_RUN=$dryRunValue
            mkdir -p "${'$'}BASE_DIR"
            COUNT=0
            REMOVED=0
            KEPT=0
            echo "baseDir=${'$'}BASE_DIR"
            echo "olderThanHours=$hours"
            echo "keepLatest=$keep"
            echo "dryRun=${'$'}DRY_RUN"
            find "${'$'}BASE_DIR" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %p\n' 2>/dev/null | sort -nr | while read -r MTIME DIR; do
              COUNT="${'$'}((COUNT + 1))"
              if [ "${'$'}COUNT" -le "${'$'}KEEP_LATEST" ]; then
                echo "keep_latest=${'$'}DIR"
                continue
              fi
              ENDED_AT=""
              [ -f "${'$'}DIR/ended_at" ] && ENDED_AT="${'$'}(cat "${'$'}DIR/ended_at" 2>/dev/null || true)"
              STATUS=""
              [ -f "${'$'}DIR/status" ] && STATUS="${'$'}(cat "${'$'}DIR/status" 2>/dev/null || true)"
              if [ -z "${'$'}ENDED_AT" ]; then
                echo "skip_no_ended_at=${'$'}DIR status=${'$'}STATUS"
                continue
              fi
              AGE="${'$'}((NOW - ENDED_AT))"
              if [ "${'$'}AGE" -lt "${'$'}OLDER_THAN_SECONDS" ]; then
                echo "keep_recent=${'$'}DIR ageSeconds=${'$'}AGE status=${'$'}STATUS"
                continue
              fi
              case "${'$'}STATUS" in
                finished|failed|stopped)
                  if [ "${'$'}DRY_RUN" = "true" ]; then
                    echo "would_remove=${'$'}DIR ageSeconds=${'$'}AGE status=${'$'}STATUS"
                  else
                    rm -rf -- "${'$'}DIR"
                    echo "removed=${'$'}DIR ageSeconds=${'$'}AGE status=${'$'}STATUS"
                  fi
                  ;;
                *)
                  echo "skip_active_or_unknown=${'$'}DIR status=${'$'}STATUS"
                  ;;
              esac
            done
        """.trimIndent().normalizeShellScript()
    }

    fun buildRecoverScript(): String {
        return """
            BASE_DIR="${'$'}HOME/.taskshell/tasks"
            mkdir -p "${'$'}BASE_DIR"
            find "${'$'}BASE_DIR" -mindepth 1 -maxdepth 1 -type d | sort | while read -r DIR; do
              TASK_ID="${'$'}(basename "${'$'}DIR")"
              STATUS="unknown"
              EXIT_CODE=""
              STARTED_AT=""
              ENDED_AT=""
              RUNNING=false
              [ -f "${'$'}DIR/status" ] && STATUS="${'$'}(cat "${'$'}DIR/status" 2>/dev/null || true)"
              [ -f "${'$'}DIR/exit.code" ] && EXIT_CODE="${'$'}(cat "${'$'}DIR/exit.code" 2>/dev/null || true)"
              [ -f "${'$'}DIR/started_at" ] && STARTED_AT="${'$'}(cat "${'$'}DIR/started_at" 2>/dev/null || true)"
              [ -f "${'$'}DIR/ended_at" ] && ENDED_AT="${'$'}(cat "${'$'}DIR/ended_at" 2>/dev/null || true)"
              if tmux has-session -t "${'$'}TASK_ID" 2>/dev/null; then RUNNING=true; fi
              printf 'taskId=%s	status=%s	exitCode=%s	startedAt=%s	endedAt=%s	running=%s
' "${'$'}TASK_ID" "${'$'}STATUS" "${'$'}EXIT_CODE" "${'$'}STARTED_AT" "${'$'}ENDED_AT" "${'$'}RUNNING"
            done
        """.trimIndent().normalizeShellScript()
    }

    fun recoverTasks(): TermuxCommandResult = runBashScript(buildRecoverScript())

    fun startTask(taskId: String, command: String, workingDirectory: String?): TermuxCommandResult =
        runBashScript(buildStartTaskScript(taskId, command, workingDirectory))

    fun statusTask(taskId: String): TermuxCommandResult = runBashScript(buildStatusScript(taskId))

    fun logsTask(taskId: String, maxLines: Int): TermuxCommandResult = runBashScript(buildLogsScript(taskId, maxLines))

    fun stopTask(taskId: String): TermuxCommandResult = runBashScript(buildStopScript(taskId))

    fun cleanupTasks(olderThanHours: Int, keepLatest: Int, dryRun: Boolean): TermuxCommandResult =
        runBashScript(buildCleanupScript(olderThanHours, keepLatest, dryRun))

    private fun runBashScript(script: String): TermuxCommandResult {
        val requestId = newRequestId()
        val callbackIntent = Intent(context, TermuxRunCommandResultReceiver::class.java).apply {
            data = Uri.parse("taskshell://termux-result?requestId=$requestId")
            putExtra(TermuxRunCommandResultReceiver.EXTRA_TASKSHELL_REQUEST_ID, requestId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestId.hashCode(),
            callbackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val intent = Intent(ACTION_RUN_COMMAND).apply {
            setClassName(TERMUX_PACKAGE, TERMUX_RUN_COMMAND_SERVICE)
            putExtra(EXTRA_COMMAND_PATH, TERMUX_BASH_PATH)
            putExtra(EXTRA_ARGUMENTS, arrayOf("-lc", script))
            putExtra(EXTRA_WORKDIR, TERMUX_HOME_PATH)
            putExtra(EXTRA_BACKGROUND, true)
            putExtra(EXTRA_SESSION_ACTION, "0")
            putExtra(EXTRA_PENDING_INTENT, pendingIntent)
        }

        return try {
            val component = context.startService(intent)
            TermuxCommandResult(
                accepted = component != null,
                transport = "termux-run-command-service",
                requestId = requestId,
                command = script,
                error = if (component == null) "Termux RunCommandService was not started. Check Termux installation and allow-external-apps." else null
            )
        } catch (throwable: Throwable) {
            TermuxCommandResult(false, "termux-run-command-service", requestId, script, throwable.message ?: throwable::class.java.name)
        }
    }

    private fun requireSafeTaskId(taskId: String): String {
        require(Regex("^[a-zA-Z0-9_.-]{1,80}$").matches(taskId)) { "Invalid taskId" }
        return taskId
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun newRequestId(): String = "req_" + UUID.randomUUID().toString().replace("-", "").take(16)

    private fun String.normalizeShellScript(): String = lineSequence()
        .map { it.trimStart() }
        .filter { it.isNotBlank() }
        .joinToString("\n")

    companion object {
        private const val TERMUX_PACKAGE = "com.termux"
        private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
        private const val TERMUX_RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
        private const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
        private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
        private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
        private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
        private const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
        private const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"
        private const val TERMUX_BASH_PATH = "/data/data/com.termux/files/usr/bin/bash"
        private const val TERMUX_HOME_PATH = "/data/data/com.termux/files/home"
    }
}

data class TermuxCommandResult(
    val accepted: Boolean,
    val transport: String,
    val requestId: String,
    val command: String,
    val error: String? = null
) {
    val callbackResult: TermuxRunCommandCallbackResult?
        get() = TermuxRunCommandResultStore.get(requestId)

    fun awaitCallback(timeoutMillis: Long): TermuxRunCommandCallbackResult? {
        return TermuxRunCommandResultStore.await(requestId, timeoutMillis)
    }
}
