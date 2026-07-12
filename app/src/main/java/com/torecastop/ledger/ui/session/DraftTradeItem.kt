package com.torecastop.ledger.ui.session

import com.torecastop.ledger.data.TradeItem
import java.util.Locale

/**
 * A mutable, in-progress trade line held in the trade entry form. Money fields
 * are kept as text so partial input (e.g. "1.") is preserved while typing.
 *
 * OUT lines are identified by SKU (scanned or typed) and may carry an optional
 * cost basis; IN lines are identified by card name and never carry one
 * (decisions T2 and T5).
 */
data class DraftTradeItem(
    val direction: String,
    val sku: String = "",
    val cardName: String = "",
    val quantity: Int = 1,
    val valueText: String = "",
    val costText: String = ""
) {
    val value: Double? get() = valueText.toDoubleOrNull()
    val costBasis: Double? get() = costText.toDoubleOrNull()

    val isValid: Boolean
        get() {
            val v = value ?: return false
            val identified =
                if (direction == TradeItem.DIRECTION_OUT) sku.isNotBlank()
                else cardName.isNotBlank()
            return identified && v >= 0 && quantity >= 1
        }

    val lineValue: Double get() = (value ?: 0.0) * quantity

    /** Display identifier, mirroring [TradeItem.label]. */
    val label: String
        get() = if (direction == TradeItem.DIRECTION_OUT) sku.trim() else cardName.trim()

    fun toTradeItem(): TradeItem =
        TradeItem(
            direction = direction,
            sku = sku.trim().ifBlank { null },
            cardName = cardName.trim().ifBlank { null },
            quantity = quantity,
            tradeValue = value ?: 0.0,
            costBasis = if (direction == TradeItem.DIRECTION_OUT) costBasis else null
        )

    companion object {
        fun from(item: TradeItem): DraftTradeItem =
            DraftTradeItem(
                direction = item.direction,
                sku = item.sku ?: "",
                cardName = item.cardName ?: "",
                quantity = item.quantity,
                valueText = String.format(Locale.US, "%.2f", item.tradeValue),
                costText = item.costBasis?.let { String.format(Locale.US, "%.2f", it) } ?: ""
            )
    }
}
