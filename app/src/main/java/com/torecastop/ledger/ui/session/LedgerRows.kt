package com.torecastop.ledger.ui.session

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.torecastop.ledger.data.SaleWithItems
import com.torecastop.ledger.data.TradeWithItems
import java.io.File

/**
 * The sale and trade cards of the ledger feed. Shared between the active
 * session (tappable, with the brief "just saved" highlight) and the read-only
 * session history detail (pass a null [onClick]).
 */

/** How many item lines a multi-item sale card shows before collapsing to "+N more". */
private const val MAX_ITEM_LINES = 5

@Composable
internal fun SaleRow(
    saleWithItems: SaleWithItems,
    onClick: (() -> Unit)?,
    highlighted: Boolean = false
) {
    val sale = saleWithItems.sale
    val items = saleWithItems.items
    LedgerCard(onClick = onClick, highlighted = highlighted) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // One representative thumbnail (whole-sale or item, whichever was
            // captured first) plus a "+N" badge when there's more than one —
            // any number of photos can be attached (v1.3 revision).
            saleWithItems.photos.firstOrNull()?.let { first ->
                Box {
                    AsyncImage(
                        model = File(first.photoPath),
                        contentDescription = "Sale photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    if (saleWithItems.photos.size > 1) {
                        Text(
                            "+${saleWithItems.photos.size - 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(topStart = 4.dp)
                                )
                                .padding(horizontal = 3.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
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
                    only.note?.let { ItemNoteLine(it) }
                } else {
                    Text(
                        "${items.size} items",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    // Cap the visible lines so a big multi-item sale can't blow
                    // out one card's height mid-scroll.
                    items.take(MAX_ITEM_LINES).forEach { item ->
                        Text(
                            "${item.quantity} × ${item.sku} · ${formatCurrency(item.price)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        item.note?.let { ItemNoteLine(it) }
                    }
                    if (items.size > MAX_ITEM_LINES) {
                        Text(
                            "+${items.size - MAX_ITEM_LINES} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                sale.note?.let { NoteLine(it) }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatCurrency(saleWithItems.total),
                    style = MaterialTheme.typography.titleMedium.tabularFigures(),
                    fontWeight = FontWeight.Bold
                )
                saleWithItems.changeDue?.let { change ->
                    Text(
                        "change ${formatCurrency(change)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    formatTime(sale.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun TradeRow(
    tradeWithItems: TradeWithItems,
    onClick: (() -> Unit)?,
    highlighted: Boolean = false
) {
    val trade = tradeWithItems.trade
    LedgerCard(onClick = onClick, highlighted = highlighted, accent = true) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // One representative thumbnail plus a "+N" badge — any number of
            // photos can be attached, whole-trade or per-card. (v1.3 revision)
            tradeWithItems.photos.firstOrNull()?.let { first ->
                Box {
                    AsyncImage(
                        model = File(first.photoPath),
                        contentDescription = "Trade photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    if (tradeWithItems.photos.size > 1) {
                        Text(
                            "+${tradeWithItems.photos.size - 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(topStart = 4.dp)
                                )
                                .padding(horizontal = 3.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.SwapHoriz,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Trade",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                tradeWithItems.outItems.forEach { item ->
                    Text(
                        "Out · ${item.quantity} × ${item.label} · ${formatCurrency(item.saleCost)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    item.note?.let { ItemNoteLine(it) }
                }
                tradeWithItems.inItems.forEach { item ->
                    Text(
                        "In · ${item.quantity} × ${item.label} · ${formatCurrency(item.saleCost)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    item.note?.let { ItemNoteLine(it) }
                }
                if (trade.cashAmount > 0) {
                    Text(
                        "Cash ${formatSignedCurrency(tradeWithItems.cashReceived)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val contact = listOfNotNull(trade.customerPhone, trade.customerEmail)
                if (contact.isNotEmpty()) {
                    Text(
                        contact.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                trade.note?.let { NoteLine(it) }
            }
            Column(horizontalAlignment = Alignment.End) {
                // No value-added/margin calc (v1.3 revision) — the headline is
                // whichever plain recorded figure is most informative: net
                // cash when there is any, otherwise the card count.
                if (trade.cashAmount > 0) {
                    Text(
                        formatSignedCurrency(tradeWithItems.cashReceived),
                        style = MaterialTheme.typography.titleMedium.tabularFigures(),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                } else {
                    Text(
                        "${tradeWithItems.itemQuantity} card" +
                            (if (tradeWithItems.itemQuantity == 1) "" else "s"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    formatTime(trade.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LedgerCard(
    onClick: (() -> Unit)?,
    highlighted: Boolean,
    accent: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = when {
        highlighted -> {
            val container by animateColorAsState(
                MaterialTheme.colorScheme.secondaryContainer,
                label = "justSavedHighlight"
            )
            CardDefaults.cardColors(containerColor = container)
        }
        // Trades get a tonal tint so a mixed feed is scannable at a glance.
        accent -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
        else -> CardDefaults.cardColors()
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = colors
    ) {
        content()
    }
}

@Composable
private fun NoteLine(note: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.AutoMirrored.Filled.Notes,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * A per-line item note (serial number / condition), shown indented under the
 * item line it belongs to — distinct from the sale/trade-level [NoteLine].
 */
@Composable
private fun ItemNoteLine(note: String) {
    Text(
        "— $note",
        style = MaterialTheme.typography.bodySmall,
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 8.dp)
    )
}
