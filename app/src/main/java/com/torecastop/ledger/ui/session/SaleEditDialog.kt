package com.torecastop.ledger.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.torecastop.ledger.data.Sale
import com.torecastop.ledger.data.SaleWithItems

/**
 * Edit a whole sale while the session is active: change, add or remove item
 * lines, edit the shared note, or delete the sale. [onSave] receives the
 * updated header plus the current draft items.
 */
@Composable
fun SaleEditDialog(
    saleWithItems: SaleWithItems,
    onSave: (Sale, List<com.torecastop.ledger.data.SaleItem>) -> Unit,
    onDelete: (Sale) -> Unit,
    onDismiss: () -> Unit
) {
    val sale = saleWithItems.sale
    val items = remember {
        mutableStateListOf<DraftItem>().apply {
            addAll(saleWithItems.items.map { DraftItem.from(it) })
        }
    }
    var note by remember { mutableStateOf(sale.note ?: "") }

    val canSave = items.isNotEmpty() && items.all { it.isValid }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit sale") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                items.forEachIndexed { index, item ->
                    ItemEditor(
                        item = item,
                        onChange = { items[index] = it },
                        onRemove = { items.removeAt(index) },
                        canRemove = items.size > 1
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                TextButton(onClick = { items.add(DraftItem()) }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add item")
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        sale.copy(note = note.trim().ifBlank { null }),
                        items.map { it.toSaleItem() }
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onDelete(sale) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun ItemEditor(
    item: DraftItem,
    onChange: (DraftItem) -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = item.sku,
                onValueChange = { onChange(item.copy(sku = it)) },
                label = { Text("SKU") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRemove, enabled = canRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remove item")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            QuantityStepper(quantity = item.quantity, onChange = { onChange(item.copy(quantity = it)) })
            Spacer(modifier = Modifier.width(12.dp))
            OutlinedTextField(
                value = item.priceText,
                onValueChange = { input ->
                    if (input.matches(MONEY_INPUT_REGEX)) onChange(item.copy(priceText = input))
                },
                label = { Text("Price") },
                prefix = { Text("$") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
        }
    }
}
