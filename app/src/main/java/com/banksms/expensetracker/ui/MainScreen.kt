package com.banksms.expensetracker.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.banksms.expensetracker.BankSmsApp
import com.banksms.expensetracker.ui.components.AddEditExpenseDialog
import com.banksms.expensetracker.ui.navigation.Screen
import com.banksms.expensetracker.ui.screens.chat.ChatScreen
import com.banksms.expensetracker.ui.screens.chat.ChatViewModel
import com.banksms.expensetracker.ui.screens.dashboard.DashboardScreen
import com.banksms.expensetracker.ui.screens.dashboard.DashboardViewModel
import com.banksms.expensetracker.ui.screens.permissions.PermissionScreen
import com.banksms.expensetracker.ui.screens.profile.ProfileScreen
import com.banksms.expensetracker.ui.screens.profile.ProfileViewModel
import com.banksms.expensetracker.ui.screens.reports.ReportsScreen
import com.banksms.expensetracker.ui.screens.reports.ReportsViewModel
import com.banksms.expensetracker.ui.screens.senders.BankSendersScreen
import com.banksms.expensetracker.ui.screens.senders.BankSendersViewModel
import com.banksms.expensetracker.ui.screens.skipped.SkippedScreen
import com.banksms.expensetracker.ui.screens.skipped.SkippedViewModel
import com.banksms.expensetracker.ui.screens.transactions.TransactionsScreen
import com.banksms.expensetracker.ui.screens.transactions.TransactionsViewModel
import com.banksms.expensetracker.ui.theme.DribbblePurple

/** Tabs shown in the floating bottom bar, in order. Skipped lives inside Transactions. */
private val barTabs = listOf(
    Screen.Dashboard,
    Screen.Transactions,
    Screen.Reports,
    Screen.Senders
)

private fun barLabel(screen: Screen): String = when (screen) {
    is Screen.Dashboard -> "Home"
    is Screen.Transactions -> "Transactions"
    is Screen.Reports -> "Report"
    is Screen.Senders -> "Banks"
    else -> screen.title
}

@Composable
/**
 * @param uid owning account. All ViewModels are keyed by it so an account
 * switch builds fresh ViewModels on the new session's repository — the
 * activity-scoped ViewModel cache would otherwise keep serving the
 * previous account's data (key() alone does not clear it).
 */
