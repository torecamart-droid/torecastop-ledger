package com.torecastop.ledger.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SaleDao {

    @Insert
    suspend fun insert(sale: Sale): Long

    @Update
    suspend fun update(sale: Sale)

    @Delete
    suspend fun delete(sale: Sale)

    /** Live list of a session's sales with their items, newest first. */
    @Transaction
    @Query("SELECT * FROM sales WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    fun observeSalesForSession(sessionId: Long): Flow<List<SaleWithItems>>

    /** One-shot fetch in chronological order (used when building the export). */
    @Transaction
    @Query("SELECT * FROM sales WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getSalesForSession(sessionId: Long): List<SaleWithItems>

    /** Live sum of item quantities across the session — feeds the "items" total. */
    @Query(
        "SELECT COALESCE(SUM(si.quantity), 0) FROM sale_items si " +
            "INNER JOIN sales s ON si.saleId = s.id WHERE s.sessionId = :sessionId"
    )
    fun observeItemCountForSession(sessionId: Long): Flow<Int>

    /** Live sum of quantity × price across the session — feeds the "$" total. */
    @Query(
        "SELECT COALESCE(SUM(si.quantity * si.price), 0) FROM sale_items si " +
            "INNER JOIN sales s ON si.saleId = s.id WHERE s.sessionId = :sessionId"
    )
    fun observeTotalForSession(sessionId: Long): Flow<Double>
}
