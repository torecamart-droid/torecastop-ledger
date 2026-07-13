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
 *  - a trade mirrors a sale: a header plus OUT/IN item lines, written atomically
 */
class LedgerRepository(private val db: LedgerDatabase) {

    private val sessionDao = db.sessionDao()
    private val saleDao = db.saleDao()
    private val saleItemDao = db.saleItemDao()
    private val tradeDao = db.tradeDao()
    private val tradeItemDao = db.tradeItemDao()
    private val cashAdjustmentDao = db.cashAdjustmentDao()

    // --- Observation (for the UI) ---

    fun observeActiveSession(): Flow<Session?> = sessionDao.observeActiveSession()
    fun observeAllSessions(): Flow<List<Session>> = sessionDao.observeAllSessions()

    fun observeSales(sessionId: Long): Flow<List<SaleWithItems>> =
        saleDao.observeSalesForSession(sessionId)

    fun observeItemCount(sessionId: Long): Flow<Int> =
        saleDao.observeItemCountForSession(sessionId)

    fun observeSessionTotal(sessionId: Long): Flow<Double> =
        saleDao.observeTotalForSession(sessionId)

    fun observeTrades(sessionId: Long): Flow<List<TradeWithItems>> =
        tradeDao.observeTradesForSession(sessionId)

    fun observeCashAdjustments(sessionId: Long): Flow<List<CashAdjustment>> =
        cashAdjustmentDao.observeForSession(sessionId)

    fun observeCashAdjustmentNet(sessionId: Long): Flow<Double> =
        cashAdjustmentDao.observeNetForSession(sessionId)

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

    /** Sets or clears the optional show/event label on a session. (v1.3) */
    suspend fun setSessionLabel(sessionId: Long, label: String?) {
        val session = sessionDao.getById(sessionId) ?: return
        sessionDao.update(session.copy(label = label?.trim()?.ifBlank { null }))
    }

    /** Records the starting cash float when a session is opened. (v1.3) */
    suspend fun setStartingFloat(sessionId: Long, startingFloat: Double?) {
        val session = sessionDao.getById(sessionId) ?: return
        sessionDao.update(session.copy(startingFloat = startingFloat))
    }

    /**
     * Closes a session, recording the end-of-day cash count and optional
     * drawer photo alongside the close. (v1.3)
     */
    suspend fun closeSessionWithCount(
        session: Session,
        countedCash: Double?,
        cashCountPhotoPath: String?
    ) {
        sessionDao.update(
            session.copy(
                endTime = System.currentTimeMillis(),
                status = Session.STATUS_CLOSED,
                countedCash = countedCash,
                cashCountPhotoPath = cashCountPhotoPath
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

    /** Removes a sale by id — backs the "Undo" on the save snackbar. */
    suspend fun deleteSaleById(saleId: Long) = saleDao.deleteById(saleId)

    /** Chronological sales (with items) for building the export bundle. */
    suspend fun getSalesForExport(sessionId: Long): List<SaleWithItems> =
        saleDao.getSalesForSession(sessionId)

    // --- Trades ---

    /**
     * Records a trade and its OUT/IN item lines atomically, stamping the
     * current time. [items] carry direction/sku/name/qty/values; their tradeId
     * is filled in here.
     */
    suspend fun addTrade(
        sessionId: Long,
        items: List<TradeItem>,
        cashAmount: Double,
        cashDirection: String,
        note: String?,
        photoPath: String?
    ): Long = db.withTransaction {
        val tradeId = tradeDao.insert(
            Trade(
                sessionId = sessionId,
                note = note?.ifBlank { null },
                photoPath = photoPath,
                timestamp = System.currentTimeMillis(),
                cashAmount = cashAmount,
                cashDirection = cashDirection
            )
        )
        tradeItemDao.insertAll(items.map { it.copy(id = 0, tradeId = tradeId) })
        tradeId
    }

    /**
     * Updates an existing trade's header (note/photo/cash) and replaces its
     * item lines (inline editing while the session is active).
     */
    suspend fun updateTrade(trade: Trade, items: List<TradeItem>) = db.withTransaction {
        tradeDao.update(trade.copy(note = trade.note?.ifBlank { null }))
        tradeItemDao.deleteForTrade(trade.id)
        tradeItemDao.insertAll(items.map { it.copy(id = 0, tradeId = trade.id) })
    }

    /** Removes a trade by id — used by delete and the save-snackbar "Undo". */
    suspend fun deleteTradeById(tradeId: Long) = tradeDao.deleteById(tradeId)

    /** Chronological trades (with items) for building the export bundle. */
    suspend fun getTradesForExport(sessionId: Long): List<TradeWithItems> =
        tradeDao.getTradesForSession(sessionId)

    // --- Cash adjustments (paid-out / cash-in log) ---

    /**
     * Records a cash movement that isn't a sale or trade. [amount] is signed
     * from the drawer's side (positive = added, negative = paid out). (v1.3)
     */
    suspend fun addCashAdjustment(sessionId: Long, amount: Double, reason: String): Long =
        cashAdjustmentDao.insert(
            CashAdjustment(
                sessionId = sessionId,
                amount = amount,
                reason = reason.trim(),
                timestamp = System.currentTimeMillis()
            )
        )

    suspend fun deleteCashAdjustmentById(id: Long) = cashAdjustmentDao.deleteById(id)

    /** Chronological cash adjustments for a session (reconciliation, export). */
    suspend fun getCashAdjustmentsForExport(sessionId: Long): List<CashAdjustment> =
        cashAdjustmentDao.getForSession(sessionId)

    /** Loads one session by id (for reconciliation context in summaries). */
    suspend fun getSession(sessionId: Long): Session? = sessionDao.getById(sessionId)

    /** One-shot end-of-day numbers for any session (pre-export review, history). */
    suspend fun summarizeSession(sessionId: Long): SessionSummary {
        val session = sessionDao.getById(sessionId)
        val adjustments = cashAdjustmentDao.getForSession(sessionId)
        return SessionSummary.from(
            saleDao.getSalesForSession(sessionId),
            tradeDao.getTradesForSession(sessionId),
            startingFloat = session?.startingFloat,
            countedCash = session?.countedCash,
            cashAdjustmentsNet = adjustments.sumOf { it.amount }
        )
    }

    companion object {
        private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    }
}
