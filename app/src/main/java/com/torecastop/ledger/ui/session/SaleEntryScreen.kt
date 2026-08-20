package com.torecastop.ledger.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.torecastop.ledger.data.SaleItem
import com.torecastop.ledger.ui.scan.BarcodeScannerScreen
import java.io.File

/**
 * Full-screen sale entry: build a cart of items (scan or type each SKU), add
 * an optional shared note and photo, then save the whole sale in one tap. A
 * single not-yet-added valid item is included automatically so single-item
 * sales stay one-tap fast. "Add & scan next" keeps the rapid multi-card loop
 * going straight through the camera.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleEntryScreen(
    onSave: (
        items: List<SaleItem>,
        itemPhotoPaths: List<List<String>>,
        note: String?,
        salePhotoPaths: List<String>,
        cashReceived: Double?
    ) -> Unit,
    onCancel: () -> Unit
) {
    val cart = remember { mutableStateListOf<DraftItem>() }
    var sku by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf(1) }
    var priceText by remember { mutableStateOf("") }
    var itemNote by remember { mutableStateOf("") }
    var itemPhotoPaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var note by remember { mutableStateOf("") }
    var cashReceivedText by remember { mutableStateOf("") }
    var salePhotoPaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var showScanner by remember { mutableStateOf(false) }
    var scannedSku by remember { mutableStateOf<String?>(null) }
    var confirmHighValue by remember { mutableStateOf(false) }

    val skuFocusRequester = remember { FocusRequester() }
    val priceFocusRequester = remember { FocusRequester() }

    fun cancel() {
        // Nothing captured here is attached to a saved sale yet — clean up
        // every file so a cancelled sale doesn't leave orphans on disk.
        (cart.flatMap { it.photoPaths } + itemPhotoPaths + salePhotoPaths)
            .forEach { File(it).delete() }
        onCancel()
    }

    BackHandler { if (showScanner) showScanner = false else cancel() }

    if (showScanner) {
        BarcodeScannerScreen(
            onResult = { scannedSku = it; showScanner = false },
            onCancel = { showScanner = false }
        )
        return
    }

    // A completed scan lands in the SKU field; jump focus to the price so the
    // scan → price → add loop needs no extra taps.
    LaunchedEffect(scannedSku) {
        scannedSku?.let {
            sku = it
            scannedSku = null
            runCatching { priceFocusRequester.requestFocus() }
        }
    }

    fun currentDraft() = DraftItem(
        sku = sku,
        quantity = quantity,
        priceText = priceText,
        note = itemNote,
        photoPaths = itemPhotoPaths
    )

    fun clearItemFields() {
        sku = ""
        quantity = 1
        priceText = ""
        itemNote = ""
        itemPhotoPaths = emptyList()
    }

    fun addCurrentToCart(): Boolean {
        val draft = currentDraft()
        if (!draft.isValid) return false
        cart.add(draft)
        clearItemFields()
        return true
    }

    fun commitSale() {
        val draft = currentDraft()
        val drafts = if (draft.isValid) cart + draft else cart.toList()
        if (drafts.isEmpty()) return
        onSave(
            drafts.map { it.toSaleItem() },
            drafts.map { it.photoPaths },
            note.trim().ifBlank { null },
            salePhotoPaths,
            cashReceivedText.toDoubleOrNull()
        )
    }

    val canAddItem = currentDraft().isValid
    val canSave = cart.isNotEmpty() || canAddItem
    val cartTotal = cart.sumOf { it.subtotal } + (if (canAddItem) currentDraft().subtotal else 0.0)
    val itemCount = cart.size + if (canAddItem) 1 else 0
    val cashReceived = cashReceivedText.toDoubleOrNull()
    val changeDue = cashReceived?.let { it - cartTotal }

    // A large total asks for confirmation before saving (typo guard); small
    // everyday sales save straight through.
    fun attemptSave() {
        if (!canSave) return
        if (cartTotal >= HIGH_VALUE_CONFIRM_THRESHOLD) confirmHighValue = true else commitSale()
    }

    if (confirmHighValue) {
        HighValueConfirmDialog(
            amount = cartTotal,
            onConfirm = { confirmHighValue = false; commitSale() },
            onDismiss = { confirmHighValue = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New sale") },
                navigationIcon = {
                    IconButton(onClick = { cancel() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel sale")
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Button(
                    onClick = { attemptSave() },
                    enabled = canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(56.dp)
                ) {
                    Text(
                        if (itemCount > 0)
                            "Save sale · $itemCount item${if (itemCount == 1) "" else "s"} · ${formatCurrency(cartTotal)}"
                        else "Save sale",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = sku,
                onValueChange = { sku = it },
                label = { Text("SKU") },
                placeholder = { Text("Scan or type the SKU") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { showScanner = true }) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan barcode")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(skuFocusRequester)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = itemNote,
                onValueChange = { itemNote = it },
                label = { Text("Item note — serial no. / condition (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            MultiPhotoCaptureRow(
                photoPaths = itemPhotoPaths,
                onPhotosChanged = { itemPhotoPaths = it },
                filePrefix = "sale_item",
                label = "Photo of this card"
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuantityStepper(quantity = quantity, onChange = { quantity = it })
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { input ->
                        if (input.matches(MONEY_INPUT_REGEX)) priceText = input
                    },
                    label = { Text("Price") },
                    prefix = { Text("$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        if (addCurrentToCart()) runCatching { skuFocusRequester.requestFocus() }
                    }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(priceFocusRequester)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (addCurrentToCart()) runCatching { skuFocusRequester.requestFocus() }
                    },
                    enabled = canAddItem
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add item to sale")
                }
            }

            // Rapid multi-card loop: bank the current item and go straight
            // back to the camera for the next one.
            TextButton(
                onClick = { if (addCurrentToCart()) showScanner = true },
                enabled = canAddItem
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add & scan next")
            }

            if (cart.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    cart.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${item.quantity} × ${item.sku}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "${formatCurrency(item.price ?: 0.0)} each",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (item.note.isNotBlank()) {
                                    Text(
                                        item.note,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (item.photoPaths.isNotEmpty()) {
                                    Text(
                                        "${item.photoPaths.size} photo${if (item.photoPaths.size == 1) "" else "s"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                formatCurrency(item.subtotal),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            IconButton(onClick = {
                                item.photoPaths.forEach { File(it).delete() }
                                cart.removeAt(index)
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove item")
                            }
                        }
                    }
                }
                HorizontalDivider()
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = cashReceivedText,
                onValueChange = { if (it.matches(MONEY_INPUT_REGEX)) cashReceivedText = it },
                label = { Text("Cash received (optional)") },
                prefix = { Text("$") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            if (changeDue != null) {
                Text(
                    if (changeDue >= 0) "Change due ${formatCurrency(changeDue)}"
                    else "Short ${formatCurrency(-changeDue)} — received less than the total",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (changeDue < 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note for this sale (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text("Photos of the whole sale (optional)", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            MultiPhotoCaptureRow(
                photoPaths = salePhotoPaths,
                onPhotosChanged = { salePhotoPaths = it },
                filePrefix = "sale"
            )
        }
    }
}
