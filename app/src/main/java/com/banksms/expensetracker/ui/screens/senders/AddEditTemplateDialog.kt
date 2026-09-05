package com.banksms.expensetracker.ui.screens.senders

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.banksms.expensetracker.data.model.MessageTemplate
import com.banksms.expensetracker.data.model.TransactionType
import com.banksms.expensetracker.data.parser.TemplateMatcher
import com.banksms.expensetracker.data.parser.TemplateTestResult
import com.banksms.expensetracker.ui.theme.ExpenseCoral
import com.banksms.expensetracker.ui.theme.IncomeEmerald
import com.banksms.expensetracker.ui.theme.MasariEmerald
import com.banksms.expensetracker.util.CurrencyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTemplateDialog(
    initialTemplate: MessageTemplate? = null,
    onDismiss: () -> Unit,
    onSave: (MessageTemplate) -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    val isEdit = initialTemplate != null

    var name by remember { mutableStateOf(initialTemplate?.name ?: "") }
    var sender by remember { mutableStateOf(initialTemplate?.sender ?: "") }
    var selectedType by remember { mutableStateOf(initialTemplate?.defaultType ?: TransactionType.EXPENSE) }
    var currency by remember { mutableStateOf(initialTemplate?.defaultCurrency ?: "SAR") }

    var patternTextValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = initialTemplate?.pattern ?: "",
                selection = TextRange(initialTemplate?.pattern?.length ?: 0)
            )
        )
    }

    var sampleSms by remember {
        mutableStateOf("")
    }
    var testResult by remember { mutableStateOf<TemplateTestResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val placeholderChips = listOf(
        "{amount}" to "Amount *",
        "{merchant}" to "Merchant",
        "{card}" to "Card",
        "{balance}" to "Balance",
        "{currency}" to "Currency",
        "*" to "Wildcard *",
        "{date}" to "Date",
        "{time}" to "Time"
    )

    fun insertPlaceholder(token: String) {
        val currentText = patternTextValue.text
        val selection = patternTextValue.selection
        val newText = currentText.substring(0, selection.start) + token + currentText.substring(selection.end)
        val newCursor = selection.start + token.length
        patternTextValue = TextFieldValue(
            text = newText,
            selection = TextRange(newCursor)
        )
        testResult = null
    }

    fun runTest() {
        if (patternTextValue.text.isBlank() || sampleSms.isBlank()) {
            errorMessage = "Please enter both a pattern and a sample SMS to test."
            return
        }
        val current = MessageTemplate(
            id = initialTemplate?.id ?: "",
            name = name.ifBlank { "Test" },
            sender = sender,
            pattern = patternTextValue.text,
            defaultType = selectedType,
            defaultCurrency = currency
        )
        testResult = TemplateMatcher.test(current, sampleSms)
        errorMessage = null
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Template?") },
            text = { Text("Are you sure you want to permanently delete '${name}' from your template configs?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        if (initialTemplate != null && onDelete != null) {
                            onDelete(initialTemplate.id)
                        }
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ExpenseCoral)
                ) {
                    Text("Delete Permanently", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isEdit) Icons.Default.Edit else Icons.Default.Add,
                            contentDescription = null,
                            tint = MasariEmerald
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEdit) "Edit SMS Template" else "New SMS Template",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Template Name
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text("Template Name *") },
                    placeholder = { Text("e.g. SNB Card Purchase, Riyad Bank ATM") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Sender ID (Optional or *)
                OutlinedTextField(
                    value = sender,
                    onValueChange = {
                        sender = it
                        testResult = null
                    },
                    label = { Text("Bank Sender (Optional)") },
                    placeholder = { Text("e.g. SNB, Alinma, or leave blank for any bank") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Type & Currency Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Type selector chips
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedType == TransactionType.EXPENSE,
                            onClick = { selectedType = TransactionType.EXPENSE },
                            label = { Text("Expense") },
                            shape = RoundedCornerShape(12.dp)
                        )
                        FilterChip(
                            selected = selectedType == TransactionType.INCOME,
                            onClick = { selectedType = TransactionType.INCOME },
                            label = { Text("Income") },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Currency
                    OutlinedTextField(
                        value = currency,
                        onValueChange = { currency = it.uppercase() },
                        label = { Text("Currency") },
                        singleLine = true,
                        modifier = Modifier.width(90.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Pattern Field Label & Placeholder helper chips
                Text(
                    text = "Template Pattern *",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap tokens below to insert extraction placeholders into your pattern:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Insertion helper chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    placeholderChips.forEach { (token, label) ->
                        SuggestionChip(
                            onClick = { insertPlaceholder(token) },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (token == "{amount}") FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Pattern Text Area
                OutlinedTextField(
                    value = patternTextValue,
                    onValueChange = {
                        patternTextValue = it
                        testResult = null
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8,
                    placeholder = {
                        Text(
                            "e.g.\nشراء عبر: {channel}\nمبلغ: {currency} {amount}\nلدى: {merchant}\nالرصيد: {balance} ريال",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Interactive Live Test Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    border = CardDefaults.outlinedCardBorder(enabled = true)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Test with Sample SMS",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Button(
                                onClick = { runTest() },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MasariEmerald)
                            ) {
                                Text("Test Pattern", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = sampleSms,
                            onValueChange = {
                                sampleSms = it
                                testResult = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Paste actual bank SMS here to test pattern matching...", fontSize = 11.sp) },
                            minLines = 2,
                            maxLines = 5,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            shape = RoundedCornerShape(10.dp)
                        )

                        // Live Test Results
                        AnimatedVisibility(visible = testResult != null) {
                            testResult?.let { res ->
                                Spacer(modifier = Modifier.height(10.dp))
                                if (res.isMatch && res.parsedTransaction != null) {
                                    val tx = res.parsedTransaction
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = IncomeEmerald.copy(alpha = 0.12f),
                                        border = CardDefaults.outlinedCardBorder(enabled = true),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = IncomeEmerald,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Pattern Match Successful!",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = IncomeEmerald
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "• Amount: ${CurrencyFormatter.format(tx.amount, tx.currency)}",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            tx.merchant?.let {
                                                Text(text = "• Merchant: $it", style = MaterialTheme.typography.bodySmall)
                                            }
                                            tx.accountOrCard?.let {
                                                Text(text = "• Card: $it", style = MaterialTheme.typography.bodySmall)
                                            }
                                            tx.availableBalance?.let {
                                                Text(
                                                    text = "• Balance: ${CurrencyFormatter.format(it, tx.currency)}",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = ExpenseCoral.copy(alpha = 0.12f),
                                        border = CardDefaults.outlinedCardBorder(enabled = true),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = ExpenseCoral,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = res.errorMessage ?: "Pattern did not match sample message",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = ExpenseCoral
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Bottom Actions: Delete / Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isEdit && onDelete != null) {
                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ExpenseCoral),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete")
                        }
                    }

                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                errorMessage = "Please enter a template name"
                                return@Button
                            }
                            if (patternTextValue.text.isBlank()) {
                                errorMessage = "Template pattern cannot be empty"
                                return@Button
                            }
                            if (!patternTextValue.text.contains("{amount}")) {
                                errorMessage = "Pattern must include the {amount} token"
                                return@Button
                            }

                            val tpl = if (isEdit) {
                                initialTemplate!!.copy(
                                    name = name.trim(),
                                    sender = sender.trim(),
                                    pattern = patternTextValue.text.trim(),
                                    defaultType = selectedType,
                                    defaultCurrency = currency.trim(),
                                    updatedAt = System.currentTimeMillis()
                                )
                            } else {
                                MessageTemplate(
                                    name = name.trim(),
                                    sender = sender.trim(),
                                    pattern = patternTextValue.text.trim(),
                                    defaultType = selectedType,
                                    defaultCurrency = currency.trim(),
                                    isEnabled = true,
                                    createdAt = System.currentTimeMillis(),
                                    updatedAt = System.currentTimeMillis()
                                )
                            }
                            onSave(tpl)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MasariEmerald),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isEdit) "Save Template" else "Create Template", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
