package com.torecastop.ledger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TradePhotoDao {

    @Insert
    suspend fun insertAll(photos: List<TradePhoto>)

    /** Removes every photo of a trade — used when replacing photos during an edit. */
    @Query("DELETE FROM trade_photos WHERE tradeId = :tradeId")
    suspend fun deleteForTrade(tradeId: Long)

    /** All photos for a trade (whole-trade and per-card), for export/edit. */
    @Query("SELECT * FROM trade_photos WHERE tradeId = :tradeId ORDER BY timestamp")
    suspend fun getForTrade(tradeId: Long): List<TradePhoto>
}
