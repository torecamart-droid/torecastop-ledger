package com.torecastop.ledger.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One photo attached to a [Sale] — either to the sale as a whole, or to one
 * specific [SaleItem] line. (v1.3 revision)
 *
 * [saleItemId] is the switch: null means "covers the whole sale" (e.g. a
 * receipt or the customer's card at checkout); set means "this specific
 * item" (e.g. a close-up of a graded card's condition). A sale or item can
 * have any number of photos — this replaces the old single [Sale.photoPath].
 *
 * Deleting the parent sale cascades to its photos. Deleting the parent item
 * also cascades (an item-level photo can't outlive its item).
 */
@Entity(
    tableName = "sale_photos",
    foreignKeys = [
        ForeignKey(
            entity = Sale::class,
            parentColumns = ["id"],
            childColumns = ["saleId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SaleItem::class,
            parentColumns = ["id"],
            childColumns = ["saleItemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("saleId"), Index("saleItemId")]
)
data class SalePhoto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long = 0,
    /** Null = a whole-sale photo. Set = a photo of this specific item line. */
    val saleItemId: Long? = null,
    /** Absolute path to the photo in app storage. */
    val photoPath: String,
    /** Epoch millis when the photo was captured. */
    val timestamp: Long
)
