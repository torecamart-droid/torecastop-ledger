package com.torecastop.ledger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CashAdjustmentDao {

    @Insert
    suspend fun insert(adjustment: CashAdjustment): Long

    /** Removes one adjustment — backs the "Undo"/delete on a logged entry. */
    @Query("DELETE FROM cash_adjustments WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Live adjustments for a session, oldest first — drives the active UI. */
    @Query("SELECT * FROM cash_adjustments WHERE sessionId = :sessionId ORDER BY timestamp")
    fun observeForSession(sessionId: Long): Flow<List<CashAdjustment>>

    /** One-shot adjustments for a session — reconciliation, history, export. */
    @Query("SELECT * FROM cash_adjustments WHERE sessionId = :sessionId ORDER BY timestamp")
    suspend fun getForSession(sessionId: Long): List<CashAdjustment>

    /** Signed net of every adjustment in a session; 0 when there are none. */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM cash_adjustments WHERE sessionId = :sessionId")
    fun observeNetForSession(sessionId: Long): Flow<Double>
}
