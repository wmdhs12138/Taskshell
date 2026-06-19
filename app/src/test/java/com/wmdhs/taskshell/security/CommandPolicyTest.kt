package com.wmdhs.taskshell.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandPolicyTest {
    private val policy = CommandPolicy()

    @Test
    fun allowsSafeCommandInTermuxHome() {
        val result = policy.validate("echo hello", "/data/data/com.termux/files/home/project")

        assertTrue(result.allowed)
    }

    @Test
    fun blocksBlankCommand() {
        val result = policy.validate("   ", "/data/data/com.termux/files/home")

        assertFalse(result.allowed)
    }

    @Test
    fun blocksWorkingDirectoryOutsideAllowList() {
        val result = policy.validate("echo hello", "/sdcard")

        assertFalse(result.allowed)
    }

    @Test
    fun blocksHighRiskCommandPattern() {
        val result = policy.validate("rm -rf /", "/data/data/com.termux/files/home")

        assertFalse(result.allowed)
    }
}
