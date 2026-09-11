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
import androidx.compose.material.icons.filled.Paid
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
import com.banksms.expensetracker.ui.theme.RizeqDahab
import com.banksms.expensetracker.ui.theme.RizeqLapis

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RizeqTopAppBar(
    title: String = "Massrofati",
    subtitle: String? = null,
    showBrandEmblem: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier
) {
    val themeManager = LocalThemeManager.current
    val currentTheme = themeManager.themeMode.value

    Column(modifier = modifier) {
        TopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (showBrandEmblem) {
                        RizeqBrandEmblem()
                    }

                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.2).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            actions = {
                // Sleek theme toggle button
                Surface(
                    onClick = { themeManager.toggleNext() },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = CardDefaults.outlinedCardBorder(enabled = true),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = currentTheme,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                            },
                            label = "ThemeToggleAnimation"
                        ) { mode ->
                            val toggleTint = MaterialTheme.colorScheme.onSurfaceVariant
                            when (mode) {
                                AppThemeMode.LIGHT -> {
                                    Icon(
                                        imageVector = Icons.Default.LightMode,
                                        contentDescription = "Switch to Dark Mode",
                                        tint = toggleTint,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                AppThemeMode.DARK -> {
                                    Icon(
                                        imageVector = Icons.Default.DarkMode,
                                        contentDescription = "Switch to System Mode",
                                        tint = toggleTint,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                AppThemeMode.SYSTEM -> {
                                    Icon(
                                        imageVector = Icons.Default.BrightnessAuto,
                                        contentDescription = "Switch to Light Mode",
                                        tint = toggleTint,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Custom caller actions
                actions()
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            thickness = 0.8.dp
        )
    }
}

@Composable
fun RizeqBrandEmblem(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        RizeqLapis,
                        RizeqDahab
                    )
                )
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.35f),
                shape = RoundedCornerShape(11.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Paid,
            contentDescription = "Rizeq Emblem",
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}
