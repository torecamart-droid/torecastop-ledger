package com.torecastop.ledger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SalePhotoDao {

    @Insert
    suspend fun insertAll(photos: List<SalePhoto>)

    /** Removes every photo of a sale — used when replacing photos during an edit. */
    @Query("DELETE FROM sale_photos WHERE saleId = :saleId")
    suspend fun deleteForSale(saleId: Long)

    /** All photos for a sale (whole-sale and per-item), for export/edit. */
    @Query("SELECT * FROM sale_photos WHERE saleId = :saleId ORDER BY timestamp")
    suspend fun getForSale(saleId: Long): List<SalePhoto>
}
