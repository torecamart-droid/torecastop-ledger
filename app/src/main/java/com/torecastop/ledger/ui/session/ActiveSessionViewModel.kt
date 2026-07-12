package com.torecastop.ledger.ui.session

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
    data class TradeSaved(val valueAdded: Double) : LedgerEvent
}

/** Everything the read-only history detail screen needs, loaded one-shot. */
data class SessionDetail(
    val sales: List<SaleWithItems>,
    val trades: List<TradeWithItems>,
    val summary: SessionSummary
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

    /** All sessions, newest first — feeds the history screen. */
    val allSessions: StateFlow<List<Session>> = repository.observeAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Ledger-entry key ("sale-3" / "trade-7") to briefly highlight after a save. */
    private val _highlightKey = MutableStateFlow<String?>(null)
    val highlightKey: StateFlow<String?> = _highlightKey.asStateFlow()

    private val _events = Channel<LedgerEvent>(Channel.BUFFERED)
    val events: Flow<LedgerEvent> = _events.receiveAsFlow()

    /** The most recent save, so the snackbar's Undo can take it back. */
    private data class LastSaved(val isTrade: Boolean, val id: Long, val photoPath: String?)
    private var lastSaved: LastSaved? = null

    init {
        // Resume today's session on first launch, opening one if none exists.
        viewModelScope.launch { repository.getOrStartActiveSession() }
    }

    // --- Sales ---

    fun addSale(items: List<SaleItem>, note: String?, photoPath: String?) {
        val sessionId = session.value?.id ?: return
        if (items.isEmpty()) return
        viewModelScope.launch {
            val saleId = repository.addSale(sessionId, items, note, photoPath)
            lastSaved = LastSaved(isTrade = false, id = saleId, photoPath = photoPath)
            _events.send(LedgerEvent.SaleSaved(items.sumOf { it.quantity * it.price }))
            flashHighlight("sale-$saleId")
        }
    }

    fun updateSale(sale: Sale, items: List<SaleItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch { repository.updateSale(sale, items) }
    }

    fun deleteSale(sale: Sale) {
        viewModelScope.launch {
            repository.deleteSale(sale)
            deletePhotoFile(sale.photoPath)
        }
    }

    // --- Trades ---

    fun addTrade(
        items: List<TradeItem>,
        cashAmount: Double,
        cashDirection: String,
        note: String?,
        photoPath: String?
    ) {
        val sessionId = session.value?.id ?: return
        if (items.isEmpty()) return
        viewModelScope.launch {
            val tradeId = repository.addTrade(
                sessionId, items, cashAmount, cashDirection, note, photoPath
            )
            lastSaved = LastSaved(isTrade = true, id = tradeId, photoPath = photoPath)
            // Headline number for the snackbar, same maths the row will show.
            val preview = TradeWithItems(
                trade = Trade(
                    sessionId = sessionId,
                    timestamp = 0,
                    cashAmount = cashAmount,
                    cashDirection = cashDirection
                ),
                items = items
            )
            _events.send(LedgerEvent.TradeSaved(preview.valueAdded))
            flashHighlight("trade-$tradeId")
        }
    }

    fun updateTrade(trade: Trade, items: List<TradeItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch { repository.updateTrade(trade, items) }
    }

    fun deleteTrade(trade: Trade) {
        viewModelScope.launch {
            repository.deleteTradeById(trade.id)
            deletePhotoFile(trade.photoPath)
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
            deletePhotoFile(last.photoPath)
        }
    }

    // --- Sessions ---

    /** Closes the current session; the UI then shows the "start a session" state. */
    fun closeSession() {
        val current = session.value ?: return
        viewModelScope.launch { repository.closeSession(current) }
    }

    /** Opens a fresh session named for today. */
    fun startNewSession() {
        viewModelScope.launch { repository.getOrStartActiveSession() }
    }

    // --- Summary / history / export ---

    /** End-of-day numbers for any session — pre-export review and history. */
    suspend fun summarize(sessionId: Long): SessionSummary =
        repository.summarizeSession(sessionId)

    /** Loads a past session's full ledger for the read-only history detail. */
    suspend fun loadSessionDetail(sessionId: Long): SessionDetail {
        val sessionSales = repository.getSalesForExport(sessionId)
        val sessionTrades = repository.getTradesForExport(sessionId)
        return SessionDetail(
            sales = sessionSales,
            trades = sessionTrades,
            summary = SessionSummary.from(sessionSales, sessionTrades)
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
            val uri = withContext(Dispatchers.IO) {
                LedgerExporter.buildZip(context, target, exportSales, exportTrades)
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

    private suspend fun deletePhotoFile(path: String?) {
        path ?: return
        withContext(Dispatchers.IO) { File(path).delete() }
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
