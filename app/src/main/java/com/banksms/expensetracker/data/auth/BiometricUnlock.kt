package com.banksms.expensetracker.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.core.content.edit

/**
 * App-level biometric gate over the persisted Firebase session.
 *
 * Firebase already keeps the user signed in across restarts; this only
 * records whether the user opted into fingerprint/face unlock, and for
 * which uid (a different account always falls back to full login).
 */
class BiometricUnlock(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True when unlock-by-biometrics may be offered for [uid]. */
    fun isEnabledFor(uid: String): Boolean =
        prefs.getBoolean(KEY_ENABLED, false) && prefs.getString(KEY_UID, null) == uid

    fun setEnabled(uid: String, enabled: Boolean) {
        prefs.edit {
            putBoolean(KEY_ENABLED, enabled)
            if (enabled) putString(KEY_UID, uid) else remove(KEY_UID)
        }
    }

    /** Whether this device can do strong biometric auth right now. */
    fun canAuthenticate(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    private companion object {
        const val PREFS = "biometric_unlock_prefs"
        const val KEY_ENABLED = "enabled"
        const val KEY_UID = "uid"
    }
}
