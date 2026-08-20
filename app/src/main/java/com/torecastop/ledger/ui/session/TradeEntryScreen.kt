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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.torecastop.ledger.data.Trade
import com.torecastop.ledger.data.TradeItem
import com.torecastop.ledger.data.TradeWithItems
import com.torecastop.ledger.intake.SellerIntakeForm
import com.torecastop.ledger.ui.scan.BarcodeScannerScreen
import java.io.File

/**
 * Full-screen trade entry (and editing, when [existing] is set).
 *
 * The two sides are deliberately asymmetric (the key design insight):
 *  - "Cards out" are the store's stock — scan or type their SKU.
 *  - "Cards in" are the customer's — no barcode yet, so name is entered
 *    manually; real SKUs get assigned later at intake (decision T5).
 *
 * Every card line carries a sale cost (what it's valued at in this deal) and
 * an optional acquisition cost (what the store paid, or is now paying, to
 * acquire it) — plain figures only, no market-value/margin calculation
 * (that headline was scrapped in this v1.3 revision).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeEntryScreen(
    existing: TradeWithItems?,
    onSaveNew: (
        items: List<TradeItem>,
        itemPhotoPaths: List<List<String>>,
        cashAmount: Double,
        cashDirection: String,
        note: String?,
        tradePhotoPaths: List<String>,
        customerPhone: String?,
        customerEmail: String?
    ) -> Unit,
    onSaveEdit: (
        trade: Trade,
        items: List<TradeItem>,
        itemPhotoPaths: List<List<String>>,
        tradePhotoPaths: List<String>
    ) -> Unit,
    onDelete: (Trade) -> Unit,
    onCancel: () -> Unit
) {
    val isEdit = existing != null

    val outItems = remember {
        mutableStateListOf<DraftTradeItem>().apply {
            existing?.outItems?.forEach {
                add(DraftTradeItem.from(it, existing.photosFor(it.id).map { p -> p.photoPath }))
            }
        }
    }
    val inItems = remember {
        mutableStateListOf<DraftTradeItem>().apply {
            existing?.inItems?.forEach {
                add(DraftTradeItem.from(it, existing.photosFor(it.id).map { p -> p.photoPath }))
            }
        }
    }

    // Pending (not yet added) OUT line.
    var outSku by remember { mutableStateOf("") }
    var outQuantity by remember { mutableStateOf(1) }
    var outSaleCostText by remember { mutableStateOf("") }
    var outNote by remember { mutableStateOf("") }
    var outPhotoPaths by remember { mutableStateOf<List<String>>(emptyList()) }

    // Pending (not yet added) IN line.
    var inName by remember { mutableStateOf("") }
    var inQuantity by remember { mutableStateOf(1) }
    var inSaleCostText by remember { mutableStateOf("") }
    var inNote by remember { mutableStateOf("") }
    var inPhotoPaths by remember { mutableStateOf<List<String>>(emptyList()) }

    var cashText by remember {
        mutableStateOf(
            existing?.trade?.cashAmount?.takeIf { it > 0 }
                ?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: ""
        )
    }
    var cashDirection by remember {
        mutableStateOf(existing?.trade?.cashDirection ?: Trade.CASH_STORE_RECEIVES)
    }
    var note by remember { mutableStateOf(existing?.trade?.note ?: "") }
    var customerPhone by remember { mutableStateOf(existing?.trade?.customerPhone ?: "") }
    var customerEmail by remember { mutableStateOf(existing?.trade?.customerEmail ?: "") }
    var tradePhotoPaths by remember {
        mutableStateOf<List<String>>(existing?.tradePhotos?.map { it.photoPath } ?: emptyList())
    }
    var showScanner by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmHighValue by remember { mutableStateOf(false) }
    var showIntakeQr by remember { mutableStateOf(false) }

    fun cancel() {
        // Nothing captured here is attached to a saved trade yet — clean up
        // every file so a cancelled trade doesn't leave orphans on disk. For
        // an edit, only newly captured files (not already-persisted ones).
        val original = existing?.photos?.map { it.photoPath }?.toSet() ?: emptySet()
        (outItems.flatMap { it.photoPaths } + inItems.flatMap { it.photoPaths } +
            outPhotoPaths + inPhotoPaths + tradePhotoPaths)
            .filter { it !in original }
            .forEach { File(it).delete() }
        onCancel()
    }

    BackHandler { if (showScanner) showScanner = false else cancel() }

    if (showScanner) {
        BarcodeScannerScreen(
            onResult = { outSku = it; showScanner = false },
            onCancel = { showScanner = false }
        )
        return
    }

    fun pendingOut() = DraftTradeItem(
        direction = TradeItem.DIRECTION_OUT,
        sku = outSku,
        quantity = outQuantity,
        saleCostText = outSaleCostText,
        note = outNote,
        photoPaths = outPhotoPaths
    )

    fun pendingIn() = DraftTradeItem(
        direction = TradeItem.DIRECTION_IN,
        cardName = inName,
        quantity = inQuantity,
        saleCostText = inSaleCostText,
        note = inNote,
        photoPaths = inPhotoPaths
    )

    fun addPendingOut() {
        if (!pendingOut().isValid) return
        outItems.add(pendingOut())
        outSku = ""; outQuantity = 1; outSaleCostText = ""
        outNote = ""; outPhotoPaths = emptyList()
    }

    fun addPendingIn() {
        if (!pendingIn().isValid) return
        inItems.add(pendingIn())
        inName = ""; inQuantity = 1; inSaleCostText = ""
        inNote = ""; inPhotoPaths = emptyList()
    }

    // Live totals include valid pending lines, so they read true while typing.
    val effectiveOut = outItems.toList() + listOfNotNull(pendingOut().takeIf { it.isValid })
    val effectiveIn = inItems.toList() + listOfNotNull(pendingIn().takeIf { it.isValid })
    val cashAmount = cashText.toDoubleOrNull() ?: 0.0
    val cashReceived =
        if (cashDirection == Trade.CASH_STORE_RECEIVES) cashAmount else -cashAmount

    val outTotal = effectiveOut.sumOf { it.lineValue }
    val inTotal = effectiveIn.sumOf { it.lineValue }

    val canSave = effectiveOut.isNotEmpty() || effectiveIn.isNotEmpty()

    fun commitSave() {
        if (!canSave) return
        val drafts = effectiveOut + effectiveIn
        val items = drafts.map { it.toTradeItem() }
        val itemPhotoPaths = drafts.map { it.photoPaths }
        if (isEdit) {
            onSaveEdit(
                existing!!.trade.copy(
                    note = note.trim().ifBlank { null },
                    cashAmount = cashAmount,
                    cashDirection = cashDirection,
                    customerPhone = customerPhone.trim().ifBlank { null },
                    customerEmail = customerEmail.trim().ifBlank { null }
                ),
                items,
                itemPhotoPaths,
                tradePhotoPaths
            )
        } else {
            onSaveNew(
                items,
                itemPhotoPaths,
                cashAmount,
                cashDirection,
                note.trim().ifBlank { null },
                tradePhotoPaths,
                customerPhone.trim().ifBlank { null },
                customerEmail.trim().ifBlank { null }
            )
        }
    }

    // Guard a big trade (either side) behind a confirm — a typo on a
    // high-value card is expensive to miss.
    val highValueGuard = maxOf(outTotal, inTotal)
    fun attemptSave() {
        if (!canSave) return
        if (highValueGuard >= HIGH_VALUE_CONFIRM_THRESHOLD) confirmHighValue = true else commitSave()
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this trade?") },
            text = { Text("The trade and all its card lines are removed from the session.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(existing.trade) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }

    if (confirmHighValue) {
        HighValueConfirmDialog(
            amount = highValueGuard,
            onConfirm = { confirmHighValue = false; commitSave() },
            onDismiss = { confirmHighValue = false }
        )
    }

    if (showIntakeQr && existing != null) {
        SellerIntakeForm.urlFor(existing.trade.id)?.let { url ->
            SellerIntakeQrDialog(
                url = url,
                tradeId = existing.trade.id,
                onDismiss = { showIntakeQr = false }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "Edit trade" else "New trade") },
                navigationIcon = {
                    IconButton(onClick = { cancel() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel trade")
                    }
                },
                actions = {
                    if (isEdit) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete trade",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
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
                        if (canSave)
                            "Save trade · out ${formatCurrency(outTotal)} / in ${formatCurrency(inTotal)}"
                        else "Save trade",
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
            // --- Cards out (store's stock — scannable) ---
            Text("Cards out — your stock", style = MaterialTheme.typography.titleMedium)
            Text(
                "Scan or type the SKU of each card you're giving.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            outItems.forEachIndexed { index, item ->
                TradeDraftRow(item = item, onRemove = {
                    item.photoPaths.forEach { File(it).delete() }
                    outItems.removeAt(index)
                })
            }

            OutlinedTextField(
                value = outSku,
                onValueChange = { outSku = it },
                label = { Text("SKU") },
                placeholder = { Text("Scan or type the SKU") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { showScanner = true }) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan barcode")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = outNote,
                onValueChange = { outNote = it },
                label = { Text("Item note — serial no. / condition (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            MultiPhotoCaptureRow(
                photoPaths = outPhotoPaths,
                onPhotosChanged = { outPhotoPaths = it },
                filePrefix = "trade_out",
                label = "Photo of this card"
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuantityStepper(quantity = outQuantity, onChange = { outQuantity = it })
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedTextField(
                    value = outSaleCostText,
                    onValueChange = { if (it.matches(MONEY_INPUT_REGEX)) outSaleCostText = it },
                    label = { Text("Sale cost") },
                    prefix = { Text("$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = { addPendingOut() }, enabled = pendingOut().isValid) {
                    Icon(Icons.Filled.Add, contentDescription = "Add outgoing card")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // --- Cards in (customer's — manual entry) ---
            Text("Cards in — from the customer", style = MaterialTheme.typography.titleMedium)
            Text(
                "No barcode yet — enter the card name and its value in the deal. " +
                    "A proper SKU gets assigned later at intake.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            inItems.forEachIndexed { index, item ->
                TradeDraftRow(item = item, onRemove = {
                    item.photoPaths.forEach { File(it).delete() }
                    inItems.removeAt(index)
                })
            }

            OutlinedTextField(
                value = inName,
                onValueChange = { inName = it },
                label = { Text("Card name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = inNote,
                onValueChange = { inNote = it },
                label = { Text("Item note — serial no. / condition (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            MultiPhotoCaptureRow(
                photoPaths = inPhotoPaths,
                onPhotosChanged = { inPhotoPaths = it },
                filePrefix = "trade_in",
                label = "Photo of this card"
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuantityStepper(quantity = inQuantity, onChange = { inQuantity = it })
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedTextField(
                    value = inSaleCostText,
                    onValueChange = { if (it.matches(MONEY_INPUT_REGEX)) inSaleCostText = it },
                    label = { Text("Sale cost") },
                    prefix = { Text("$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = { addPendingIn() }, enabled = pendingIn().isValid) {
                    Icon(Icons.Filled.Add, contentDescription = "Add incoming card")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // --- Cash on top ---
            Text("Cash on top", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = cashText,
                onValueChange = { if (it.matches(MONEY_INPUT_REGEX)) cashText = it },
                label = { Text("Amount (leave empty if none)") },
                prefix = { Text("$") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = cashDirection == Trade.CASH_STORE_RECEIVES,
                    onClick = { cashDirection = Trade.CASH_STORE_RECEIVES },
                    label = { Text("Customer pays us") },
                    enabled = cashAmount > 0
                )
                FilterChip(
                    selected = cashDirection == Trade.CASH_STORE_PAYS,
                    onClick = { cashDirection = Trade.CASH_STORE_PAYS },
                    label = { Text("We pay customer") },
                    enabled = cashAmount > 0
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Running totals (plain figures — no margin/value-added calc) ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Out ${formatCurrency(outTotal)}", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "In ${formatCurrency(inTotal)}" +
                                (if (cashAmount > 0) " · cash ${formatSignedCurrency(cashReceived)}" else ""),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Seller/customer contact (optional, v1.3) ---
            Text("Seller contact — optional", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = customerPhone,
                onValueChange = { customerPhone = it },
                label = { Text("Phone") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = customerEmail,
                onValueChange = { customerEmail = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            if (isEdit && SellerIntakeForm.isConfigured) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { showIntakeQr = true }) {
                    Text("Show seller intake QR")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note for this trade (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text("Photos of the whole trade (optional)", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            MultiPhotoCaptureRow(
                photoPaths = tradePhotoPaths,
                onPhotosChanged = { tradePhotoPaths = it },
                filePrefix = "trade"
            )
        }
    }
}

@Composable
private fun TradeDraftRow(item: DraftTradeItem, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${item.quantity} × ${item.label}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                "${formatCurrency(item.saleCost ?: 0.0)} each",
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
        Text(formatCurrency(item.lineValue), style = MaterialTheme.typography.bodyLarge)
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = "Remove card")
        }
    }
}
