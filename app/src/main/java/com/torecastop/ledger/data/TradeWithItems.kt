package com.torecastop.ledger.data

import androidx.room.Embedded
import androidx.room.Relation

/**
 * A [Trade] together with its [TradeItem] lines — the shape the UI and export
 * use.
 *
 * No margin/value-added calculation (scrapped in this v1.3 revision, per the
 * planning doc): just plain totals of what was recorded on each side. Sale
 * cost and acquisition cost are kept per line for the record, not combined
 * into a computed profit figure.
 */
data class TradeWithItems(
    @Embedded val trade: Trade,
    @Relation(parentColumn = "id", entityColumn = "tradeId")
    val items: List<TradeItem>,
    /** All photos for the trade — both whole-trade and per-card. (v1.3 revision) */
    @Relation(parentColumn = "id", entityColumn = "tradeId")
    val photos: List<TradePhoto> = emptyList()
) {
    val outItems: List<TradeItem> get() = items.filter { it.direction == TradeItem.DIRECTION_OUT }
    val inItems: List<TradeItem> get() = items.filter { it.direction == TradeItem.DIRECTION_IN }

    /** Photos attached to the trade as a whole (not tied to one card). */
    val tradePhotos: List<TradePhoto> get() = photos.filter { it.tradeItemId == null }

    /** Photos attached to one specific card line. */
    fun photosFor(tradeItemId: Long): List<TradePhoto> =
        photos.filter { it.tradeItemId == tradeItemId }

    /** Total sale cost of the cards the store gave away. */
    val outTotal: Double get() = outItems.sumOf { it.lineValue }

    /** Total sale cost of the cards the store received. */
    val inTotal: Double get() = inItems.sumOf { it.lineValue }

    /** Cash movement signed from the store's side: positive = cash came in. */
    val cashReceived: Double
        get() = if (trade.cashDirection == Trade.CASH_STORE_RECEIVES) trade.cashAmount
        else -trade.cashAmount

    /** Total number of physical cards on both sides. */
    val itemQuantity: Int get() = items.sumOf { it.quantity }
}
