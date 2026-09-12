package com.banksms.expensetracker.ui.screens.auth

import android.util.Patterns
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.banksms.expensetracker.R
import com.banksms.expensetracker.data.auth.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val displayName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val busy: Boolean = false,
    val googleBusy: Boolean = false,
    val error: String? = null
)

/** Login / registration state. Emits the fresh uid on success for the biometric offer. */
class AuthViewModel(
    private val auth: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private val _freshLoginUid = MutableStateFlow<String?>(null)
    val freshLoginUid: StateFlow<String?> = _freshLoginUid.asStateFlow()

    fun setDisplayName(v: String) = update { copy(displayName = v, error = null) }
    fun setEmail(v: String) = update { copy(email = v, error = null) }
    fun setPassword(v: String) = update { copy(password = v, error = null) }
    fun setConfirmPassword(v: String) = update { copy(confirmPassword = v, error = null) }
    fun clearError() = update { copy(error = null) }
    fun consumeFreshLogin() { _freshLoginUid.value = null }

    fun login() {
        val s = _state.value
        val emailError = validateEmail(s.email)
        if (emailError != null) return update { copy(error = emailError) }
        if (s.password.isEmpty()) return update { copy(error = "Enter your password.") }
        viewModelScope.launch {
            update { copy(busy = true, error = null) }
            val result = auth.login(s.email, s.password)
            result
                .onSuccess { _freshLoginUid.value = auth.currentUser?.uid }
                .onFailure { e -> update { copy(error = e.message) } }
            update { copy(busy = false) }
        }
    }

    fun register() {
        val s = _state.value
        if (s.displayName.trim().length < 2) {
            return update { copy(error = "Tell us your name (2+ characters).") }
        }
        val emailError = validateEmail(s.email)
        if (emailError != null) return update { copy(error = emailError) }
        if (s.password.length < 6) {
            return update { copy(error = "Password needs at least 6 characters.") }
        }
        if (s.password != s.confirmPassword) {
            return update { copy(error = "Passwords don't match.") }
        }
        viewModelScope.launch {
            update { copy(busy = true, error = null) }
            val result = auth.register(s.displayName, s.email, s.password)
            result
                .onSuccess { _freshLoginUid.value = auth.currentUser?.uid }
                .onFailure { e -> update { copy(error = e.message) } }
            update { copy(busy = false) }
        }
    }

    fun signInWithGoogle(activity: FragmentActivity) {
        if (_state.value.googleBusy) return
        viewModelScope.launch {
            update { copy(googleBusy = true, error = null) }
            try {
                val serverClientId = activity.getString(R.string.default_web_client_id)
                val result = auth.signInWithGoogle(activity, serverClientId)
                result
                    .onSuccess { _freshLoginUid.value = auth.currentUser?.uid }
                    .onFailure { e ->
                        // Dismissed sheet: stay silent, it's not an error.
                        if (e !is AuthRepository.GoogleSignInCancelled) {
                            update { copy(error = e.message) }
                        }
                    }
            } catch (_: Exception) {
                update { copy(error = "Google sign-in isn't set up on this build yet.") }
            }
            update { copy(googleBusy = false) }
        }
    }

    private fun validateEmail(email: String): String? {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) return "Enter your email address."
        if (!Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()) {
            return "That email address doesn't look valid."
        }
        return null
    }

    private inline fun update(block: AuthUiState.() -> AuthUiState) {
        _state.value = _state.value.block()
    }

    class Factory(private val auth: AuthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AuthViewModel(auth) as T
        }
    }
}
