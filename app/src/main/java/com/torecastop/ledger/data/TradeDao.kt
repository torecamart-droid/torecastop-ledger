package com.torecastop.ledger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TradeDao {

    @Insert
    suspend fun insert(trade: Trade): Long

    @Update
    suspend fun update(trade: Trade)

    /** Removes a trade by id; the FK cascade removes its item lines. */
    @Query("DELETE FROM trades WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Live list of a session's trades with their items, newest first. */
    @Transaction
    @Query("SELECT * FROM trades WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    fun observeTradesForSession(sessionId: Long): Flow<List<TradeWithItems>>

    /** One-shot fetch in chronological order (used when building the export). */
    @Transaction
    @Query("SELECT * FROM trades WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getTradesForSession(sessionId: Long): List<TradeWithItems>
}
