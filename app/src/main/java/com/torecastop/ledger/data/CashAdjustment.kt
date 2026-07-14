package com.torecastop.ledger.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A cash movement in or out of the drawer that isn't a sale or a trade — making
 * change for a neighbouring stall, a supply run, topping up the float. Feeds the
 * end-of-day cash reconciliation so a legitimate movement doesn't read as a
 * variance. (v1.3)
 *
 * Deleting the parent session cascades to its adjustments.
 */
@Entity(
    tableName = "cash_adjustments",
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
data class CashAdjustment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long = 0,
    /**
     * Signed dollars from the drawer's point of view: positive = cash added to
     * the drawer, negative = cash paid out. The reconciliation sums these into
     * the expected-cash figure.
     */
    val amount: Double,
    /** Why the cash moved — free text, e.g. "change for stall 12", "lunch run". */
    val reason: String,
    /** Epoch millis when the adjustment was recorded. */
    val timestamp: Long
) {
    companion object {
        const val DIRECTION_IN = "in"
        const val DIRECTION_OUT = "out"
    }
}
