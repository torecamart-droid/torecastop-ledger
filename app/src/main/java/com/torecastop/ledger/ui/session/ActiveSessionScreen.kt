package com.torecastop.ledger.ui.session

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.torecastop.ledger.data.SaleWithItems
import com.torecastop.ledger.data.Session
import com.torecastop.ledger.data.SessionSummary
import com.torecastop.ledger.data.TradeWithItems
import kotlinx.coroutines.launch

/** Which full-screen panel is showing. The ledger is home. */
private sealed interface Panel {
    data object Ledger : Panel
    data object NewSale : Panel
    data class TradeForm(val existing: TradeWithItems?) : Panel
    data object History : Panel
    data class HistoryDetail(val session: Session) : Panel
}

private data class ExportRequest(val session: Session, val summary: SessionSummary)

/**
 * Active Session screen: big legible running totals, the merged newest-first
 * ledger of sales and trades, and two thumb-reach bottom buttons — New Sale
 * and New Trade (decision U1). Saving shows a "saved — Undo" snackbar; the
 * overflow menu holds export (with a pre-export summary), session history and
 * close-session.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionScreen(viewModel: ActiveSessionViewModel) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val trades by viewModel.trades.collectAsStateWithLifecycle()
    val itemCount by viewModel.itemCount.collectAsStateWithLifecycle()
    val total by viewModel.total.collectAsStateWithLifecycle()
    val allSessions by viewModel.allSessions.collectAsStateWithLifecycle()
    val highlightKey by viewModel.highlightKey.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var panel by remember { mutableStateOf<Panel>(Panel.Ledger) }
    var editingSale by remember { mutableStateOf<SaleWithItems?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    var exportRequest by remember { mutableStateOf<ExportRequest?>(null) }

    // "Saved — Undo" snackbars for every sale/trade save.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is LedgerEvent.SaleSaved ->
                    "Sale saved · ${formatCurrency(event.total)}"
                is LedgerEvent.TradeSaved ->
                    "Trade saved · value added ${formatSignedCurrency(event.valueAdded)}"
            }
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "Undo",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoLastSave()
        }
    }

    // Pre-export review, shown over whichever panel requested it.
    exportRequest?.let { request ->
        ExportSummaryDialog(
            sessionName = request.session.name,
            summary = request.summary,
            onConfirm = {
                exportRequest = null
                viewModel.exportSession(context, request.session) { uri ->
                    if (uri != null) shareExport(context, uri)
                }
            },
            onDismiss = { exportRequest = null }
        )
    }

    when (val p = panel) {
        Panel.NewSale -> {
            SaleEntryScreen(
                onSave = { items, note, photoPath ->
                    viewModel.addSale(items, note, photoPath)
                    panel = Panel.Ledger
                },
                onCancel = { panel = Panel.Ledger }
            )
            return
        }
        is Panel.TradeForm -> {
            TradeEntryScreen(
                existing = p.existing,
                onSaveNew = { items, cashAmount, cashDirection, note, photoPath ->
                    viewModel.addTrade(items, cashAmount, cashDirection, note, photoPath)
                    panel = Panel.Ledger
                },
                onSaveEdit = { trade, items ->
                    viewModel.updateTrade(trade, items)
                    panel = Panel.Ledger
                },
                onDelete = { trade ->
                    viewModel.deleteTrade(trade)
                    panel = Panel.Ledger
                },
                onCancel = { panel = Panel.Ledger }
            )
            return
        }
        Panel.History -> {
            SessionHistoryScreen(
                sessions = allSessions,
                onOpen = { panel = Panel.HistoryDetail(it) },
                onBack = { panel = Panel.Ledger }
            )
            return
        }
        is Panel.HistoryDetail -> {
            SessionDetailScreen(
                session = p.session,
                viewModel = viewModel,
                onExportRequest = { summary ->
                    exportRequest = ExportRequest(p.session, summary)
                },
                onBack = { panel = Panel.History }
            )
            return
        }
        Panel.Ledger -> Unit // fall through to the main scaffold below
    }

    editingSale?.let { saleWithItems ->
        SaleEditDialog(
            saleWithItems = saleWithItems,
            onSave = { sale, items -> viewModel.updateSale(sale, items); editingSale = null },
            onDelete = { viewModel.deleteSale(it); editingSale = null },
            onDismiss = { editingSale = null }
        )
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("Close this session?") },
            text = { Text("The session is locked once closed. You can still export it from Session history, then start a new one.") },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(session?.name ?: "TorecaStop Ledger") },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        session?.let { current ->
                            DropdownMenuItem(
                                text = { Text("Export session") },
                                onClick = {
                                    menuOpen = false
                                    scope.launch {
                                        val summary = viewModel.summarize(current.id)
                                        if (summary.isEmpty) {
                                            snackbarHostState.showSnackbar(
                                                "Nothing to export yet — record a sale or trade first."
                                            )
                                        } else {
                                            exportRequest = ExportRequest(current, summary)
                                        }
                                    }
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Session history") },
                            onClick = { menuOpen = false; panel = Panel.History }
                        )
                        session?.let {
                            DropdownMenuItem(
                                text = { Text("Close session") },
                                onClick = { menuOpen = false; confirmClose = true }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (session != null) {
                Surface(shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { panel = Panel.NewSale },
                            modifier = Modifier
                                .weight(1f)
                                .height(60.dp)
                        ) {
                            Icon(Icons.Filled.PointOfSale, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("New Sale", style = MaterialTheme.typography.titleMedium)
                        }
                        FilledTonalButton(
                            onClick = { panel = Panel.TradeForm(null) },
                            modifier = Modifier
                                .weight(1f)
                                .height(60.dp)
                        ) {
                            Icon(Icons.Filled.SwapHoriz, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("New Trade", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
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
                SessionTotalsHeader(
                    saleCount = sales.size,
                    itemCount = itemCount,
                    total = total,
                    trades = trades
                )
                val entries = remember(sales, trades) { LedgerEntry.merge(sales, trades) }
                if (entries.isEmpty()) {
                    EmptyLedger(modifier = Modifier.weight(1f))
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(entries, key = { it.key }) { entry ->
                            when (entry) {
                                is LedgerEntry.SaleEntry -> SaleRow(
                                    saleWithItems = entry.data,
                                    onClick = { editingSale = entry.data },
                                    highlighted = highlightKey == entry.key
                                )
                                is LedgerEntry.TradeEntry -> TradeRow(
                                    tradeWithItems = entry.data,
                                    onClick = { panel = Panel.TradeForm(entry.data) },
                                    highlighted = highlightKey == entry.key
                                )
                            }
                        }
                    }
                }
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
        Icon(
            Icons.Filled.Storefront,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(64.dp)
        )
        Text(
            "No active session",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            "Start a session to record today's sales and trades. Past sessions live under Session history in the menu.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        Button(
            onClick = onStart,
            modifier = Modifier
                .padding(top = 24.dp)
                .height(56.dp)
        ) {
            Text("Start today's session", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun EmptyLedger(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            "Nothing recorded yet.\nUse the buttons below — scan a card to start the first sale.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(32.dp)
        )
    }
}

/**
 * Big, sunlight-readable running totals. Sales and trades stay separate
 * (decision T3): the headline is sales cash; the trade line shows count,
 * value added and net trade cash so end-of-day reconciliation is quick.
 */
@Composable
private fun SessionTotalsHeader(
    saleCount: Int,
    itemCount: Int,
    total: Double,
    trades: List<TradeWithItems>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Sales total",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        formatCurrency(total),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "Items sold",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        itemCount.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "$saleCount sale${if (saleCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (trades.isNotEmpty()) {
                val tradeValueAdded = trades.sumOf { it.valueAdded }
                val tradeCash = trades.sumOf { it.cashReceived }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${trades.size} trade${if (trades.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "value added ${formatSignedCurrency(tradeValueAdded)}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            tradeValueAdded > 0.0049 -> MaterialTheme.colorScheme.secondary
                            tradeValueAdded < -0.0049 -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text(
                        "cash ${formatSignedCurrency(tradeCash)}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/** Quantity stepper shared by the entry forms and the edit dialog. */
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
