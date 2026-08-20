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
    /** Total sale cost of cards given (OUT), across all trades. (v1.3 revision) */
    val tradeOutTotal: Double,
    /** Total sale cost of cards received (IN), across all trades. (v1.3 revision) */
    val tradeInTotal: Double,
    /** Net cash moved by trades, signed from the store's side. */
    val tradeCash: Double,
    /** Epoch millis of the earliest recorded entry; null when there are none. */
    val firstTimestamp: Long?,
    /** Epoch millis of the latest recorded entry; null when there are none. */
    val lastTimestamp: Long?,
    /** Cash in the drawer at open, if counted. (v1.3) */
    val startingFloat: Double? = null,
    /** Cash counted in the drawer at close, if counted. (v1.3) */
    val countedCash: Double? = null,
    /** Signed net of the session's cash adjustments. (v1.3) */
    val cashAdjustmentsNet: Double = 0.0
) {
    val isEmpty: Boolean get() = saleCount == 0 && tradeCount == 0

    /** Cash the drawer should hold: float + cash sales + net trade cash + adjustments. */
    val expectedCash: Double
        get() = (startingFloat ?: 0.0) + cashTotal + tradeCash + cashAdjustmentsNet

    /** Counted minus expected; null until the drawer is counted. */
    val cashVariance: Double? get() = countedCash?.let { it - expectedCash }

    /** Whether there's any reconciliation data worth showing. */
    val hasReconciliation: Boolean
        get() = startingFloat != null || countedCash != null || cashAdjustmentsNet != 0.0

    companion object {
        fun from(
            sales: List<SaleWithItems>,
            trades: List<TradeWithItems>,
            startingFloat: Double? = null,
            countedCash: Double? = null,
            cashAdjustmentsNet: Double = 0.0
        ): SessionSummary {
            val timestamps = sales.map { it.sale.timestamp } + trades.map { it.trade.timestamp }
            return SessionSummary(
                saleCount = sales.size,
                itemCount = sales.sumOf { it.itemQuantity },
                cashTotal = sales.sumOf { it.total },
                tradeCount = trades.size,
                tradeOutTotal = trades.sumOf { it.outTotal },
                tradeInTotal = trades.sumOf { it.inTotal },
                tradeCash = trades.sumOf { it.cashReceived },
                firstTimestamp = timestamps.minOrNull(),
                lastTimestamp = timestamps.maxOrNull(),
                startingFloat = startingFloat,
                countedCash = countedCash,
                cashAdjustmentsNet = cashAdjustmentsNet
            )
        }
    }
}
