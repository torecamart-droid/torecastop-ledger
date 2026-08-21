package com.torecastop.ledger.ui.session

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.torecastop.ledger.BuildConfig
import com.torecastop.ledger.data.CashAdjustment
import com.torecastop.ledger.data.LedgerExporter
import com.torecastop.ledger.data.LedgerRepository
import com.torecastop.ledger.data.Sale
import com.torecastop.ledger.data.SaleItem
import com.torecastop.ledger.data.SaleWithItems
import com.torecastop.ledger.data.Session
import com.torecastop.ledger.data.SessionSummary
import com.torecastop.ledger.data.Trade
import com.torecastop.ledger.data.TradeItem
import com.torecastop.ledger.data.TradeWithItems
import com.torecastop.ledger.update.UpdateChecker
import com.torecastop.ledger.update.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** One-shot UI events: drive the "saved — Undo" snackbars. */
sealed interface LedgerEvent {
    data class SaleSaved(val total: Double) : LedgerEvent
    data class TradeSaved(val outTotal: Double, val inTotal: Double) : LedgerEvent
}

/** Everything the read-only history detail screen needs, loaded one-shot. */
data class SessionDetail(
    val sales: List<SaleWithItems>,
    val trades: List<TradeWithItems>,
    val summary: SessionSummary,
    val cashAdjustments: List<CashAdjustment>
)

