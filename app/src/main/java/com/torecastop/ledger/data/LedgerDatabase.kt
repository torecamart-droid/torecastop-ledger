package com.torecastop.ledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Session::class, Sale::class, SaleItem::class],
    version = 2,
    exportSchema = false
)
abstract class LedgerDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun saleDao(): SaleDao
    abstract fun saleItemDao(): SaleItemDao

    companion object {
        @Volatile private var INSTANCE: LedgerDatabase? = null

        fun get(context: Context): LedgerDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LedgerDatabase::class.java,
                    "torecastop_ledger.db"
                )
                    // v2 splits Sale into a header + SaleItem lines. Existing v1
                    // data is discarded on upgrade (confirmed clean-reset choice).
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
