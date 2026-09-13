package com.banksms.expensetracker.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.banksms.expensetracker.data.auth.AuthRepository
import com.banksms.expensetracker.data.auth.BiometricUnlock
import com.banksms.expensetracker.data.auth.PasscodeCrypto
import com.banksms.expensetracker.data.auth.PasscodeLock
import com.banksms.expensetracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val displayName: String = "",
    val email: String = "",
    val isGoogleAccount: Boolean = false,
    val hasPassword: Boolean = false,
    val biometricAvailable: Boolean = false,
    val biometricOn: Boolean = false,
    val passcodeSet: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null
)

/** Self-service account management: identity, password, unlock, wipe. */
class ProfileViewModel(
    private val auth: AuthRepository,
    private val biometricUnlock: BiometricUnlock,
    private val passcodeLock: PasscodeLock,
    private val repository: TransactionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            auth.user.collect { user ->
                val uid = user?.uid
                _state.value = _state.value.copy(
                    displayName = user?.displayName ?: "",
                    email = user?.email ?: "",
                    isGoogleAccount = user?.providerData
                        ?.any { it.providerId == "google.com" } == true,
                    hasPassword = auth.hasPasswordProvider(),
                    biometricOn = uid?.let { biometricUnlock.isEnabledFor(it) } ?: false,
                    passcodeSet = uid?.let { passcodeLock.hasPasscode(it) } ?: false
                )
            }
        }
    }

    fun refreshBiometricAvailability(canAuthenticate: Boolean) {
        _state.value = _state.value.copy(biometricAvailable = canAuthenticate)
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, info = null)
    }

    fun saveDisplayName(name: String) {
        val trimmed = name.trim()
        if (trimmed.length < 2) {
            return update { copy(error = "Name needs at least 2 characters.") }
        }
        viewModelScope.launch {
            update { copy(busy = true, error = null, info = null) }
            auth.updateDisplayName(trimmed)
                // Set directly: profile updates don't re-emit the auth flow.
                .onSuccess { update { copy(displayName = trimmed, info = "Name updated.") } }
                .onFailure { e -> update { copy(error = e.message) } }
            update { copy(busy = false) }
        }
    }

    fun changePassword(current: String, new: String, confirm: String) {
        if (current.isEmpty()) {
            return update { copy(error = "Enter your current password.") }
        }
        if (new.length < 6) {
            return update { copy(error = "The new password needs at least 6 characters.") }
        }
        if (new != confirm) {
            return update { copy(error = "New passwords don't match.") }
        }
        viewModelScope.launch {
            update { copy(busy = true, error = null, info = null) }
            auth.changePassword(current, new)
                .onSuccess { update { copy(info = "Password changed.") } }
                .onFailure { e -> update { copy(error = e.message) } }
            update { copy(busy = false) }
        }
    }

    fun sendPasswordReset() {
        viewModelScope.launch {
            update { copy(busy = true, error = null, info = null) }
            auth.sendPasswordReset()
                .onSuccess { update { copy(info = "Reset link sent — check your inbox.") } }
                .onFailure { e -> update { copy(error = e.message) } }
            update { copy(busy = false) }
        }
    }

    fun setBiometric(on: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        biometricUnlock.setEnabled(uid, on)
        update { copy(biometricOn = on) }
    }

    fun changePasscode(new: String, confirm: String) {
        val uid = auth.currentUser?.uid ?: return
        if (!PasscodeCrypto.isValidFormat(new)) {
            return update { copy(error = "Passcode must be 4 digits.") }
        }
        if (new != confirm) {
            return update { copy(error = "Passcodes don't match.") }
        }
        if (passcodeLock.setPasscode(uid, new)) {
            update { copy(passcodeSet = true, info = "App passcode updated.") }
        } else {
            update { copy(error = "Could not save the passcode.") }
        }
    }

    fun signOut() {
        auth.currentUser?.uid?.let { biometricUnlock.setEnabled(it, false) }
        auth.signOut()
        // AuthGate observes the session and routes to login automatically.
    }

    /**
     * Full goodbye: wipes every cloud collection for this account FIRST
     * (rules forbid touching them once the login is gone), disables
     * biometric unlock, then deletes the Firebase account. AuthGate routes
     * to login when the user disappears.
     */
    fun deleteAccount() {
        viewModelScope.launch {
            update { copy(busy = true, error = null, info = null) }
            try {
                runCatching { repository.clearAllPersistenceFiles() }
                val uid = auth.currentUser?.uid
                auth.deleteAccount()
                    .onSuccess {
                        if (uid != null) {
                            biometricUnlock.setEnabled(uid, false)
                            passcodeLock.clear(uid)
                        }
                    }
                    .onFailure { e -> update { copy(error = e.message) } }
            } finally {
                update { copy(busy = false) }
            }
        }
    }

    private inline fun update(block: ProfileUiState.() -> ProfileUiState) {
        _state.value = _state.value.block()
    }

    class Factory(
        private val auth: AuthRepository,
        private val biometricUnlock: BiometricUnlock,
        private val passcodeLock: PasscodeLock,
        private val repository: TransactionRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProfileViewModel(auth, biometricUnlock, passcodeLock, repository) as T
        }
    }
}
