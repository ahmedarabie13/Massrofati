package com.banksms.expensetracker.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.banksms.expensetracker.BankSmsApp
import com.banksms.expensetracker.ui.navigation.Screen
import com.banksms.expensetracker.ui.screens.dashboard.DashboardScreen
import com.banksms.expensetracker.ui.screens.dashboard.DashboardViewModel
import com.banksms.expensetracker.ui.screens.permissions.PermissionScreen
import com.banksms.expensetracker.ui.screens.reports.ReportsScreen
import com.banksms.expensetracker.ui.screens.reports.ReportsViewModel
import com.banksms.expensetracker.ui.screens.senders.BankSendersScreen
import com.banksms.expensetracker.ui.screens.senders.BankSendersViewModel
import com.banksms.expensetracker.ui.screens.transactions.TransactionsScreen
import com.banksms.expensetracker.ui.screens.transactions.TransactionsViewModel

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
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

            val dashboardViewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory(repository))
            val transactionsViewModel: TransactionsViewModel = viewModel(factory = TransactionsViewModel.Factory(repository))
            val reportsViewModel: ReportsViewModel = viewModel(factory = ReportsViewModel.Factory(repository))
            val sendersViewModel: BankSendersViewModel = viewModel(factory = BankSendersViewModel.Factory(repository))

            // Auto-trigger initial scan when permission is granted
            LaunchedEffect(Unit) {
                dashboardViewModel.syncSms()
            }

            Scaffold(
                bottomBar = {
                    NavigationBar {
                        Screen.items.forEach { screen ->
                            val selected = currentRoute == screen.route
                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                        contentDescription = screen.title
                                    )
                                },
                                label = { Text(screen.title) },
                                selected = selected,
                                onClick = {
                                    if (currentRoute != screen.route) {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                            )
                        }
                    }
                },
                modifier = modifier.fillMaxSize()
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = Screen.Dashboard.route,
                    modifier = Modifier.padding(innerPadding)
                ) {
                    composable(Screen.Dashboard.route) {
                        DashboardScreen(
                            viewModel = dashboardViewModel,
                            onNavigateToTransactions = {
                                navController.navigate(Screen.Transactions.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }

                    composable(Screen.Transactions.route) {
                        TransactionsScreen(viewModel = transactionsViewModel)
                    }

                    composable(Screen.Reports.route) {
                        ReportsScreen(viewModel = reportsViewModel)
                    }

                    composable(Screen.Senders.route) {
                        BankSendersScreen(viewModel = sendersViewModel)
                    }
                }
            }
        }
    }
}
