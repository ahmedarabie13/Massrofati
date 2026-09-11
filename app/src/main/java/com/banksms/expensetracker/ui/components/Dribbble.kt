package com.banksms.expensetracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** A single slice of [DonutChart], expressed as a fraction of the whole (0f..1f). */
data class DonutSegment(val fraction: Float, val color: Color)

/**
 * Donut chart in the Dribbble expense-report language: thick rounded slices
 * separated by page-background gaps, an optional dashed "remainder" slice,
 * centered total, and a dark %-badge pinned to the largest slice.
 */
@Composable
fun DonutChart(
    segments: List<DonutSegment>,
    centerLabel: String,
    centerValue: String,
    modifier: Modifier = Modifier,
    remainderFraction: Float = 0f,
    remainderColor: Color = Color.Unspecified,
    chartSize: Dp = 232.dp,
    stroke: Dp = 34.dp,
    gapDegrees: Float = 4f,
    showBadge: Boolean = true
) {
    val density = LocalDensity.current
    val gap = gapDegrees
    // Adaptive remainder: light track in light mode, dark track in dark mode.
    val resolvedRemainder =
        if (remainderColor == Color.Unspecified) MaterialTheme.colorScheme.surfaceContainerHigh
        else remainderColor

    // Mid-angle (degrees, Canvas space: 0 = 3 o'clock, clockwise) of largest slice.
    val badge = remember(segments) {
        var angle = -90f
        var bestMid = 0f
        var bestFraction = -1f
        segments.forEach { seg ->
            val sweep = seg.fraction * 360f
            if (seg.fraction > bestFraction) {
                bestFraction = seg.fraction
                bestMid = angle + sweep / 2f
            }
            angle += sweep
        }
        if (bestFraction > 0.02f) bestMid to bestFraction else null
    }

    val radiusDp = remember(chartSize, stroke) { (chartSize - stroke) / 2 }
    val badgeOffset = remember(badge, radiusDp, density) {
        badge?.let { (midDeg, _) ->
            val rad = Math.toRadians(midDeg.toDouble())
            val rPx = with(density) { radiusDp.toPx() }
            val xPx = (rPx * cos(rad)).toFloat()
            val yPx = (rPx * sin(rad)).toFloat()
            with(density) { xPx.toDp() to yPx.toDp() }
        }
    }

    Box(modifier = modifier.size(chartSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = stroke.toPx()
            val diameter = chartSize.toPx() - strokePx
            val arcTopLeft = Offset(
                (chartSize.toPx() - diameter) / 2f,
                (chartSize.toPx() - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)

            var angle = -90f
            segments.forEach { seg ->
                val sweep = (seg.fraction * 360f - gap).coerceAtLeast(0f)
                if (sweep > 0.5f) {
                    drawArc(
                        color = seg.color,
                        startAngle = angle + gap / 2f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }
                angle += seg.fraction * 360f
            }

            if (remainderFraction > 0.005f) {
                val sweep = (remainderFraction * 360f - gap).coerceAtLeast(0f)
                if (sweep > 0.5f) {
                    drawArc(
                        color = resolvedRemainder,
                        startAngle = angle + gap / 2f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(
                            width = strokePx,
                            cap = StrokeCap.Butt,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 9f), 0f)
                        )
                    )
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerLabel,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = centerValue,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }

        if (showBadge && badge != null && badgeOffset != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.inverseSurface,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = badgeOffset.first, y = badgeOffset.second)
            ) {
                Text(
                    text = "${(badge.second * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Expenses / Income style segmented control: grey capsule track with a
 * white elevated pill for the selected option.
 */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = CircleShape,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Surface(
                    onClick = { onSelect(index) },
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    shadowElevation = if (selected) 2.dp else 0.dp,
                    border = if (selected) androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant
                    ) else null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        ),
                        color = if (selected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
            }
        }
    }
}

/** Small "November 2025" style dropdown pill used in headers. */
@Composable
fun MonthPill(
    label: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                ),
                color = contentColor
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
