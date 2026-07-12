package com.torecastop.ledger.data

import androidx.room.Embedded
import androidx.room.Relation

/**
 * A [Trade] together with its [TradeItem] lines, plus the value-added maths —
 * the shape the UI and export use.
 *
 * Two metrics (decision T1 — both, margin as headline):
 *  - [valueSwing]: trade values only — was the deal fair at market? Always
 *    computable, no cost basis needed.
 *  - [margin]: what the store actually gained over the cost of the cards it
 *    gave away. Only computable when every OUT line has a cost basis.
 *  - [valueAdded]: the headline — [margin] when available, else [valueSwing].
 */
data class TradeWithItems(
    @Embedded val trade: Trade,
    @Relation(parentColumn = "id", entityColumn = "tradeId")
    val items: List<TradeItem>
) {
    val outItems: List<TradeItem> get() = items.filter { it.direction == TradeItem.DIRECTION_OUT }
    val inItems: List<TradeItem> get() = items.filter { it.direction == TradeItem.DIRECTION_IN }

    /** Market value of the cards the store gave away. */
    val outTotal: Double get() = outItems.sumOf { it.lineValue }

    /** Market value of the cards the store received. */
    val inTotal: Double get() = inItems.sumOf { it.lineValue }

    /** Cash movement signed from the store's side: positive = cash came in. */
    val cashReceived: Double
        get() = if (trade.cashDirection == Trade.CASH_STORE_RECEIVES) trade.cashAmount
        else -trade.cashAmount

    /** (in + cash) − out, all at trade values. $0 = fair at market. */
    val valueSwing: Double get() = inTotal + cashReceived - outTotal

    /** True when every OUT line carries a cost basis (so [margin] is real). */
    val hasFullCostBasis: Boolean get() = outItems.all { it.costBasis != null }

    /** (in + cash) − cost of the OUT cards; null when cost basis is incomplete. */
    val margin: Double?
        get() = if (hasFullCostBasis) {
            inTotal + cashReceived - outItems.sumOf { it.quantity * (it.costBasis ?: 0.0) }
        } else null

    /** Headline number: margin over cost when available, else the value swing. */
    val valueAdded: Double get() = margin ?: valueSwing

    /** Total number of physical cards on both sides. */
    val itemQuantity: Int get() = items.sumOf { it.quantity }
}
