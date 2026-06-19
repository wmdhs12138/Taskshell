package com.wmdhs.taskshell.task

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicTaskResultsTest {
    @Test
    fun previewRedactsCommonSecretsBeforeTruncation() {
        val preview = "curl -H 'Authorization: Bearer abcdef123456' https://example.com?token=secret123 sk-abcdefghijklmnop ghp_abcdefghijklmnop".preview(240)

        assertTrue(preview.contains("Authorization: Bearer <redacted>"))
        assertTrue(preview.contains("token=<redacted>"))
        assertTrue(preview.contains("sk-<redacted>"))
        assertTrue(preview.contains("gh<redacted>"))
        assertFalse(preview.contains("abcdef123456"))
        assertFalse(preview.contains("secret123"))
    }

    @Test
    fun sha256IsStableAndSensitiveToInput() {
        assertEquals("abc".sha256(), "abc".sha256())
        assertNotEquals("abc".sha256(), "abcd".sha256())
        assertEquals(64, "abc".sha256().length)
    }
}
