package com.banksms.expensetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.banksms.expensetracker.ui.MainScreen
import com.banksms.expensetracker.ui.theme.LocalThemeManager
import com.banksms.expensetracker.ui.theme.MasariTheme
import com.banksms.expensetracker.ui.theme.ThemeManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeManager = remember { ThemeManager(applicationContext) }

            CompositionLocalProvider(LocalThemeManager provides themeManager) {
                MasariTheme(themeMode = themeManager.themeMode.value) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        MainScreen()
                    }
                }
            }
        }
    }
}
