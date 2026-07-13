package com.torecastop.ledger.ui.session

import com.torecastop.ledger.data.SaleItem

/**
 * A mutable, in-progress sale item held in the entry cart or the edit dialog.
 * Price is kept as text so partial input (e.g. "1.") is preserved while typing.
 */
data class DraftItem(
    val sku: String = "",
    val quantity: Int = 1,
    val priceText: String = "",
    /** Optional per-line note — serial number, condition, etc. (v1.3) */
    val note: String = ""
) {
    val price: Double? get() = priceText.toDoubleOrNull()

    val isValid: Boolean
        get() = sku.isNotBlank() && (price?.let { it >= 0 } == true) && quantity >= 1

    val subtotal: Double get() = (price ?: 0.0) * quantity

    fun toSaleItem(): SaleItem =
        SaleItem(
            sku = sku.trim(),
            quantity = quantity,
            price = price ?: 0.0,
            note = note.trim().ifBlank { null }
        )

    companion object {
        fun from(item: SaleItem): DraftItem =
            DraftItem(
                sku = item.sku,
                quantity = item.quantity,
                priceText = String.format(java.util.Locale.US, "%.2f", item.price),
                note = item.note ?: ""
            )
    }
}
