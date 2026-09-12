package com.banksms.expensetracker.ui.screens.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
        authViewModel.consumeFreshLogin()
        AuthFlow(authViewModel, modifier)
        return
    }

    // New sign-in this process changed accounts: drop the old unlock.
    if (lastUid != null && lastUid != current.uid) {
        sessionUnlocked = false
        biometricsOfferedFor = null
    }
    lastUid = current.uid

    // Fresh credential login → offer biometric unlock once.
    if (freshUid == current.uid && biometricsOfferedFor != current.uid) {
        EnableBiometricsScreen(
            uid = current.uid,
            onDone = {
                biometricsOfferedFor = current.uid
                authViewModel.consumeFreshLogin()
                sessionUnlocked = true
            },
            modifier = modifier
        )
        return
    }

    // Returning session with biometric opt-in → lock until verified.
    if (!sessionUnlocked &&
        app.biometricUnlock.isEnabledFor(current.uid) &&
        app.biometricUnlock.canAuthenticate(context)
    ) {
        LockScreen(
            displayName = current.displayName ?: "",
            onUnlocked = { sessionUnlocked = true },
            onUsePasswordInstead = { app.authRepository.signOut() },
            modifier = modifier
        )
        return
    }

    MainScreen(modifier = modifier)
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