/**
 * Backs the Active Session screen. Observes the single active session reactively
 * so the UI follows open/close, and streams its running totals, sales and trades.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActiveSessionViewModel(private val repository: LedgerRepository) : ViewModel() {

    val session: StateFlow<Session?> = repository.observeActiveSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val sales: StateFlow<List<SaleWithItems>> = session
        .filterNotNull()
        .flatMapLatest { repository.observeSales(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trades: StateFlow<List<TradeWithItems>> = session
        .filterNotNull()
        .flatMapLatest { repository.observeTrades(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val itemCount: StateFlow<Int> = session
        .filterNotNull()
        .flatMapLatest { repository.observeItemCount(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val total: StateFlow<Double> = session
        .filterNotNull()
        .flatMapLatest { repository.observeSessionTotal(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    /** Cash adjustments (paid-out / cash-in) for the active session. (v1.3) */
    val cashAdjustments: StateFlow<List<CashAdjustment>> = session
        .filterNotNull()
        .flatMapLatest { repository.observeCashAdjustments(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Signed net of the active session's cash adjustments. (v1.3) */
    val cashAdjustmentNet: StateFlow<Double> = session
        .filterNotNull()
        .flatMapLatest { repository.observeCashAdjustmentNet(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    /** All sessions, newest first — feeds the history screen. */
    val allSessions: StateFlow<List<Session>> = repository.observeAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Ledger-entry key ("sale-3" / "trade-7") to briefly highlight after a save. */
    private val _highlightKey = MutableStateFlow<String?>(null)
    val highlightKey: StateFlow<String?> = _highlightKey.asStateFlow()

    private val _events = Channel<LedgerEvent>(Channel.BUFFERED)
    val events: Flow<LedgerEvent> = _events.receiveAsFlow()

    /** A newer sideloaded build, if the update check found one. (v1.3) */
    private val _updateAvailable = MutableStateFlow<UpdateInfo?>(null)
    val updateAvailable: StateFlow<UpdateInfo?> = _updateAvailable.asStateFlow()

    /** The most recent save, so the snackbar's Undo can take it back. */
    private data class LastSaved(val isTrade: Boolean, val id: Long, val photoPaths: List<String>)
    private var lastSaved: LastSaved? = null

    init {
        // Resume today's session on first launch, opening one if none exists.
        viewModelScope.launch { repository.getOrStartActiveSession() }
        // Fire-and-forget update check; no-ops without a configured manifest.
        viewModelScope.launch {
            _updateAvailable.value = UpdateChecker.check(BuildConfig.VERSION_CODE)
        }
    }

    /** Dismisses the update banner for this session. (v1.3) */
    fun dismissUpdate() {
        _updateAvailable.value = null
    }

    // --- Sales ---

    /**
     * [itemPhotoPaths] is parallel to [items] — photos captured for that
     * specific line. [salePhotoPaths] cover the sale as a whole. (v1.3 revision)
     */
    fun addSale(
        items: List<SaleItem>,
        itemPhotoPaths: List<List<String>>,
        note: String?,
        salePhotoPaths: List<String>,
        cashReceived: Double? = null
    ) {
        val sessionId = session.value?.id ?: return
        if (items.isEmpty()) return
        viewModelScope.launch {
            val saleId = repository.addSale(
                sessionId, items, itemPhotoPaths, note, salePhotoPaths, cashReceived
            )
            lastSaved = LastSaved(
                isTrade = false,
                id = saleId,
                photoPaths = itemPhotoPaths.flatten() + salePhotoPaths
            )
            _events.send(LedgerEvent.SaleSaved(items.sumOf { it.quantity * it.price }))
            flashHighlight("sale-$saleId")
        }
    }

    fun updateSale(
        sale: Sale,
        items: List<SaleItem>,
        itemPhotoPaths: List<List<String>> = emptyList(),
        salePhotoPaths: List<String> = emptyList()
    ) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            repository.updateSale(sale, items, itemPhotoPaths, salePhotoPaths)
        }
    }

    /** [photoPaths] are every photo (whole-sale + per-item) so they're cleaned up too. */
    fun deleteSale(sale: Sale, photoPaths: List<String> = emptyList()) {
        viewModelScope.launch {
            repository.deleteSale(sale)
            deletePhotoFiles(photoPaths)
        }
    }

    // --- Trades ---

    /**
     * [itemPhotoPaths] is parallel to [items] — photos captured for that
     * specific card. [tradePhotoPaths] cover the trade as a whole. (v1.3 revision)
     */
    fun addTrade(
        items: List<TradeItem>,
        itemPhotoPaths: List<List<String>>,
        cashAmount: Double,
        cashDirection: String,
        note: String?,
        tradePhotoPaths: List<String>,
        customerName: String? = null,
        customerPhone: String? = null,
        customerEmail: String? = null,
        customerAddress: String? = null
    ) {
        val sessionId = session.value?.id ?: return
        if (items.isEmpty()) return
        viewModelScope.launch {
            val tradeId = repository.addTrade(
                sessionId, items, itemPhotoPaths, cashAmount, cashDirection, note,
                tradePhotoPaths, customerName, customerPhone, customerEmail, customerAddress
            )
            lastSaved = LastSaved(
                isTrade = true,
                id = tradeId,
                photoPaths = itemPhotoPaths.flatten() + tradePhotoPaths
            )
            // Totals for the snackbar, same figures the row will show.
            val preview = TradeWithItems(
                trade = Trade(
                    sessionId = sessionId,
                    timestamp = 0,
                    cashAmount = cashAmount,
                    cashDirection = cashDirection
                ),
                items = items
            )
            _events.send(LedgerEvent.TradeSaved(preview.outTotal, preview.inTotal))
            flashHighlight("trade-$tradeId")
        }
    }

    fun updateTrade(
        trade: Trade,
        items: List<TradeItem>,
        itemPhotoPaths: List<List<String>> = emptyList(),
        tradePhotoPaths: List<String> = emptyList()
    ) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            repository.updateTrade(trade, items, itemPhotoPaths, tradePhotoPaths)
        }
    }

    /** [photoPaths] are every photo (whole-trade + per-card) so they're cleaned up too. */
    fun deleteTrade(trade: Trade, photoPaths: List<String> = emptyList()) {
        viewModelScope.launch {
            repository.deleteTradeById(trade.id)
            deletePhotoFiles(photoPaths.ifEmpty { listOfNotNull(trade.photoPath) })
        }
    }

    // --- Undo (snackbar action) ---

    /** Takes back the most recent sale/trade save. No-op once consumed. */
    fun undoLastSave() {
        val last = lastSaved ?: return
        lastSaved = null
        viewModelScope.launch {
            if (last.isTrade) repository.deleteTradeById(last.id)
            else repository.deleteSaleById(last.id)
            deletePhotoFiles(last.photoPaths)
        }
    }

    // --- Sessions ---

    /** Closes the current session; the UI then shows the "start a session" state. */
    fun closeSession() {
        val current = session.value ?: return
        viewModelScope.launch { repository.closeSession(current) }
    }

    /** Closes the current session, recording the end-of-day cash count. (v1.3) */
    fun closeSessionWithCount(countedCash: Double?, cashCountPhotoPath: String?) {
        val current = session.value ?: return
        viewModelScope.launch {
            repository.closeSessionWithCount(current, countedCash, cashCountPhotoPath)
        }
    }

    /** Opens a fresh session named for today. */
    fun startNewSession() {
        viewModelScope.launch { repository.getOrStartActiveSession() }
    }

    /** Sets or clears the optional show/event label on the active session. (v1.3) */
    fun setSessionLabel(label: String?) {
        val id = session.value?.id ?: return
        viewModelScope.launch { repository.setSessionLabel(id, label) }
    }

    /** Records (or clears) the starting cash float for the active session. (v1.3) */
    fun setStartingFloat(amount: Double?) {
        val id = session.value?.id ?: return
        viewModelScope.launch { repository.setStartingFloat(id, amount) }
    }

    /** Logs a cash adjustment; [amount] is signed (+ added, − paid out). (v1.3) */
    fun addCashAdjustment(amount: Double, reason: String) {
        val id = session.value?.id ?: return
        if (amount == 0.0 || reason.isBlank()) return
        viewModelScope.launch { repository.addCashAdjustment(id, amount, reason) }
    }

    /** Removes a logged cash adjustment. (v1.3) */
    fun deleteCashAdjustment(id: Long) {
        viewModelScope.launch { repository.deleteCashAdjustmentById(id) }
    }

    // --- Summary / history / export ---

    /** End-of-day numbers for any session — pre-export review and history. */
    suspend fun summarize(sessionId: Long): SessionSummary =
        repository.summarizeSession(sessionId)

    /** Loads a past session's full ledger for the read-only history detail. */
    suspend fun loadSessionDetail(sessionId: Long): SessionDetail {
        val loadedSession = repository.getSession(sessionId)
        val sessionSales = repository.getSalesForExport(sessionId)
        val sessionTrades = repository.getTradesForExport(sessionId)
        val adjustments = repository.getCashAdjustmentsForExport(sessionId)
        return SessionDetail(
            sales = sessionSales,
            trades = sessionTrades,
            summary = SessionSummary.from(
                sessionSales,
                sessionTrades,
                startingFloat = loadedSession?.startingFloat,
                countedCash = loadedSession?.countedCash,
                cashAdjustmentsNet = adjustments.sumOf { it.amount }
            ),
            cashAdjustments = adjustments
        )
    }

    /**
     * Builds the export zip (CSVs + photos) for [target] off the main thread
     * and hands back a shareable [Uri]. Works for active and closed sessions.
     */
    fun exportSession(context: Context, target: Session, onReady: (Uri?) -> Unit) {
        viewModelScope.launch {
            val exportSales = repository.getSalesForExport(target.id)
            val exportTrades = repository.getTradesForExport(target.id)
            val exportAdjustments = repository.getCashAdjustmentsForExport(target.id)
            val uri = withContext(Dispatchers.IO) {
                LedgerExporter.buildZip(context, target, exportSales, exportTrades, exportAdjustments)
            }
            onReady(uri)
        }
    }

    // --- Internals ---

    private fun flashHighlight(key: String) {
        viewModelScope.launch {
            _highlightKey.value = key
            delay(2_500)
            if (_highlightKey.value == key) _highlightKey.value = null
        }
    }

    private suspend fun deletePhotoFiles(paths: List<String>) {
        if (paths.isEmpty()) return
        withContext(Dispatchers.IO) { paths.forEach { File(it).delete() } }
    }

    companion object {
        fun factory(repository: LedgerRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ActiveSessionViewModel(repository) as T
            }
    }
}
