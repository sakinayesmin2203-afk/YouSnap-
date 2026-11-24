package com.example.ui.utils

import java.security.MessageDigest
import java.security.SecureRandom

object SecurityUtils {
    private const val HASH_PREFIX = "sha256$"

    fun hashPin(pin: String): String {
        val clean = pin.trim()
        if (clean.isEmpty()) return ""
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val saltHex = salt.toHex()
        val digestHex = sha256Hex("$saltHex:$clean")
        return "$HASH_PREFIX$saltHex$$digestHex"
    }

    fun isHashedPin(stored: String): Boolean {
        return stored.startsWith(HASH_PREFIX)
    }

    fun verifyPin(inputPin: String, storedValue: String): Boolean {
        val cleanInput = inputPin.trim()
        val cleanStored = storedValue.trim()
        if (cleanInput.isEmpty() || cleanStored.isEmpty()) return false

        if (cleanStored.startsWith(HASH_PREFIX)) {
            val parts = cleanStored.removePrefix(HASH_PREFIX).split('$')
            if (parts.size != 2) return false
            val saltHex = parts[0]
            val expectedHashHex = parts[1]
            val actualHashHex = sha256Hex("$saltHex:$cleanInput")
            return MessageDigest.isEqual(
                actualHashHex.toByteArray(Charsets.UTF_8),
                expectedHashHex.toByteArray(Charsets.UTF_8)
            )
        }

        // Legacy plaintext fallback so existing accounts still unlock before migration
        return MessageDigest.isEqual(
            cleanInput.toByteArray(Charsets.UTF_8),
            cleanStored.toByteArray(Charsets.UTF_8)
        )
    }

    fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        return bytes.toHex()
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}
