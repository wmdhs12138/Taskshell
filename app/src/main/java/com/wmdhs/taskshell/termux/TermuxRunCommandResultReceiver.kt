package com.wmdhs.taskshell.termux

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import java.time.Instant

class TermuxRunCommandResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getStringExtra(EXTRA_TASKSHELL_REQUEST_ID)
            ?: intent.data?.getQueryParameter("requestId")
            ?: return
        val extras = intent.extras.toFlatStringMap()
        TermuxRunCommandResultStore.put(
            TermuxRunCommandCallbackResult(
                requestId = requestId,
                receivedAt = Instant.now(),
                resultCode = extras["resultCode"]?.toIntOrNull() ?: extras["exitCode"]?.toIntOrNull(),
                extras = extras,
                rawExtras = extras.entries.joinToString("\n") { (key, value) -> "$key=$value" }
            )
        )
    }

    private fun Bundle?.toFlatStringMap(): Map<String, String?> {
        if (this == null) return emptyMap()
        val map = linkedMapOf<String, String?>()
        flattenBundle(prefix = null, bundle = this, out = map)
        return map
    }

    private fun flattenBundle(prefix: String?, bundle: Bundle, out: MutableMap<String, String?>) {
        for (key in bundle.keySet()) {
            @Suppress("DEPRECATION")
            val value = bundle.get(key)
            val fullKey = if (prefix == null) key else "$prefix.$key"
            when (value) {
                is Bundle -> {
                    out[key] = value.keySet().joinToString(prefix = "{", postfix = "}") { nestedKey ->
                        @Suppress("DEPRECATION")
                        "$nestedKey=${value.get(nestedKey)}"
                    }
                    out[fullKey] = out[key]
                    flattenBundle(fullKey, value, out)
                    // Termux puts stdout/stderr/exitCode inside a result Bundle.
                    // Mirror common nested keys to top-level aliases for easier parsing.
                    if (key == "result") {
                        copyIfPresent(out, "result.stdout", "stdout")
                        copyIfPresent(out, "result.stderr", "stderr")
                        copyIfPresent(out, "result.exitCode", "exitCode")
                        copyIfPresent(out, "result.errmsg", "errmsg")
                    }
                }
                is Array<*> -> out[fullKey] = value.joinToString(prefix = "[", postfix = "]")
                else -> out[fullKey] = value?.toString()
            }
        }
    }

    private fun copyIfPresent(out: MutableMap<String, String?>, from: String, to: String) {
        if (!out[from].isNullOrBlank()) out[to] = out[from]
    }

    companion object {
        const val EXTRA_TASKSHELL_REQUEST_ID = "com.wmdhs.taskshell.extra.REQUEST_ID"
    }
}
