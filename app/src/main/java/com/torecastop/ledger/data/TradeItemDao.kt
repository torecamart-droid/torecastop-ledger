package com.torecastop.ledger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TradeItemDao {

    /** Returns the generated ids, same order as [items] — used to attach photos. */
    @Insert
    suspend fun insertAll(items: List<TradeItem>): List<Long>

    /** Removes every line of a trade — used when replacing items during an edit. */
    @Query("DELETE FROM trade_items WHERE tradeId = :tradeId")
    suspend fun deleteForTrade(tradeId: Long)
}
