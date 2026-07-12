package com.torecastop.ledger.data

/**
 * The end-of-day numbers for one session — shown on the pre-export review
 * screen and reused by the session history detail. Sales and trades are kept
 * separate (decision T3) so cash reconciliation stays clean.
 */
data class SessionSummary(
    val saleCount: Int,
    /** Physical items sold (sum of sale line quantities). */
    val itemCount: Int,
    /** Cash taken through sales. */
    val cashTotal: Double,
    val tradeCount: Int,
    /** Sum of each trade's headline value added (margin, else value swing). */
    val tradeValueAdded: Double,
    /** Net cash moved by trades, signed from the store's side. */
    val tradeCash: Double,
    /** Epoch millis of the earliest recorded entry; null when there are none. */
    val firstTimestamp: Long?,
    /** Epoch millis of the latest recorded entry; null when there are none. */
    val lastTimestamp: Long?
) {
    val isEmpty: Boolean get() = saleCount == 0 && tradeCount == 0

    companion object {
        fun from(sales: List<SaleWithItems>, trades: List<TradeWithItems>): SessionSummary {
            val timestamps = sales.map { it.sale.timestamp } + trades.map { it.trade.timestamp }
            return SessionSummary(
                saleCount = sales.size,
                itemCount = sales.sumOf { it.itemQuantity },
                cashTotal = sales.sumOf { it.total },
                tradeCount = trades.size,
                tradeValueAdded = trades.sumOf { it.valueAdded },
                tradeCash = trades.sumOf { it.cashReceived },
                firstTimestamp = timestamps.minOrNull(),
                lastTimestamp = timestamps.maxOrNull()
            )
        }
    }
}
