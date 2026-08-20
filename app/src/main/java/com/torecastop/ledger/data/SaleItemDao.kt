package com.torecastop.ledger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SaleItemDao {

    /** Returns the generated ids, same order as [items] — used to attach photos. */
    @Insert
    suspend fun insertAll(items: List<SaleItem>): List<Long>

    /** Removes every line of a sale — used when replacing items during an edit. */
    @Query("DELETE FROM sale_items WHERE saleId = :saleId")
    suspend fun deleteForSale(saleId: Long)
}
