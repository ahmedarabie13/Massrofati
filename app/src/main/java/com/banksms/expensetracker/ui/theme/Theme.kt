package com.banksms.expensetracker.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val RizeqDarkColorScheme = darkColorScheme(
    primary = Color(0xFF9D8FFF),
    onPrimary = Color(0xFF221358),
    primaryContainer = Color(0xFF3E2F8F),
    onPrimaryContainer = Color(0xFFE4DEFF),
    secondary = Color(0xFFDEB344),
    onSecondary = Color(0xFF4A3400),
    secondaryContainer = Color(0xFF644A00),
    onSecondaryContainer = Color(0xFFFFE8B3),
    tertiary = Color(0xFF63C8A8),
    onTertiary = Color(0xFF0A3A2C),
    tertiaryContainer = Color(0xFF0B5240),
    onTertiaryContainer = Color(0xFFC9F2E2),
    // Neutral dark mirror of the light scheme: near-black page, dark-grey
    // cards, hairline borders — same roles as light mode, inverted values.
    background = Color(0xFF101014),
    onBackground = Color(0xFFF4F4F6),
    surface = Color(0xFF1C1C22),
    onSurface = Color(0xFFF4F4F6),
    surfaceVariant = Color(0xFF26262E),
    onSurfaceVariant = Color(0xFFA7A7B3),
    surfaceContainerLowest = Color(0xFF0C0C10),
    surfaceContainerLow = Color(0xFF1C1C22),
    surfaceContainer = Color(0xFF26262E),
    surfaceContainerHigh = Color(0xFF2E2E37),
    surfaceContainerHighest = Color(0xFF383842),
    outline = Color(0xFF4C4C58),
    outlineVariant = Color(0xFF2C2C35),
    error = Color(0xFFFF8A64),
    onError = Color(0xFF5A1D00),
    errorContainer = Color(0xFF7A2E10),
    onErrorContainer = Color(0xFFFFDCCC)
)

private val RizeqLightColorScheme = lightColorScheme(
    primary = DribbblePurple,
    onPrimary = Color.White,
    primaryContainer = DribbblePurplePale,
    onPrimaryContainer = Color(0xFF352A9E),
    secondary = RizeqDahabDeep,
    onSecondary = Color.White,
    secondaryContainer = RizeqDahabLight,
    onSecondaryContainer = Color(0xFF4A3300),
    tertiary = RizeqNile,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC8EBDF),
    onTertiaryContainer = Color(0xFF0A4635),
    background = DribbblePage,
    onBackground = DribbbleInk,
    surface = Color.White,
    onSurface = DribbbleInk,
    surfaceVariant = Color(0xFFF1F1F4),
    onSurfaceVariant = DribbbleGray,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFC),
    surfaceContainer = Color(0xFFF1F1F4),
    surfaceContainerHigh = Color(0xFFE9E9ED),
    surfaceContainerHighest = Color(0xFFDFDFE5),
    outline = Color(0xFFC9C9D1),
    outlineVariant = DribbbleBorder,
    error = RizeqTerracotta,
    onError = Color.White,
    errorContainer = RizeqTerracottaLight,
    onErrorContainer = Color(0xFF5A2100)
)

@Composable
fun RizeqTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> RizeqDarkColorScheme
        else -> RizeqLightColorScheme
    }

    // Keep the status/navigation bar icon contrast in sync with the app theme
    // (the default enableEdgeToEdge() only follows the system theme).
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Backward-compatible alias for existing references
@Composable
fun BankSmsExpenseTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    RizeqTheme(
        themeMode = if (darkTheme) AppThemeMode.DARK else AppThemeMode.LIGHT,
        dynamicColor = dynamicColor,
        content = content
    )
}