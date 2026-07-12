package com.torecastop.ledger.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Single access point for the ledger data layer. All business rules live here:
 *  - exactly one active session at a time
 *  - sessions are auto-named from the date they were opened
 *  - timestamps are stamped at save time
 *  - a sale is a transaction with one or more item lines, written atomically
 */
class LedgerRepository(private val db: LedgerDatabase) {

    private val sessionDao = db.sessionDao()
    private val saleDao = db.saleDao()
    private val saleItemDao = db.saleItemDao()

    // --- Observation (for the UI) ---

    fun observeActiveSession(): Flow<Session?> = sessionDao.observeActiveSession()
    fun observeAllSessions(): Flow<List<Session>> = sessionDao.observeAllSessions()

    fun observeSales(sessionId: Long): Flow<List<SaleWithItems>> =
        saleDao.observeSalesForSession(sessionId)

    fun observeItemCount(sessionId: Long): Flow<Int> =
        saleDao.observeItemCountForSession(sessionId)

    fun observeSessionTotal(sessionId: Long): Flow<Double> =
        saleDao.observeTotalForSession(sessionId)

    // --- Sessions ---

    /**
     * Returns the current active session, opening a new one (named for today's
     * date) if none exists. Guarantees a single active session at any time.
     */
    suspend fun getOrStartActiveSession(): Session {
        sessionDao.getActiveSession()?.let { return it }
        val now = System.currentTimeMillis()
        val id = sessionDao.insert(
            Session(name = dateFormat.format(Date(now)), startTime = now)
        )
        return sessionDao.getById(id)!!
    }

    /** Closes a session. After this it is locked; a new one can be opened. */
    suspend fun closeSession(session: Session) {
        sessionDao.update(
            session.copy(
                endTime = System.currentTimeMillis(),
                status = Session.STATUS_CLOSED
            )
        )
    }

    // --- Sales ---

    /**
     * Records a sale and its item lines atomically, stamping the current time.
     * [items] carry sku/quantity/price; their saleId is filled in here.
     */
    suspend fun addSale(
        sessionId: Long,
        items: List<SaleItem>,
        note: String?,
        photoPath: String?
    ): Long = db.withTransaction {
        val saleId = saleDao.insert(
            Sale(
                sessionId = sessionId,
                note = note?.ifBlank { null },
                photoPath = photoPath,
                timestamp = System.currentTimeMillis()
            )
        )
        saleItemDao.insertAll(items.map { it.copy(id = 0, saleId = saleId) })
        saleId
    }

    /**
     * Updates an existing sale's note/photo and replaces its item lines
     * (inline editing while the session is active).
     */
    suspend fun updateSale(sale: Sale, items: List<SaleItem>) = db.withTransaction {
        saleDao.update(sale.copy(note = sale.note?.ifBlank { null }))
        saleItemDao.deleteForSale(sale.id)
        saleItemDao.insertAll(items.map { it.copy(id = 0, saleId = sale.id) })
    }

    suspend fun deleteSale(sale: Sale) = saleDao.delete(sale)

    /** Chronological sales (with items) for building the export bundle. */
    suspend fun getSalesForExport(sessionId: Long): List<SaleWithItems> =
        saleDao.getSalesForSession(sessionId)

    companion object {
        private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    }
}
