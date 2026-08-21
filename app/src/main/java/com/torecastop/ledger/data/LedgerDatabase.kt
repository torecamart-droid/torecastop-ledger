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
        CashAdjustment::class, SalePhoto::class, TradePhoto::class
    ],
    version = 6,
    exportSchema = false
)
abstract class LedgerDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun saleDao(): SaleDao
    abstract fun saleItemDao(): SaleItemDao
    abstract fun tradeDao(): TradeDao
    abstract fun tradeItemDao(): TradeItemDao
    abstract fun cashAdjustmentDao(): CashAdjustmentDao
    abstract fun salePhotoDao(): SalePhotoDao
    abstract fun tradePhotoDao(): TradePhotoDao

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

        /**
         * v5 is the v1.3 planning-doc revision — all additive, existing data
         * kept. `tradeValue` is NOT touched here: it's renamed at the Kotlin
         * level only (see [TradeItem.saleCost]) — same column, so no SQL is
         * needed for that part. `costBasis` (→ [TradeItem.acquisitionCost])
         * is likewise untouched — that field was later dropped from entry/
         * display/export, but the column stays in the schema, just unused.
         *  - sale_photos / trade_photos tables (multi-photo, whole + per-item)
         *  - sales.cashReceived (cash-received / change-due prompt)
         *  - trades.customerPhone / trades.customerEmail (seller contact)
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sales` ADD COLUMN `cashReceived` REAL")
                db.execSQL("ALTER TABLE `trades` ADD COLUMN `customerPhone` TEXT")
                db.execSQL("ALTER TABLE `trades` ADD COLUMN `customerEmail` TEXT")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sale_photos` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`saleId` INTEGER NOT NULL, " +
                        "`saleItemId` INTEGER, " +
                        "`photoPath` TEXT NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`saleId`) REFERENCES `sales`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(`saleItemId`) REFERENCES `sale_items`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sale_photos_saleId` ON `sale_photos` (`saleId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sale_photos_saleItemId` ON `sale_photos` (`saleItemId`)"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `trade_photos` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`tradeId` INTEGER NOT NULL, " +
                        "`tradeItemId` INTEGER, " +
                        "`photoPath` TEXT NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`tradeId`) REFERENCES `trades`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(`tradeItemId`) REFERENCES `trade_items`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_trade_photos_tradeId` ON `trade_photos` (`tradeId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_trade_photos_tradeItemId` ON `trade_photos` (`tradeItemId`)"
                )

                // Carry forward any existing single sale/trade photo into the
                // new whole-transaction (saleItemId/tradeItemId = NULL) shape,
                // so nothing already captured is silently lost.
                db.execSQL(
                    "INSERT INTO sale_photos (saleId, saleItemId, photoPath, timestamp) " +
                        "SELECT id, NULL, photoPath, timestamp FROM sales WHERE photoPath IS NOT NULL"
                )
                db.execSQL(
                    "INSERT INTO trade_photos (tradeId, tradeItemId, photoPath, timestamp) " +
                        "SELECT id, NULL, photoPath, timestamp FROM trades WHERE photoPath IS NOT NULL"
                )
            }
        }

        /**
         * v6 adds full name and a free-text address to customer contact
         * capture (v1.4 self-serve intake) — additive, existing data kept.
         *  - trades.customerName / trades.customerAddress
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `trades` ADD COLUMN `customerName` TEXT")
                db.execSQL("ALTER TABLE `trades` ADD COLUMN `customerAddress` TEXT")
            }
        }

        fun get(context: Context): LedgerDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LedgerDatabase::class.java,
                    "torecastop_ledger.db"
                )
                    // v2→v3 (trade tables), v3→v4 (v1.3 notes/cash batch),
                    // v4→v5 (v1.3 revision: multi-photo, cash-received, seller
                    // contact), and v5→v6 (v1.4: customer name/address) all
                    // migrate in place, keeping data.
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    // Only the pre-multi-item v1 is destructive on upgrade
                    // (confirmed clean-reset choice when v2 shipped).
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
