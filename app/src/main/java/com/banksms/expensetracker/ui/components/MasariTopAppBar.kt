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

    Column(modifier = modifier) {
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
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.2).sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (title == "Masari") {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MasariEmerald.copy(alpha = 0.12f),
                                    border = CardDefaults.outlinedCardBorder(enabled = true)
                                ) {
                                    Text(
                                        text = "مصاري",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp
                                        ),
                                        color = MasariEmerald,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

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
                            when (mode) {
                                AppThemeMode.LIGHT -> {
                                    Icon(
                                        imageVector = Icons.Default.LightMode,
                                        contentDescription = "Switch to Dark Mode",
                                        tint = Color(0xFFF59E0B), // Warm Amber Sun
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                AppThemeMode.DARK -> {
                                    Icon(
                                        imageVector = Icons.Default.DarkMode,
                                        contentDescription = "Switch to System Mode",
                                        tint = MasariCyan, // Electric Cyan Moon
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                AppThemeMode.SYSTEM -> {
                                    Icon(
                                        imageVector = Icons.Default.BrightnessAuto,
                                        contentDescription = "Switch to Light Mode",
                                        tint = MasariEmerald, // Brand Emerald
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
                scrolledContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            thickness = 0.8.dp
        )
    }
}

@Composable
fun MasariBrandEmblem(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MasariEmerald,
                        MasariCyan
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
            imageVector = Icons.Default.TrendingUp,
            contentDescription = "Masari Emblem",
            tint = Color(0xFF07241A),
            modifier = Modifier.size(20.dp)
        )
    }
}
