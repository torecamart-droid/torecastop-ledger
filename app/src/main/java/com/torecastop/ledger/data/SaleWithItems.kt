package com.torecastop.ledger.data

import androidx.room.Embedded
import androidx.room.Relation

/**
 * A [Sale] together with its [SaleItem] lines and [SalePhoto]s — the shape the
 * UI and export use.
 */
data class SaleWithItems(
    @Embedded val sale: Sale,
    @Relation(parentColumn = "id", entityColumn = "saleId")
    val items: List<SaleItem>,
    /** All photos for the sale — both whole-sale and per-item. (v1.3 revision) */
    @Relation(parentColumn = "id", entityColumn = "saleId")
    val photos: List<SalePhoto> = emptyList()
) {
    /** Money total for the whole transaction. */
    val total: Double get() = items.sumOf { it.subtotal }

    /** Total number of physical items (sum of line quantities). */
    val itemQuantity: Int get() = items.sumOf { it.quantity }

    /** Cash received minus the total; null when cash received wasn't recorded. (v1.3) */
    val changeDue: Double? get() = sale.cashReceived?.let { it - total }

    /** Photos attached to the sale as a whole (not tied to one item). */
    val salePhotos: List<SalePhoto> get() = photos.filter { it.saleItemId == null }

    /** Photos attached to one specific item line. */
    fun photosFor(saleItemId: Long): List<SalePhoto> =
        photos.filter { it.saleItemId == saleItemId }
}
