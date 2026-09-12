package com.banksms.expensetracker.ui.screens.auth

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.banksms.expensetracker.BankSmsApp
import com.banksms.expensetracker.ui.theme.DribbblePurple

/**
 * Offered once, right after a fresh login/registration. If the device has
 * no biometric hardware enrolled the screen auto-continues (nothing to offer).
 */
@Composable
fun EnableBiometricsScreen(
    uid: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as BankSmsApp
    val canOffer = app.biometricUnlock.canAuthenticate(context)

    LaunchedEffect(canOffer) {
        if (!canOffer) onDone()
    }
    if (!canOffer) return

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
            text = "Unlock with your fingerprint?",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Next time you open Massrofati, a quick touch is all it takes. " +
                "You stay signed in — this just guards the front door.",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        AuthPrimaryButton(
            text = "Enable",
            onClick = {
                app.biometricUnlock.setEnabled(uid, true)
                onDone()
            },
            busy = false
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onDone) {
            Text("Skip for now")
        }
    }
}
