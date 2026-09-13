package com.banksms.expensetracker.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banksms.expensetracker.BankSmsApp
import com.banksms.expensetracker.ui.theme.DribbblePurple
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val PASSCODE_LENGTH = 4

/**
 * Mandatory one-time setup: shown after a fresh login (and to already
 * signed-in accounts on next start) until the account owns a passcode.
 */
@Composable
fun PasscodeSetupScreen(
    uid: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as BankSmsApp
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(0) } // 0 = enter, 1 = confirm
    var first by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun onDigit(d: Char) {
        if (busy || code.length >= PASSCODE_LENGTH) return
        error = null
        val next = code + d
        if (next.length < PASSCODE_LENGTH) {
            code = next
            return
        }
        code = next
        busy = true
        scope.launch {
            delay(120)
            if (step == 0) {
                first = next
                code = ""
                step = 1
            } else if (next == first) {
                app.passcodeLock.setPasscode(uid, next)
                onDone()
            } else {
                error = "Codes don't match — try again."
                first = ""
                code = ""
                step = 0
            }
            busy = false
        }
    }

    PasscodeScaffold(
        title = if (step == 0) "Set your app passcode" else "Confirm your passcode",
        subtitle = "A 4-digit code that locks Masrofati when fingerprint unlock is off.",
        code = code,
        error = error,
        onDigit = ::onDigit,
        onBackspace = { if (!busy) code = code.dropLast(1) },
        modifier = modifier
    )
}

/**
 * Lock-screen fallback: shown on cold start when a passcode exists but
 * fingerprint unlock is disabled or unavailable.
 */
@Composable
fun PasscodeEntryScreen(
    uid: String,
    displayName: String,
    onUnlocked: () -> Unit,
    onUsePasswordInstead: () -> Unit,
    onUseBiometric: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as BankSmsApp
    val scope = rememberCoroutineScope()

    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun onDigit(d: Char) {
        if (busy || code.length >= PASSCODE_LENGTH) return
        error = null
        val next = code + d
        code = next
        if (next.length < PASSCODE_LENGTH) return
        busy = true
        scope.launch {
            delay(120)
            if (app.passcodeLock.verify(uid, next)) {
                onUnlocked()
            } else {
                error = "Wrong code — try again."
                code = ""
            }
            busy = false
        }
    }

    PasscodeScaffold(
        title = "Enter your passcode",
        subtitle = "Welcome back${if (displayName.isNotBlank()) ", $displayName" else ""}.",
        code = code,
        error = error,
        onDigit = ::onDigit,
        onBackspace = { if (!busy) code = code.dropLast(1) },
        modifier = modifier,
        footer = {
            if (onUseBiometric != null) {
                TextButton(onClick = onUseBiometric) {
                    Text("Use fingerprint instead")
                }
            }
            TextButton(onClick = onUsePasswordInstead) {
                Text("Use password instead")
            }
        }
    )
}

@Composable
private fun PasscodeScaffold(
    title: String,
    subtitle: String,
    code: String,
    error: String?,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = DribbblePurple,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        PasscodeDots(code = code)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = error ?: " ",
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.height(20.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        PasscodePad(onDigit = onDigit, onBackspace = onBackspace)
        Spacer(modifier = Modifier.height(8.dp))
        footer?.invoke()
    }
}

@Composable
private fun PasscodeDots(code: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(PASSCODE_LENGTH) { i ->
            val filled = i < code.length
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(
                        if (filled) DribbblePurple
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}

@Composable
private fun PasscodePad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit
) {
    val rows = listOf("123", "456", "789")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { digit ->
                    PasscodeKey(label = digit.toString()) { onDigit(digit) }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.size(64.dp))
            PasscodeKey(label = "0") { onDigit('0') }
            IconButton(onClick = onBackspace, modifier = Modifier.size(64.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PasscodeKey(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.size(64.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp
                )
            )
        }
    }
}
