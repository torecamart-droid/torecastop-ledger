package com.torecastop.ledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Session::class, Sale::class, SaleItem::class, Trade::class, TradeItem::class,
        CashAdjustment::class
    ],
    version = 4,
    exportSchema = false
)
abstract class LedgerDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun saleDao(): SaleDao
    abstract fun saleItemDao(): SaleItemDao
    abstract fun tradeDao(): TradeDao
    abstract fun tradeItemDao(): TradeItemDao
    abstract fun cashAdjustmentDao(): CashAdjustmentDao

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

        /**
         * v4 is the v1.3 batch — all additive, so existing data is kept:
         *  - per-line notes on sale/trade items (serial numbers, condition)
         *  - session show/event label + cash-reconciliation fields
         *  - the cash_adjustments table (paid-out / cash-in log)
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sale_items` ADD COLUMN `note` TEXT")
                db.execSQL("ALTER TABLE `trade_items` ADD COLUMN `note` TEXT")

                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `label` TEXT")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `startingFloat` REAL")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `countedCash` REAL")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `cashCountPhotoPath` TEXT")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cash_adjustments` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sessionId` INTEGER NOT NULL, " +
                        "`amount` REAL NOT NULL, " +
                        "`reason` TEXT NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_cash_adjustments_sessionId` ON `cash_adjustments` (`sessionId`)"
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
                    // v2→v3 (trade tables) and v3→v4 (v1.3 notes/cash batch)
                    // both migrate in place, keeping data.
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    // Only the pre-multi-item v1 is destructive on upgrade
                    // (confirmed clean-reset choice when v2 shipped).
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
