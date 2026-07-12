package com.torecastop.ledger

import android.app.Application
import com.torecastop.ledger.data.LedgerDatabase
import com.torecastop.ledger.data.LedgerRepository

/**
 * Holds the app-wide database and repository so every screen shares one
 * instance. Registered in AndroidManifest.xml via android:name=".LedgerApplication".
 */
class LedgerApplication : Application() {

    val database: LedgerDatabase by lazy { LedgerDatabase.get(this) }

    val repository: LedgerRepository by lazy {
        LedgerRepository(database)
    }
}
