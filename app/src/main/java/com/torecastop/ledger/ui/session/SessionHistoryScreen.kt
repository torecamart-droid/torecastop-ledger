package com.torecastop.ledger.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.torecastop.ledger.data.Session
import com.torecastop.ledger.data.SessionSummary

/**
 * Past (closed) sessions, newest first. Tap one to re-open it read-only and
 * re-export its zip — for when a file gets lost or needs re-sending.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionHistoryScreen(
    sessions: List<Session>,
    onOpen: (Session) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val closed = sessions.filter { !it.isActive }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session history") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (closed.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No closed sessions yet.\nSessions appear here after you close them.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(closed, key = { it.id }) { session ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(session) }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            session.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            buildString {
                                append("Opened ")
                                append(formatTime(session.startTime))
                                session.endTime?.let {
                                    append(" · closed ")
                                    append(formatTime(it))
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Read-only view of one past session: its end-of-day numbers and the full
 * ledger, with re-export in the top bar. Nothing here is editable — closed
 * sessions are locked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    session: Session,
    viewModel: ActiveSessionViewModel,
    onExportRequest: (SessionSummary) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var detail by remember { mutableStateOf<SessionDetail?>(null) }
    LaunchedEffect(session.id) { detail = viewModel.loadSessionDetail(session.id) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(session.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    detail?.let { loaded ->
                        IconButton(onClick = { onExportRequest(loaded.summary) }) {
                            Icon(Icons.Filled.IosShare, contentDescription = "Export session")
                        }
                    }
                }
            )
        }
    ) { padding ->
        val loaded = detail
        if (loaded == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        val entries = LedgerEntry.merge(loaded.sales, loaded.trades)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SummaryLine("Sales", loaded.summary.saleCount.toString())
                    SummaryLine("Items sold", loaded.summary.itemCount.toString())
                    SummaryLine("Cash total", formatCurrency(loaded.summary.cashTotal), bold = true)
                    if (loaded.summary.tradeCount > 0) {
                        SummaryLine("Trades", loaded.summary.tradeCount.toString())
                        SummaryLine(
                            "Value added",
                            formatSignedCurrency(loaded.summary.tradeValueAdded),
                            bold = true
                        )
                        SummaryLine(
                            "Cash in trades",
                            formatSignedCurrency(loaded.summary.tradeCash)
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                }
            }

            if (entries.isEmpty()) {
                item {
                    Text(
                        "Nothing was recorded in this session.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp)
                    )
                }
            }

            items(entries, key = { it.key }) { entry ->
                when (entry) {
                    is LedgerEntry.SaleEntry -> SaleRow(entry.data, onClick = null)
                    is LedgerEntry.TradeEntry -> TradeRow(entry.data, onClick = null)
                }
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String, bold: Boolean = false) {
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
