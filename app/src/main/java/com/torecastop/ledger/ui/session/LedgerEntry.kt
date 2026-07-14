package com.torecastop.ledger.ui.session

import com.torecastop.ledger.data.SaleWithItems
import com.torecastop.ledger.data.TradeWithItems

/**
 * One row in the session's ledger feed — sales and trades merged into a single
 * newest-first list. [key] is stable and unique across both kinds, for
 * LazyColumn keys and the brief "just saved" highlight.
 */
sealed interface LedgerEntry {
    val timestamp: Long
    val key: String

    data class SaleEntry(val data: SaleWithItems) : LedgerEntry {
        override val timestamp: Long get() = data.sale.timestamp
        override val key: String get() = "sale-${data.sale.id}"
    }

    data class TradeEntry(val data: TradeWithItems) : LedgerEntry {
        override val timestamp: Long get() = data.trade.timestamp
        override val key: String get() = "trade-${data.trade.id}"
    }

    companion object {
        fun merge(sales: List<SaleWithItems>, trades: List<TradeWithItems>): List<LedgerEntry> =
            (sales.map { SaleEntry(it) } + trades.map { TradeEntry(it) })
                .sortedByDescending { it.timestamp }
    }
}
