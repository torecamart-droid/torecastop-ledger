package com.torecastop.ledger.ui.session

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.torecastop.ledger.data.SessionSummary

/**
 * Pre-export review: the day's numbers at a glance before the zip is built —
 * a last "did I actually record everything?" check.
 */
@Composable
fun ExportSummaryDialog(
    sessionName: String,
    summary: SessionSummary,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export \"$sessionName\"?") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                SummaryRow("Sales", summary.saleCount.toString())
                SummaryRow("Items sold", summary.itemCount.toString())
                SummaryRow("Cash total", formatCurrency(summary.cashTotal), bold = true)
                if (summary.tradeCount > 0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SummaryRow("Trades", summary.tradeCount.toString())
                    SummaryRow("Value added", formatSignedCurrency(summary.tradeValueAdded), bold = true)
                    SummaryRow("Cash in trades", formatSignedCurrency(summary.tradeCash))
                }
                if (summary.firstTimestamp != null && summary.lastTimestamp != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SummaryRow(
                        "Recorded",
                        "${formatTime(summary.firstTimestamp)} – ${formatTime(summary.lastTimestamp)}"
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Export") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SummaryRow(label: String, value: String, bold: Boolean = false) {
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
