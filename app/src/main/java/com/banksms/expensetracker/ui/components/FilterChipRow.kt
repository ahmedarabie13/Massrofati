package com.banksms.expensetracker.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.banksms.expensetracker.data.model.TransactionType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionTypeFilterRow(
    selectedType: TransactionType?,
    onTypeSelected: (TransactionType?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedType == null,
            onClick = { onTypeSelected(null) },
            label = {
                Text(
                    "All Activity",
                    fontWeight = if (selectedType == null) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = com.banksms.expensetracker.ui.theme.DribbblePurple.copy(alpha = 0.16f),
                selectedLabelColor = com.banksms.expensetracker.ui.theme.DribbblePurple
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selectedType == null,
                selectedBorderColor = com.banksms.expensetracker.ui.theme.DribbblePurple.copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(20.dp)
        )

        FilterChip(
            selected = selectedType == TransactionType.EXPENSE,
            onClick = { onTypeSelected(TransactionType.EXPENSE) },
            label = {
                Text(
                    "Expenses (Outflow)",
                    fontWeight = if (selectedType == TransactionType.EXPENSE) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = com.banksms.expensetracker.ui.theme.ExpenseCoral.copy(alpha = 0.16f),
                selectedLabelColor = com.banksms.expensetracker.ui.theme.ExpenseCoral
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selectedType == TransactionType.EXPENSE,
                selectedBorderColor = com.banksms.expensetracker.ui.theme.ExpenseCoral.copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(20.dp)
        )

        FilterChip(
            selected = selectedType == TransactionType.INCOME,
            onClick = { onTypeSelected(TransactionType.INCOME) },
            label = {
                Text(
                    "Income (Inflow)",
                    fontWeight = if (selectedType == TransactionType.INCOME) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = com.banksms.expensetracker.ui.theme.IncomeEmerald.copy(alpha = 0.16f),
                selectedLabelColor = com.banksms.expensetracker.ui.theme.IncomeEmerald
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selectedType == TransactionType.INCOME,
                selectedBorderColor = com.banksms.expensetracker.ui.theme.IncomeEmerald.copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(20.dp)
        )
    }
}
