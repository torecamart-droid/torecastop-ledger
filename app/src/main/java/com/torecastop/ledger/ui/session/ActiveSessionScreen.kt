package com.torecastop.ledger.ui.session

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.torecastop.ledger.data.PhotoStorage
import com.torecastop.ledger.data.SaleItem
import com.torecastop.ledger.data.SaleWithItems
import com.torecastop.ledger.ui.scan.BarcodeScannerScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Active Session screen: running totals, the sale entry form (a cart of items
 * with an optional shared note and photo), and the live, newest-first list of
 * sales. The overflow menu exports the session or closes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionScreen(viewModel: ActiveSessionViewModel) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val itemCount by viewModel.itemCount.collectAsStateWithLifecycle()
    val total by viewModel.total.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showScanner by remember { mutableStateOf(false) }
    var scannedSku by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<SaleWithItems?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }

    if (showScanner) {
        BarcodeScannerScreen(
            onResult = { scannedSku = it; showScanner = false },
            onCancel = { showScanner = false }
        )
        return
    }

    editing?.let { saleWithItems ->
        SaleEditDialog(
            saleWithItems = saleWithItems,
            onSave = { sale, items -> viewModel.updateSale(sale, items); editing = null },
            onDelete = { viewModel.deleteSale(it); editing = null },
            onDismiss = { editing = null }
        )
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("Close this session?") },
            text = { Text("The session is locked once closed. You can still export it, then start a new one.") },
            confirmButton = {
                TextButton(onClick = { confirmClose = false; viewModel.closeSession() }) {
                    Text("Close session")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClose = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(session?.name ?: "TorecaStop Ledger") },
                actions = {
                    if (session != null) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Export session") },
                                onClick = {
                                    menuOpen = false
                                    viewModel.exportSession(context) { uri ->
                                        if (uri != null) shareExport(context, uri)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Close session") },
                                onClick = { menuOpen = false; confirmClose = true }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        val current = session
        if (current == null) {
            NoActiveSession(
                onStart = viewModel::startNewSession,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                SessionTotalsHeader(itemCount = itemCount, total = total)
                SaleEntryForm(
                    onAddSale = viewModel::addSale,
                    onScanRequest = { showScanner = true },
                    scannedSku = scannedSku,
                    onScannedSkuConsumed = { scannedSku = null }
                )
                HorizontalDivider()
                SalesList(
                    sales = sales,
                    onEditSale = { editing = it },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun NoActiveSession(onStart: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("No active session", style = MaterialTheme.typography.titleLarge)
        Text(
            "Start a session to begin recording sales.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        Button(onClick = onStart, modifier = Modifier.padding(top = 24.dp)) {
            Text("Start today's session")
        }
    }
}

@Composable
private fun SessionTotalsHeader(itemCount: Int, total: Double) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Items sold", style = MaterialTheme.typography.labelMedium)
                Text(
                    itemCount.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Total", style = MaterialTheme.typography.labelMedium)
                Text(
                    formatCurrency(total),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SaleEntryForm(
    onAddSale: (items: List<SaleItem>, note: String?, photoPath: String?) -> Unit,
    onScanRequest: () -> Unit,
    scannedSku: String?,
    onScannedSkuConsumed: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val cart = remember { mutableStateListOf<DraftItem>() }
    var sku by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf(1) }
    var priceText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var photoFile by remember { mutableStateOf<File?>(null) }
    var photoPath by remember { mutableStateOf<String?>(null) }

    val skuFocusRequester = remember { FocusRequester() }

    // A completed scan arrives here; drop it into the current SKU field.
    LaunchedEffect(scannedSku) {
        scannedSku?.let {
            sku = it
            onScannedSkuConsumed()
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = photoFile
        if (success && file != null) {
            scope.launch {
                withContext(Dispatchers.IO) { PhotoStorage.compress(file) }
                photoPath = file.absolutePath
            }
        } else {
            file?.delete()
            photoFile = null
        }
    }

    fun currentDraft() = DraftItem(sku = sku, quantity = quantity, priceText = priceText)

    fun clearItemFields() {
        sku = ""
        quantity = 1
        priceText = ""
    }

    fun addCurrentToCart() {
        val draft = currentDraft()
        if (!draft.isValid) return
        cart.add(draft)
        clearItemFields()
        skuFocusRequester.requestFocus()
    }

    fun saveSale() {
        val draft = currentDraft()
        val items = if (draft.isValid) cart + draft else cart.toList()
        if (items.isEmpty()) return
        onAddSale(items.map { it.toSaleItem() }, note.trim().ifBlank { null }, photoPath)
        cart.clear()
        clearItemFields()
        note = ""
        photoFile = null
        photoPath = null
        skuFocusRequester.requestFocus()
    }

    val canAddItem = currentDraft().isValid
    val canSave = cart.isNotEmpty() || canAddItem
    val cartTotal = cart.sumOf { it.subtotal } + (if (canAddItem) currentDraft().subtotal else 0.0)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("New sale", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = sku,
                onValueChange = { sku = it },
                label = { Text("SKU") },
                placeholder = { Text("Scan or type the SKU") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = onScanRequest) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan barcode")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(skuFocusRequester)
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
                        if (input.matches(PRICE_REGEX)) priceText = input
                    },
                    label = { Text("Price") },
                    prefix = { Text("$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addCurrentToCart() }),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { addCurrentToCart() },
                    enabled = canAddItem
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add item to sale")
                }
            }

            if (cart.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    cart.forEachIndexed { index, item ->
                        CartItemRow(item = item, onRemove = { cart.removeAt(index) })
                    }
                }
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

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    val file = PhotoStorage.newPhotoFile(context)
                    photoFile = file
                    takePicture.launch(PhotoStorage.uriFor(context, file))
                }) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (photoPath == null) "Add photo" else "Retake")
                }
                photoPath?.let { path ->
                    Spacer(modifier = Modifier.width(12.dp))
                    AsyncImage(
                        model = File(path),
                        contentDescription = "Sale photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = {
                        photoFile?.delete()
                        photoFile = null
                        photoPath = null
                    }) { Text("Remove") }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { saveSale() },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                val count = cart.size + if (canAddItem) 1 else 0
                Text(
                    if (count > 0) "Save sale · $count item${if (count == 1) "" else "s"} · ${formatCurrency(cartTotal)}"
                    else "Save sale"
                )
            }
        }
    }
}

@Composable
private fun CartItemRow(item: DraftItem, onRemove: () -> Unit) {
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
        }
        Text(formatCurrency(item.subtotal), style = MaterialTheme.typography.bodyLarge)
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = "Remove item")
        }
    }
}

