package com.torecastop.ledger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TradeItemDao {

    @Insert
    suspend fun insertAll(items: List<TradeItem>)

    /** Removes every line of a trade — used when replacing items during an edit. */
    @Query("DELETE FROM trade_items WHERE tradeId = :tradeId")
    suspend fun deleteForTrade(tradeId: Long)
}
