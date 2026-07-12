package com.torecastop.ledger.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One card trade — the header for the [TradeItem] lines on both sides of the
 * swap. The store GIVES cards from stock (scanned, they have SKUs) and
 * RECEIVES the customer's cards (entered manually, no SKU yet), optionally
 * with cash on top in one direction.
 *
 * Like [Sale], the note, photo and timestamp cover the whole trade, and the
 * item lines are written atomically with the header.
 */
@Entity(
    tableName = "trades",
    foreignKeys = [
        ForeignKey(
            entity = Session::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class Trade(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    /** Optional note covering the whole trade. */
    val note: String? = null,
    /** Absolute path to a photo in app storage; null if none taken. */
    val photoPath: String? = null,
    /** Epoch millis captured automatically at save time. */
    val timestamp: Long,
    /** Cash on top of the card swap, in dollars; 0.0 when the swap is cards-only. */
    val cashAmount: Double = 0.0,
    /** [CASH_STORE_RECEIVES] or [CASH_STORE_PAYS]; only meaningful when cashAmount > 0. */
    val cashDirection: String = CASH_STORE_RECEIVES
) {
    companion object {
        const val CASH_STORE_RECEIVES = "store_receives"
        const val CASH_STORE_PAYS = "store_pays"
    }
}