@Composable
private fun SalesList(
    sales: List<SaleWithItems>,
    onEditSale: (SaleWithItems) -> Unit,
    modifier: Modifier = Modifier
) {
    if (sales.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "No sales yet — add items above and save the first sale.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(sales, key = { it.sale.id }) { saleWithItems ->
            SaleRow(saleWithItems = saleWithItems, onClick = { onEditSale(saleWithItems) })
        }
    }
}

@Composable
private fun SaleRow(saleWithItems: SaleWithItems, onClick: () -> Unit) {
    val sale = saleWithItems.sale
    val items = saleWithItems.items
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                sale.photoPath?.let { path ->
                    AsyncImage(
                        model = File(path),
                        contentDescription = "Sale photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column {
                    if (items.size == 1) {
                        val only = items.first()
                        Text(
                            "${only.quantity} × ${only.sku}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${formatCurrency(only.price)} each",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "${items.size} items",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        items.forEach { item ->
                            Text(
                                "${item.quantity} × ${item.sku} · ${formatCurrency(item.price)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    sale.note?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatCurrency(saleWithItems.total),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    formatTime(sale.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Quantity stepper shared by the entry form and the edit dialog. */
@Composable
internal fun QuantityStepper(quantity: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Qty", style = MaterialTheme.typography.labelLarge)
        IconButton(onClick = { if (quantity > 1) onChange(quantity - 1) }) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease quantity")
        }
        Text(
            quantity.toString(),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.width(28.dp),
            textAlign = TextAlign.Center
        )
        IconButton(onClick = { onChange(quantity + 1) }) {
            Icon(Icons.Filled.Add, contentDescription = "Increase quantity")
        }
    }
}

private fun shareExport(context: android.content.Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Export session"))
}

/** Up to two decimal places, allowing partial input while typing. */
private val PRICE_REGEX = Regex("^\\d*\\.?\\d{0,2}$")
