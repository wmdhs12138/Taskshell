package com.wmdhs.taskshell.task

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskLogParserTest {
    @Test
    fun parseKeyValueLinesKeepsValuesAfterFirstEquals() {
        val parsed = "taskId=abc\nstatus=finished\nvalue=a=b=c\nignored\n".parseKeyValueLines()

        assertEquals("abc", parsed["taskId"])
        assertEquals("finished", parsed["status"])
        assertEquals("a=b=c", parsed["value"])
        assertNull(parsed["ignored"])
    }

    @Test
    fun parseTaskLogsSplitsStdoutAndStderr() {
        val raw = "taskId=t1\nstatus=finished\nexitCode=0\n--- stdout ---\nhello\nworld\n--- stderr ---\nwarn\n"

        val parsed = raw.parseTaskLogs()

        assertEquals("hello\nworld", parsed.stdout)
        assertEquals("warn", parsed.stderr)
    }

    @Test
    fun parseTaskLogsFallsBackToWholeTextWhenMarkersMissing() {
        val parsed = "plain output".parseTaskLogs()

        assertEquals("plain output", parsed.stdout)
        assertNull(parsed.stderr)
    }
}
