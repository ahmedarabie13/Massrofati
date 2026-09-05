package com.banksms.expensetracker.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banksms.expensetracker.ui.theme.AppThemeMode
import com.banksms.expensetracker.ui.theme.LocalThemeManager
import com.banksms.expensetracker.ui.theme.MasariCyan
import com.banksms.expensetracker.ui.theme.MasariEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasariTopAppBar(
    title: String = "Masari",
    subtitle: String? = null,
    showBrandEmblem: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier
) {
    val themeManager = LocalThemeManager.current
    val currentTheme = themeManager.themeMode.value

    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (showBrandEmblem) {
                    MasariBrandEmblem()
                }

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (title == "Masari") {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "مصاري",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                ),
                                color = MasariEmerald
                            )
                        }
                    }

                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        actions = {
            // Theme toggle button with animation
            IconButton(
                onClick = { themeManager.toggleNext() }
            ) {
                AnimatedContent(
                    targetState = currentTheme,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                    },
                    label = "ThemeToggleAnimation"
                ) { mode ->
                    when (mode) {
                        AppThemeMode.LIGHT -> {
                            Icon(
                                imageVector = Icons.Default.LightMode,
                                contentDescription = "Switch to Dark Mode",
                                tint = Color(0xFFF59E0B) // Warm Amber Sun
                            )
                        }
                        AppThemeMode.DARK -> {
                            Icon(
                                imageVector = Icons.Default.DarkMode,
                                contentDescription = "Switch to System Mode",
                                tint = MasariCyan // Electric Cyan Moon
                            )
                        }
                        AppThemeMode.SYSTEM -> {
                            Icon(
                                imageVector = Icons.Default.BrightnessAuto,
                                contentDescription = "Switch to Light Mode",
                                tint = MaterialTheme.colorScheme.primary // Brand Emerald
                            )
                        }
                    }
                }
            }

            // Custom caller actions
            actions()
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
    )
}

@Composable
fun MasariBrandEmblem(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MasariEmerald,
                        MasariCyan
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.TrendingUp,
            contentDescription = "Masari Emblem",
            tint = Color(0xFF0B0F19),
            modifier = Modifier.size(20.dp)
        )
    }
}
