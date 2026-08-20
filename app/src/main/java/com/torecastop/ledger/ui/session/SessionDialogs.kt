package com.torecastop.ledger.ui.session

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.torecastop.ledger.data.CashAdjustment
import com.torecastop.ledger.intake.QrCodeGenerator
import java.io.File
import java.util.Locale

/** Type a quantity directly — the fast path for bulk sales. (v1.3) */
@Composable
fun QuantityEntryDialog(
    current: Int,
    onSet: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(current.toString()) }
    val value = text.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quantity") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    if (input.length <= 4 && input.all { it.isDigit() }) text = input
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = value != null && value >= 1,
                onClick = { value?.let { onSet(it.coerceAtLeast(1)) } }
            ) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Shows the seller intake QR code (and the raw link, with a copy button) for
 * one saved trade — "any seller that comes to our table" scans it to fill in
 * their own contact details on a form the team hosts elsewhere. (v1.3)
 */
@Composable
fun SellerIntakeQrDialog(
    url: String,
    tradeId: Long,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val qrBitmap = remember(url) { QrCodeGenerator.generate(url) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seller intake — trade #$tradeId") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Have the seller scan this to fill in their details. " +
                        "Reference code $tradeId links their response back to this trade.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "Seller intake QR code",
                    modifier = Modifier.size(220.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { clipboard.setText(AnnotatedString(url)) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy link")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

/** Quick confirm before saving an unusually large sale/trade — typo guard. */
@Composable
fun HighValueConfirmDialog(
    amount: Double,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm large amount") },
        text = {
            Text(
                "This comes to ${formatCurrency(amount)} — larger than a usual sale. " +
                    "Save it as is?"
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Check again") } }
    )
}

/** Set or clear the optional show/event name shown next to the session date. */
@Composable
fun SessionLabelDialog(
    current: String?,
    onSave: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(current ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Show / event name") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Names this session alongside its date — e.g. \"PAX Unplugged\". " +
                        "Leave empty to just use the date.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Show name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim().ifBlank { null }) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Record (or clear) the cash physically in the drawer when the session opens. */
@Composable
fun StartingFloatDialog(
    current: Double?,
    onSave: (Double?) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember {
        mutableStateOf(current?.let { String.format(Locale.US, "%.2f", it) } ?: "")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Starting cash float") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "The cash already in the drawer at the start of the day. Used to " +
                        "reconcile the drawer when you close the session.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.matches(MONEY_INPUT_REGEX)) text = it },
                    label = { Text("Float amount") },
                    prefix = { Text("$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.toDoubleOrNull()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Log a cash movement that isn't a sale or trade (paid out / cash in). */
@Composable
fun CashAdjustmentDialog(
    onAdd: (amount: Double, reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    var paidOut by remember { mutableStateOf(true) }
    var amountText by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }

    val amount = amountText.toDoubleOrNull() ?: 0.0
    val canAdd = amount > 0 && reason.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log cash in / out") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = paidOut,
                        onClick = { paidOut = true },
                        label = { Text("Paid out") }
                    )
                    FilterChip(
                        selected = !paidOut,
                        onClick = { paidOut = false },
                        label = { Text("Cash in") }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { if (it.matches(MONEY_INPUT_REGEX)) amountText = it },
                    label = { Text("Amount") },
                    prefix = { Text("$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason — e.g. change for stall 12") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canAdd,
                onClick = { onAdd(if (paidOut) -amount else amount, reason.trim()) }
            ) { Text("Log") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Close-session reconciliation: shows the expected drawer cash (float + cash
 * sales + net trade cash + adjustments), lets the owner count the drawer and
 * see the variance, optionally snap a photo of the count, then close. Counting
 * is skippable — closing without a count records no reconciliation.
 */
@Composable
fun CloseSessionDialog(
    startingFloat: Double?,
    cashSales: Double,
    tradeCash: Double,
    adjustmentsNet: Double,
    onClose: (countedCash: Double?, cashCountPhotoPath: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var countedText by remember { mutableStateOf("") }
    var photoPath by remember { mutableStateOf<String?>(null) }

    val expected = (startingFloat ?: 0.0) + cashSales + tradeCash + adjustmentsNet
    val counted = countedText.toDoubleOrNull()
    val variance = counted?.let { it - expected }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("Close session") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                ReconLine("Starting float", formatCurrency(startingFloat ?: 0.0))
                ReconLine("Cash sales", formatCurrency(cashSales))
                if (tradeCash != 0.0) ReconLine("Trade cash (net)", formatSignedCurrency(tradeCash))
                if (adjustmentsNet != 0.0) {
                    ReconLine("Cash adjustments", formatSignedCurrency(adjustmentsNet))
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ReconLine("Expected in drawer", formatCurrency(expected), bold = true)

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = countedText,
                    onValueChange = { if (it.matches(MONEY_INPUT_REGEX)) countedText = it },
                    label = { Text("Counted in drawer (optional)") },
                    prefix = { Text("$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (variance != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val overShort = when {
                        variance > 0.0049 -> "over"
                        variance < -0.0049 -> "short"
                        else -> "balanced"
                    }
                    Text(
                        "Variance ${formatSignedCurrency(variance)} · $overShort",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            variance < -0.0049 -> MaterialTheme.colorScheme.error
                            variance > 0.0049 -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                PhotoCaptureRow(
                    photoPath = photoPath,
                    onPhotoChanged = { photoPath = it },
                    filePrefix = "cashcount",
                    deleteReplacedFiles = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onClose(counted, photoPath) }) {
                Text("Close session")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                // Cancelling a close discards any drawer photo captured for it.
                photoPath?.let { java.io.File(it).delete() }
                onDismiss()
            }) { Text("Cancel") }
        }
    )
}

/** Compact "log cash in/out" list shown above the ledger when adjustments exist. */
@Composable
fun CashAdjustmentsCard(
    adjustments: List<CashAdjustment>,
    onDelete: (CashAdjustment) -> Unit,
    modifier: Modifier = Modifier
) {
    if (adjustments.isEmpty()) return
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                "Cash in / out",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            adjustments.forEach { adj ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        adj.reason,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        formatSignedCurrency(adj.amount),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (adj.amount < 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.secondary
                    )
                    IconButton(onClick = { onDelete(adj) }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remove adjustment",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReconLine(label: String, value: String, bold: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
    }
}
