package com.torecastop.ledger.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One photo attached to a [Trade] — either to the trade as a whole, or to one
 * specific [TradeItem] card line. Mirrors [SalePhoto]. (v1.3 revision)
 *
 * [tradeItemId] null = covers the whole trade; set = this specific card. A
 * trade or card line can have any number of photos — this replaces the old
 * single [Trade.photoPath].
 */
@Entity(
    tableName = "trade_photos",
    foreignKeys = [
        ForeignKey(
            entity = Trade::class,
            parentColumns = ["id"],
            childColumns = ["tradeId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TradeItem::class,
            parentColumns = ["id"],
            childColumns = ["tradeItemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tradeId"), Index("tradeItemId")]
)
data class TradePhoto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tradeId: Long = 0,
    /** Null = a whole-trade photo. Set = a photo of this specific card line. */
    val tradeItemId: Long? = null,
    /** Absolute path to the photo in app storage. */
    val photoPath: String,
    /** Epoch millis when the photo was captured. */
    val timestamp: Long
)
