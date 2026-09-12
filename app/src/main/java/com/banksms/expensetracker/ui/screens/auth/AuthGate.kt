package com.banksms.expensetracker.ui.screens.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.banksms.expensetracker.BankSmsApp
import com.banksms.expensetracker.ui.MainScreen
import com.google.firebase.auth.FirebaseUser

private object AuthRoutes {
    const val LOGIN = "login"
    const val REGISTER = "register"
}

/**
 * Root gate above everything: splash → login/register → (biometric offer) →
 * (lock) → app. The Firebase SDK owns session persistence; this only routes.
 */
@Composable
fun AuthGate(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as BankSmsApp
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.Factory(app.authRepository)
    )
    val freshUid by authViewModel.freshLoginUid.collectAsState()

    var authReady by remember { mutableStateOf(false) }
    val user by produceState<FirebaseUser?>(initialValue = null, app) {
        app.authRepository.user.collect {
            value = it
            authReady = true
        }
    }

    var sessionUnlocked by remember { mutableStateOf(false) }
    var biometricsOfferedFor by remember { mutableStateOf<String?>(null) }
    var lastUid by remember { mutableStateOf<String?>(null) }
    var sessionReady by remember { mutableStateOf(false) }

    if (!authReady) {
        Splash(modifier)
        return
    }

    val current: FirebaseUser? = user
    if (current == null) {
        // Signed out: reset session flags and show login/registration.
        sessionUnlocked = false
        biometricsOfferedFor = null
        lastUid = null
        sessionReady = false
        authViewModel.consumeFreshLogin()
        app.closeSession()
        AuthFlow(authViewModel, modifier)
        return
    }

    // New sign-in this process changed accounts: drop the old unlock.
    if (lastUid != null && lastUid != current.uid) {
        sessionUnlocked = false
        biometricsOfferedFor = null
    }
    lastUid = current.uid

    val biometricOptIn = app.biometricUnlock.isEnabledFor(current.uid)
    // Biometric hardware state can report unavailable for a moment on cold
    // start (sensor service still binding). When the user opted in, poll
    // briefly instead of deciding once and skipping the lock forever.
    // Fail-open after ~3s so a truly sensor-less device never hangs here.
    var biometricsReady by remember(current.uid) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(current.uid) {
        if (!app.biometricUnlock.isEnabledFor(current.uid)) {
            biometricsReady = false
            return@LaunchedEffect
        }
        biometricsReady = null
        repeat(12) {
            val can = try {
                app.biometricUnlock.canAuthenticate(context)
            } catch (_: Exception) {
                false
            }
            if (can) {
                biometricsReady = true
                return@LaunchedEffect
            }
            kotlinx.coroutines.delay(250)
        }
        biometricsReady = false
    }

    // Fresh credential login → offer biometric unlock once (never re-offer
    // when already enabled).
    if (freshUid == current.uid && biometricsOfferedFor != current.uid && !biometricOptIn) {
        EnableBiometricsScreen(
            uid = current.uid,
            onDone = {
                biometricsOfferedFor = current.uid
                authViewModel.consumeFreshLogin()
                sessionUnlocked = true
                // Open eagerly (synchronous, idempotent): the session block
                // below reaches the same call, but this way Enable never
                // depends on effect timing to advance.
                runCatching { app.openSession(current.uid) }
            },
            modifier = modifier
        )
        return
    }

    // Returning session with biometric opt-in → lock until verified.
    // While the sensor state is still being probed (null), hold the splash
    // so the dashboard never flashes before the lock.
    if (biometricOptIn && biometricsReady == null) {
        Splash(modifier)
        return
    }
    if (!sessionUnlocked && biometricOptIn && biometricsReady == true) {
        LockScreen(
            displayName = current.displayName ?: "",
            onUnlocked = { sessionUnlocked = true },
            onUsePasswordInstead = { app.authRepository.signOut() },
            modifier = modifier
        )
        return
    }

    // Open the Firestore session before the app touches any data. Retried
    // once: a one-shot open failure must never strand the UI on the splash
    // (LaunchedEffect does not refire for an unchanged key).
    LaunchedEffect(current.uid) {
        sessionReady = false
        repeat(2) { attempt ->
            try {
                app.openSession(current.uid)
                sessionReady = app.isSessionOpen(current.uid)
                if (sessionReady) return@LaunchedEffect
            } catch (e: Exception) {
                android.util.Log.w("AuthGate", "openSession failed", e)
                sessionReady = false
            }
            if (attempt == 0) kotlinx.coroutines.delay(2000)
        }
    }
    if (!app.isSessionOpen(current.uid) || !sessionReady) {
        Splash(modifier)
        return
    }

    // Keyed by account, and MainScreen keys every ViewModel by uid too:
    // switching accounts rebuilds screens AND ViewModels against the new
    // session's repository. Neither key() nor a new repository alone is
    // enough — the activity-scoped ViewModel cache would keep serving the
    // previous account's data.
    key(current.uid) {
        MainScreen(uid = current.uid, modifier = modifier)
    }
}

@Composable
private fun AuthFlow(viewModel: AuthViewModel, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = AuthRoutes.LOGIN) {
        composable(AuthRoutes.LOGIN) {
            LoginScreen(
                viewModel = viewModel,
                onGoToRegister = { navController.navigate(AuthRoutes.REGISTER) },
                modifier = modifier
            )
        }
        composable(AuthRoutes.REGISTER) {
            RegisterScreen(
                viewModel = viewModel,
                onGoToLogin = { navController.popBackStack() },
                modifier = modifier
            )
        }
    }
}

@Composable
private fun Splash(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
