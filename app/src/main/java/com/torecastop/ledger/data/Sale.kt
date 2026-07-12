package com.torecastop.ledger.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One cash transaction (a "sale") — the header for one or more [SaleItem] lines.
 *
 * A single customer checkout can contain several different cards, so the item
 * lines live in [SaleItem]; the note, photo and timestamp belong to the whole
 * sale. Each sale is its own transaction (no auto-merging across sales).
 */
@Entity(
    tableName = "sales",
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
data class Sale(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    /** Optional note covering the whole transaction. */
    val note: String? = null,
    /** Absolute path to a photo in app storage; null if none taken. */
    val photoPath: String? = null,
    /** Epoch millis captured automatically at save time. */
    val timestamp: Long
)
