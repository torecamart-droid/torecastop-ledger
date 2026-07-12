package com.torecastop.ledger.data

import androidx.room.Embedded
import androidx.room.Relation

/**
 * A [Sale] together with its [SaleItem] lines — the shape the UI and export use.
 */
data class SaleWithItems(
    @Embedded val sale: Sale,
    @Relation(parentColumn = "id", entityColumn = "saleId")
    val items: List<SaleItem>
) {
    /** Money total for the whole transaction. */
    val total: Double get() = items.sumOf { it.subtotal }

    /** Total number of physical items (sum of line quantities). */
    val itemQuantity: Int get() = items.sumOf { it.quantity }
}
