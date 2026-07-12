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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backs the Active Session screen. Observes the single active session reactively
 * so the UI follows open/close, and streams its running totals and sale lines.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActiveSessionViewModel(private val repository: LedgerRepository) : ViewModel() {

    val session: StateFlow<Session?> = repository.observeActiveSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val sales: StateFlow<List<SaleWithItems>> = session
        .filterNotNull()
        .flatMapLatest { repository.observeSales(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val itemCount: StateFlow<Int> = session
        .filterNotNull()
        .flatMapLatest { repository.observeItemCount(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val total: StateFlow<Double> = session
        .filterNotNull()
        .flatMapLatest { repository.observeSessionTotal(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    init {
        // Resume today's session on first launch, opening one if none exists.
        viewModelScope.launch { repository.getOrStartActiveSession() }
    }

    fun addSale(items: List<SaleItem>, note: String?, photoPath: String?) {
        val sessionId = session.value?.id ?: return
        if (items.isEmpty()) return
        viewModelScope.launch {
            repository.addSale(sessionId, items, note, photoPath)
        }
    }

    fun updateSale(sale: Sale, items: List<SaleItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch { repository.updateSale(sale, items) }
    }

    fun deleteSale(sale: Sale) {
        viewModelScope.launch { repository.deleteSale(sale) }
    }

    /** Closes the current session; the UI then shows the "start a session" state. */
    fun closeSession() {
        val current = session.value ?: return
        viewModelScope.launch { repository.closeSession(current) }
    }

    /** Opens a fresh session named for today. */
    fun startNewSession() {
        viewModelScope.launch { repository.getOrStartActiveSession() }
    }

    /**
     * Builds the export zip (CSV + photos) off the main thread and hands back a
     * shareable [Uri], or null if there is no active session.
     */
    fun exportSession(context: Context, onReady: (Uri?) -> Unit) {
        val current = session.value ?: return onReady(null)
        viewModelScope.launch {
            val lines = repository.getSalesForExport(current.id)
            val uri = withContext(Dispatchers.IO) {
                LedgerExporter.buildZip(context, current, lines)
            }
            onReady(uri)
        }
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
