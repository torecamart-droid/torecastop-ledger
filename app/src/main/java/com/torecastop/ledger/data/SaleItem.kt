package com.torecastop.ledger.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single line within a [Sale] — one SKU at a quantity and unit price.
 * Deleting the parent sale cascades to its items.
 */
@Entity(
    tableName = "sale_items",
    foreignKeys = [
        ForeignKey(
            entity = Sale::class,
            parentColumns = ["id"],
            childColumns = ["saleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("saleId")]
)
data class SaleItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long = 0,
    /** Scanned barcode value, or manually entered SKU. */
    val sku: String,
    val quantity: Int = 1,
    /** Per-unit price in dollars. */
    val price: Double,
    /**
     * Optional per-line note — e.g. a serial number or condition detail for a
     * specific card. Distinct from the sale-level [Sale.note]. (v1.3)
     */
    val note: String? = null
) {
    /** Line total = quantity × unit price. Not stored; computed on read. */
    @get:Ignore
    val subtotal: Double get() = quantity * price
}
