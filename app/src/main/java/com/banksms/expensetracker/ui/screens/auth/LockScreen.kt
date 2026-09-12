package com.banksms.expensetracker.ui.screens.auth

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.banksms.expensetracker.ui.theme.DribbblePurple

/**
 * Shown on cold start when a session exists and the user opted into
 * biometric unlock. Success reveals the app; "use password" signs out to
 * the login screen (the SDK session stays revoked only via sign-out).
 */
@Composable
fun LockScreen(
    displayName: String,
    onUnlocked: () -> Unit,
    onUsePasswordInstead: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var failedMessage by remember { mutableStateOf<String?>(null) }
    var autoPrompted by remember { mutableStateOf(false) }

    fun launchPrompt() {
        val fragmentActivity = activity ?: return
        val executor = ContextCompat.getMainExecutor(fragmentActivity)
        val prompt = BiometricPrompt(
            fragmentActivity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    onUnlocked()
                }

                override fun onAuthenticationFailed() {
                    failedMessage = "Not recognized — try again."
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // User pressed the negative button or dismissed: don't loop.
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        failedMessage = errString.toString()
                    }
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Massrofati")
                .setSubtitle("Confirm it's you to continue")
                .setAllowedAuthenticators(BIOMETRIC_STRONG)
                .setNegativeButtonText("Use password instead")
                .build()
        )
    }

    // Android delivers the negative button as ERROR_NEGATIVE_BUTTON; offer
    // the same escape hatch as an explicit button below.
    LaunchedEffect(Unit) {
        if (!autoPrompted) {
            autoPrompted = true
            launchPrompt()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Fingerprint,
            contentDescription = null,
            tint = DribbblePurple,
            modifier = Modifier.size(88.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Welcome back${if (displayName.isNotBlank()) ", $displayName" else ""}",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = failedMessage ?: "Unlock with your biometrics to continue.",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        AuthPrimaryButton(text = "Unlock", onClick = ::launchPrompt, busy = false)
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onUsePasswordInstead) {
            Text("Use password instead")
        }
    }
}
