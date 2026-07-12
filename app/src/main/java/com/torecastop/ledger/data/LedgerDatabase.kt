package com.torecastop.ledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Session::class, Sale::class, SaleItem::class, Trade::class, TradeItem::class],
    version = 3,
    exportSchema = false
)
abstract class LedgerDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun saleDao(): SaleDao
    abstract fun saleItemDao(): SaleItemDao
    abstract fun tradeDao(): TradeDao
    abstract fun tradeItemDao(): TradeItemDao

    companion object {
        @Volatile private var INSTANCE: LedgerDatabase? = null

        /** v3 adds the trade tables. Purely additive, so existing data is kept. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `trades` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sessionId` INTEGER NOT NULL, " +
                        "`note` TEXT, " +
                        "`photoPath` TEXT, " +
                        "`timestamp` INTEGER NOT NULL, " +
                        "`cashAmount` REAL NOT NULL, " +
                        "`cashDirection` TEXT NOT NULL, " +
                        "FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_trades_sessionId` ON `trades` (`sessionId`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `trade_items` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`tradeId` INTEGER NOT NULL, " +
                        "`direction` TEXT NOT NULL, " +
                        "`sku` TEXT, " +
                        "`cardName` TEXT, " +
                        "`quantity` INTEGER NOT NULL, " +
                        "`tradeValue` REAL NOT NULL, " +
                        "`costBasis` REAL, " +
                        "FOREIGN KEY(`tradeId`) REFERENCES `trades`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_trade_items_tradeId` ON `trade_items` (`tradeId`)"
                )
            }
        }

        fun get(context: Context): LedgerDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LedgerDatabase::class.java,
                    "torecastop_ledger.db"
                )
                    // v2→v3 (trade tables) migrates in place, keeping data.
                    .addMigrations(MIGRATION_2_3)
                    // Only the pre-multi-item v1 is destructive on upgrade
                    // (confirmed clean-reset choice when v2 shipped).
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
