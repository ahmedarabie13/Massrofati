package com.banksms.expensetracker.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.security.MessageDigest
import java.util.UUID

/**
 * Pure, JVM-testable passcode helpers: format rules and salted hashing.
 * The 4-digit code is a front-door guard (used when biometrics are off),
 * never stored in plain text — only a salted SHA-256 hash per uid.
 */
object PasscodeCrypto {

    fun isValidFormat(code: String): Boolean =
        code.length == 4 && code.all { it.isDigit() }

    fun generateSalt(): String = UUID.randomUUID().toString()

    fun hash(salt: String, code: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$salt:$code".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

/**
 * Per-account 4-digit passcode gate. Backs the lock screen when fingerprint
 * unlock is disabled or unavailable; every account registers one on its
 * first successful login.
 */
class PasscodeLock(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True once [uid] has registered a passcode. */
    fun hasPasscode(uid: String): Boolean =
        prefs.contains(keyHash(uid))

    /** Stores [code] for [uid]. Returns false when the format is invalid. */
    fun setPasscode(uid: String, code: String): Boolean {
        if (!PasscodeCrypto.isValidFormat(code)) return false
        val salt = PasscodeCrypto.generateSalt()
        prefs.edit {
            putString(keySalt(uid), salt)
            putString(keyHash(uid), PasscodeCrypto.hash(salt, code))
        }
        return true
    }

    /** True when [code] matches the stored hash for [uid]. */
    fun verify(uid: String, code: String): Boolean {
        if (!PasscodeCrypto.isValidFormat(code)) return false
        val salt = prefs.getString(keySalt(uid), null) ?: return false
        val expected = prefs.getString(keyHash(uid), null) ?: return false
        val actual = PasscodeCrypto.hash(salt, code)
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            actual.toByteArray(Charsets.UTF_8)
        )
    }

    /** Removes the passcode for [uid] (account deletion). */
    fun clear(uid: String) {
        prefs.edit {
            remove(keySalt(uid))
            remove(keyHash(uid))
        }
    }

    private fun keySalt(uid: String) = "salt_$uid"
    private fun keyHash(uid: String) = "hash_$uid"

    private companion object {
        const val PREFS = "passcode_lock_prefs"
    }
}
