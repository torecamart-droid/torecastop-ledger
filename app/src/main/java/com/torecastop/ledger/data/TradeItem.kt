package com.torecastop.ledger.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One card line on one side of a [Trade].
 *
 * The two sides are not symmetrical:
 *  - [DIRECTION_OUT] lines are the store's stock — they carry a [sku]
 *    (scanned from the printed label).
 *  - [DIRECTION_IN] lines are the customer's cards — no SKU yet, identified by
 *    [cardName]; a real SKU is assigned later during the normal intake flow.
 *
 * Each line carries a plain [saleCost] — what the card is valued at in this
 * deal. No market-value/margin maths (scrapped in the v1.3 revision).
 *
 * Deleting the parent trade cascades to its items.
 */
@Entity(
    tableName = "trade_items",
    foreignKeys = [
        ForeignKey(
            entity = Trade::class,
            parentColumns = ["id"],
            childColumns = ["tradeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tradeId")]
)
data class TradeItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tradeId: Long = 0,
    /** [DIRECTION_OUT] (store gives) or [DIRECTION_IN] (store receives). */
    val direction: String,
    /** TorecaStop SKU — set for OUT lines, null for IN lines (no SKU yet). */
    val sku: String? = null,
    /** Free-text card name — the identifier for IN lines. */
    val cardName: String? = null,
    val quantity: Int = 1,
    /**
     * Per-unit value the card is counted as in this deal, in dollars.
     * Column kept as `tradeValue` — same data, renamed at the Kotlin level
     * only, so no migration is needed for the rename itself.
     */
    @ColumnInfo(name = "tradeValue")
    val saleCost: Double,
    /**
     * Deprecated — acquisition cost was removed from trade entry/display/
     * export after user feedback; the column (`costBasis`, dating to v2)
     * stays in the schema unused rather than forcing a DROP COLUMN
     * migration for a field nothing ever wrote much data into. Always null
     * on new/edited trades.
     */
    @ColumnInfo(name = "costBasis")
    val acquisitionCost: Double? = null,
    /**
     * Optional per-line note — e.g. a serial number or condition detail for a
     * specific card. Distinct from the trade-level [Trade.note]. (v1.3)
     */
    val note: String? = null
) {
    /** Line total = quantity × per-unit sale cost. Not stored; computed on read. */
    @get:Ignore
    val lineValue: Double get() = quantity * saleCost

    /** Display identifier: card name when present, otherwise the SKU. */
    @get:Ignore
    val label: String get() = cardName?.takeIf { it.isNotBlank() } ?: sku.orEmpty()

    companion object {
        const val DIRECTION_OUT = "out"
        const val DIRECTION_IN = "in"
    }
}
