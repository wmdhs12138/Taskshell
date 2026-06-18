package com.wmdhs.taskshell.security

import android.content.Context
import java.security.SecureRandom

class TokenManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getOrCreateToken(): String {
        val existing = prefs.getString(KEY_TOKEN, null)
        if (!existing.isNullOrBlank()) return existing
        return regenerateToken()
    }

    fun regenerateToken(): String {
        val token = generateToken()
        prefs.edit().putString(KEY_TOKEN, token).apply()
        return token
    }

    fun isValid(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        return constantTimeEquals(token, getOrCreateToken())
    }

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes.joinToString(separator = "") { "%02x".format(it) }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        val aBytes = a.toByteArray()
        val bBytes = b.toByteArray()
        var diff = aBytes.size xor bBytes.size
        val max = maxOf(aBytes.size, bBytes.size)
        for (i in 0 until max) {
            val av = if (i < aBytes.size) aBytes[i].toInt() else 0
            val bv = if (i < bBytes.size) bBytes[i].toInt() else 0
            diff = diff or (av xor bv)
        }
        return diff == 0
    }

    companion object {
        private const val PREFS_NAME = "taskshell_security"
        private const val KEY_TOKEN = "api_token"
        private val random = SecureRandom()
    }
}
