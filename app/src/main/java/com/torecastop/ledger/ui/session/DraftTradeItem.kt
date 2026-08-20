package com.torecastop.ledger.ui.session

import com.torecastop.ledger.data.TradeItem
import java.util.Locale

/**
 * A mutable, in-progress trade line held in the trade entry form. Money fields
 * are kept as text so partial input (e.g. "1.") is preserved while typing.
 *
 * OUT lines are identified by SKU (scanned or typed); IN lines are identified
 * by card name (decision T5). Both sides carry just a sale cost — no
 * acquisition cost (removed from entry/display/export), no market-value/
 * margin maths (v1.3 revision).
 */
data class DraftTradeItem(
    val direction: String,
    val sku: String = "",
    val cardName: String = "",
    val quantity: Int = 1,
    val saleCostText: String = "",
    /** Optional per-line note — serial number, condition, etc. (v1.3) */
    val note: String = "",
    /**
     * Photos captured for this specific card, not yet persisted — UI-only
     * state; [TradeItem] itself carries no photo field, they live in the
     * separate [TradePhoto] table once saved. (v1.3 revision)
     */
    val photoPaths: List<String> = emptyList()
) {
    val saleCost: Double? get() = saleCostText.toDoubleOrNull()

    val isValid: Boolean
        get() {
            val v = saleCost ?: return false
            val identified =
                if (direction == TradeItem.DIRECTION_OUT) sku.isNotBlank()
                else cardName.isNotBlank()
            return identified && v >= 0 && quantity >= 1
        }

    val lineValue: Double get() = (saleCost ?: 0.0) * quantity

    /** Display identifier, mirroring [TradeItem.label]. */
    val label: String
        get() = if (direction == TradeItem.DIRECTION_OUT) sku.trim() else cardName.trim()

    fun toTradeItem(): TradeItem =
        TradeItem(
            direction = direction,
            sku = sku.trim().ifBlank { null },
            cardName = cardName.trim().ifBlank { null },
            quantity = quantity,
            saleCost = saleCost ?: 0.0,
            note = note.trim().ifBlank { null }
        )

    companion object {
        fun from(item: TradeItem, photoPaths: List<String> = emptyList()): DraftTradeItem =
            DraftTradeItem(
                direction = item.direction,
                sku = item.sku ?: "",
                cardName = item.cardName ?: "",
                quantity = item.quantity,
                saleCostText = String.format(Locale.US, "%.2f", item.saleCost),
                note = item.note ?: "",
                photoPaths = photoPaths
            )
    }
}
