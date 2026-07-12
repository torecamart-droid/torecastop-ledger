package com.torecastop.ledger.ui.session

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notes
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
import androidx.compose.ui.text.font.FontWeight
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
                sale.note?.let { NoteLine(it) }
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

@Composable
internal fun TradeRow(
    tradeWithItems: TradeWithItems,
    onClick: (() -> Unit)?,
    highlighted: Boolean = false
) {
    val trade = tradeWithItems.trade
    LedgerCard(onClick = onClick, highlighted = highlighted) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            trade.photoPath?.let { path ->
                AsyncImage(
                    model = File(path),
                    contentDescription = "Trade photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
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
                        "Out · ${item.quantity} × ${item.label} · ${formatCurrency(item.tradeValue)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                tradeWithItems.inItems.forEach { item ->
                    Text(
                        "In · ${item.quantity} × ${item.label} · ${formatCurrency(item.tradeValue)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (trade.cashAmount > 0) {
                    Text(
                        "Cash ${formatSignedCurrency(tradeWithItems.cashReceived)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                trade.note?.let { NoteLine(it) }
            }
            Column(horizontalAlignment = Alignment.End) {
                val valueAdded = tradeWithItems.valueAdded
                Text(
                    formatSignedCurrency(valueAdded),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        valueAdded > 0.0049 -> MaterialTheme.colorScheme.secondary
                        valueAdded < -0.0049 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    if (tradeWithItems.margin != null) "margin" else "swing",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
    content: @Composable () -> Unit
) {
    val colors = if (highlighted) {
        val container by animateColorAsState(
            MaterialTheme.colorScheme.secondaryContainer,
            label = "justSavedHighlight"
        )
        CardDefaults.cardColors(containerColor = container)
    } else {
        CardDefaults.cardColors()
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
            Icons.Filled.Notes,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
