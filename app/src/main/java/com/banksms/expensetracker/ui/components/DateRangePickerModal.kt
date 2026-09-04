package com.banksms.expensetracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.banksms.expensetracker.util.DateRangePreset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeSelectionBottomSheet(
    selectedPreset: DateRangePreset,
    onPresetSelected: (DateRangePreset) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Select Time Period",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            val presets = listOf(
                DateRangePreset.THIS_MONTH,
                DateRangePreset.LAST_MONTH,
                DateRangePreset.LAST_30_DAYS,
                DateRangePreset.THIS_YEAR,
                DateRangePreset.ALL_TIME
            )

            presets.forEach { preset ->
                val isSelected = preset == selectedPreset
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onPresetSelected(preset)
                            onDismiss()
                        }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = preset.label,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    )

                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Divider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
