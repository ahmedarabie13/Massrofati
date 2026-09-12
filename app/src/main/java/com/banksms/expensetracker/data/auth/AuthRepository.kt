package com.banksms.expensetracker.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Thin wrapper over FirebaseAuth. The SDK persists the session itself, so
 * a signed-in user survives process death — the biometric gate in
 * [BiometricUnlock] only guards UI access to that persisted session.
 */
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    /**
     * Emits the current user on every sign-in / sign-out / token refresh.
     * Conflated: auth state is latest-wins, and a rendezvous channel could
     * drop a rapid sign-out→sign-in transition (leaving the gate stuck on
     * the previous account's session).
     */
    val user: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.buffer(capacity = Channel.CONFLATED)

    val currentUser: FirebaseUser? get() = auth.currentUser

    suspend fun register(displayName: String, email: String, password: String): Result<Unit> =
        runCatching {
            val credential = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            credential.user?.updateProfile(
                UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName.trim())
                    .build()
            )?.await()
            Unit
        }.mapFailure(::friendlyMessage)

    suspend fun login(email: String, password: String): Result<Unit> =
        runCatching {
            auth.signInWithEmailAndPassword(email.trim(), password).await()
            Unit
        }.mapFailure(::friendlyMessage)

    /**
     * Google sign-in via Credential Manager. Throws [GoogleSignInCancelled]
     * when the user dismisses the sheet (callers should stay silent then).
     */
    suspend fun signInWithGoogle(context: Context, serverClientId: String): Result<Unit> {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val result = CredentialManager.create(context).getCredential(context, request)
            val googleCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
            runCatching {
                auth.signInWithCredential(
                    GoogleAuthProvider.getCredential(googleCredential.idToken, null)
                ).await()
                Unit
            }.mapFailure(::friendlyMessage)
        } catch (e: GetCredentialCancellationException) {
            Result.failure(GoogleSignInCancelled(e))
        } catch (e: Exception) {
            Result.failure(Exception(friendlyMessage(e)))
        }
    }

    fun signOut() {
        auth.signOut()
    }

    /** True for password accounts (Google-only accounts can't change a password). */
    fun hasPasswordProvider(): Boolean =
        currentUser?.providerData?.any { it.providerId == EmailAuthProvider.PROVIDER_ID } == true

    suspend fun updateDisplayName(name: String): Result<Unit> {
        val user = currentUser ?: return Result.failure(Exception("You're not signed in."))
        return runCatching {
            user.updateProfile(
                UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()
            ).await()
            // Refresh the cached user so new name shows immediately.
            auth.currentUser?.reload()?.await()
            Unit
        }.mapFailure(::friendlyMessage)
    }

    /**
     * Password change for email accounts. Re-authenticates with the current
     * password first; surfaces a clear message when the session is too old
     * (user must log in again).
     */
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        val user = currentUser ?: return Result.failure(Exception("You're not signed in."))
        val email = user.email
            ?: return Result.failure(Exception("This account has no email to verify."))
        return runCatching {
            val credential = EmailAuthProvider.getCredential(email, currentPassword)
            user.reauthenticate(credential).await()
            user.updatePassword(newPassword).await()
            Unit
        }.mapFailure(::friendlyMessage)
    }

    suspend fun sendPasswordReset(): Result<Unit> {
        val email = currentUser?.email
            ?: return Result.failure(Exception("This account has no email address."))
        return runCatching {
            auth.sendPasswordResetEmail(email).await()
            Unit
        }.mapFailure(::friendlyMessage)
    }

    /**
     * Deletes the Firebase account. Callers should wipe the user's cloud
     * data first (rules forbid touching it once the account is gone).
     */
    suspend fun deleteAccount(): Result<Unit> {
        val user = currentUser ?: return Result.failure(Exception("You're not signed in."))
        return runCatching {
            user.delete().await()
            Unit
        }.mapFailure(::friendlyMessage)
    }

    private fun friendlyMessage(e: Throwable): String = when (e) {
        is FirebaseAuthWeakPasswordException -> "Password is too weak — use at least 6 characters."
        is FirebaseAuthUserCollisionException -> "An account with this email already exists. Try logging in."
        is FirebaseAuthInvalidUserException -> "No account found for this email."
        is FirebaseAuthRecentLoginRequiredException ->
            "For safety, log out and log back in, then try again."
        is FirebaseAuthInvalidCredentialsException ->
            if (e.errorCode == "ERROR_WRONG_PASSWORD") "Wrong password. Try again."
            else "That email address doesn't look valid."
        else -> e.localizedMessage?.takeIf { it.isNotBlank() } ?: "Authentication failed. Try again."
    }

    private inline fun <T> Result<T>.mapFailure(map: (Throwable) -> String): Result<T> =
        recoverCatching { throw Exception(map(it)) }

    /** Marker: user dismissed the Google account sheet — not an error. */
    class GoogleSignInCancelled(cause: Throwable) : Exception(cause)
}
