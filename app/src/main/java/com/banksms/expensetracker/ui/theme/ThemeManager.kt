package com.banksms.expensetracker.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf

enum class AppThemeMode(val title: String) {
    SYSTEM("Auto (System)"),
    LIGHT("Light Mode"),
    DARK("Dark Mode");

    fun next(): AppThemeMode = when (this) {
        SYSTEM -> LIGHT
        LIGHT -> DARK
        DARK -> SYSTEM
    }
}

class ThemeManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("masari_theme_prefs", Context.MODE_PRIVATE)

    private val _themeMode = mutableStateOf(loadThemeMode())
    val themeMode: State<AppThemeMode> = _themeMode

    private fun loadThemeMode(): AppThemeMode {
        val saved = prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name)
        return try {
            AppThemeMode.valueOf(saved ?: AppThemeMode.SYSTEM.name)
        } catch (_: Exception) {
            AppThemeMode.SYSTEM
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun toggleNext(): AppThemeMode {
        val nextMode = _themeMode.value.next()
        setThemeMode(nextMode)
        return nextMode
    }

    companion object {
        private const val KEY_THEME_MODE = "key_app_theme_mode"
    }
}

val LocalThemeManager = compositionLocalOf<ThemeManager> {
    error("LocalThemeManager not provided. Ensure CompositionLocalProvider wraps your hierarchy.")
}
