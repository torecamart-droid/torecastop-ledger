package com.torecastop.ledger.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A selling event — one market day or convention.
 *
 * Only ONE session may be active at a time (enforced in [LedgerRepository]).
 * The session name is auto-generated from the date it was opened.
 */
@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Display name, auto-generated from the open date, e.g. "01 Jul 2026". */
    val name: String,
    /** Epoch millis when the session was opened. */
    val startTime: Long,
    /** Epoch millis when closed; null while the session is still active. */
    val endTime: Long? = null,
    /** [STATUS_ACTIVE] or [STATUS_CLOSED]. */
    val status: String = STATUS_ACTIVE,
    /**
     * Optional free-text show/event name, e.g. "PAX Unplugged". Shown next to
     * the auto-generated date [name] so past sessions stay identifiable. (v1.3)
     */
    val label: String? = null,
    /** Cash physically in the drawer at open, if the float was counted. (v1.3) */
    val startingFloat: Double? = null,
    /** Cash counted in the drawer at close, if it was counted. (v1.3) */
    val countedCash: Double? = null,
    /** Photo of the counted drawer at close, if one was taken. (v1.3) */
    val cashCountPhotoPath: String? = null
) {
    val isActive: Boolean get() = status == STATUS_ACTIVE

    companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_CLOSED = "closed"
    }
}