fun MainScreen(uid: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as BankSmsApp
    val repository = app.repository

    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[Manifest.permission.READ_SMS] ?: false
        hasSmsPermission = readGranted
    }

    Crossfade(targetState = hasSmsPermission, label = "PermissionCrossfade") { granted ->
        if (!granted) {
            PermissionScreen(
                onRequestPermission = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_SMS,
                            Manifest.permission.RECEIVE_SMS
                        )
                    )
                }
            )
        } else {
            val navController = rememberNavController()
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            val dashboardViewModel: DashboardViewModel = viewModel(key = "DashboardViewModel_$uid", factory = DashboardViewModel.Factory(repository))
            val transactionsViewModel: TransactionsViewModel = viewModel(key = "TransactionsViewModel_$uid", factory = TransactionsViewModel.Factory(repository))
            val skippedViewModel: SkippedViewModel = viewModel(key = "SkippedViewModel_$uid", factory = SkippedViewModel.Factory(repository))
            val reportsViewModel: ReportsViewModel = viewModel(key = "ReportsViewModel_$uid", factory = ReportsViewModel.Factory(repository))
            val sendersViewModel: BankSendersViewModel = viewModel(key = "BankSendersViewModel_$uid", factory = BankSendersViewModel.Factory(repository))
            val profileViewModel: ProfileViewModel = viewModel(
                key = "ProfileViewModel_$uid",
                factory = ProfileViewModel.Factory(app.authRepository, app.biometricUnlock, repository)
            )
            // Shared App engine: one model load for chat + AI parsing.
            val chatEngine = remember { app.refreshEngine() }
            val chatViewModel: ChatViewModel = viewModel(
                key = "ChatViewModel_$uid",
                factory = ChatViewModel.Factory(
                    context.applicationContext as android.app.Application,
                    repository,
                    chatEngine
                )
            )

            var showAddExpense by remember { mutableStateOf(false) }

            fun navigateTo(route: String) {
                if (currentRoute != route) {
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }

            // Auto-trigger initial scan when permission is granted. Cold start
            // only: account switches and post-login compositions reuse this
            // screen and must not fire a full sync under the user's feet.
            LaunchedEffect(Unit) {
                // Request MANAGE_EXTERNAL_STORAGE on Android 11+ so we can access Documents/Masari (legacy data folder)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                }
                if (!app.didColdStartSync) {
                    app.didColdStartSync = true
                    dashboardViewModel.syncSms()
                }
            }

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    // Chat and Profile are full-screen destinations with their
                    // own bars — the floating dock would cover them, so hide
                    // the dock there.
                    if (currentRoute != Screen.Chat.route && currentRoute != Screen.Profile.route) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 18.dp, top = 26.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            // Semi-transparent frosted pill instead of a solid
                            // slab: page content flows full-bleed beneath the
                            // dock and shows faintly through it. (True backdrop
                            // blur isn't available in Compose on minSdk 26, so
                            // translucency + the hairline border carry the
                            // glass effect.)
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                            shadowElevation = 12.dp,
                            tonalElevation = 0.dp,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                barTabs.take(2).forEach { screen ->
                                    DribbbleNavItem(
                                        label = barLabel(screen),
                                        selected = currentRoute == screen.route,
                                        selectedIcon = screen.selectedIcon,
                                        unselectedIcon = screen.unselectedIcon,
                                        onClick = { navigateTo(screen.route) }
                                    )
                                }
                                Spacer(modifier = Modifier.width(64.dp))
                                barTabs.drop(2).forEach { screen ->
                                    DribbbleNavItem(
                                        label = barLabel(screen),
                                        selected = currentRoute == screen.route,
                                        selectedIcon = screen.selectedIcon,
                                        unselectedIcon = screen.unselectedIcon,
                                        onClick = { navigateTo(screen.route) }
                                    )
                                }
                            }
                        }

                        FloatingActionButton(
                            onClick = { showAddExpense = true },
                            shape = CircleShape,
                            containerColor = DribbblePurple,
                            contentColor = Color.White,
                            elevation = FloatingActionButtonDefaults.elevation(
                                defaultElevation = 8.dp,
                                pressedElevation = 10.dp
                            ),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-24).dp)
                                .size(60.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Expense",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    }
                },
                modifier = modifier.fillMaxSize(),
                // Outer scaffold only offsets the bottom bar; each inner screen owns
                // its own Scaffold + top app bar, which draw edge-to-edge behind the
                // status bar. Zeroing this prevents double status-bar insets.
                contentWindowInsets = WindowInsets(0, 0, 0, 0)
            ) { _ ->
                // No bottom inset: content flows full-bleed beneath the floating
                // dock. Each list owns bottom clearance spacers; the bar itself
                // is transparent outside the pill so nothing solid sits under it.
                NavHost(
                    navController = navController,
                    startDestination = Screen.Dashboard.route,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = { fadeIn(animationSpec = tween(180)) },
                    exitTransition = { fadeOut(animationSpec = tween(180)) }
                ) {
                    composable(Screen.Dashboard.route) {
                        DashboardScreen(
                            viewModel = dashboardViewModel,
                            onNavigateToTransactions = { navigateTo(Screen.Transactions.route) },
                            onNavigateToReports = { navigateTo(Screen.Reports.route) },
                            onNavigateToChat = { navigateTo(Screen.Chat.route) },
                            onNavigateToProfile = { navigateTo(Screen.Profile.route) }
                        )
                    }

                    composable(Screen.Transactions.route) {
                        TransactionsScreen(
                            viewModel = transactionsViewModel,
                            onNavigateToSkipped = { navigateTo(Screen.Skipped.route) }
                        )
                    }

                    composable(Screen.Skipped.route) {
                        SkippedScreen(viewModel = skippedViewModel)
                    }

                    composable(Screen.Reports.route) {
                        ReportsScreen(viewModel = reportsViewModel)
                    }

                    composable(Screen.Senders.route) {
                        BankSendersScreen(viewModel = sendersViewModel)
                    }

                    composable(Screen.Chat.route) {
                        ChatScreen(
                            viewModel = chatViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(Screen.Profile.route) {
                        ProfileScreen(
                            viewModel = profileViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }

            if (showAddExpense) {
                AddEditExpenseDialog(
                    initialExpense = null,
                    onDismiss = { showAddExpense = false },
                    onSave = { expense ->
                        dashboardViewModel.addManualExpense(expense)
                        showAddExpense = false
                    }
                )
            }
        }
    }
}

@Composable
private fun DribbbleNavItem(
    label: String,
    selected: Boolean,
    selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val contentColor = if (selected) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(16.dp)
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = if (selected) selectedIcon else unselectedIcon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp
                ),
                color = contentColor,
                textAlign = TextAlign.Center
            )
        }
    }
}
