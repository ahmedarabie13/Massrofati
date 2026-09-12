package com.banksms.expensetracker.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.banksms.expensetracker.data.model.ParseMode
import com.banksms.expensetracker.data.model.SummaryReport
import com.banksms.expensetracker.data.model.Transaction
import com.banksms.expensetracker.data.repository.SyncResult
import com.banksms.expensetracker.data.repository.TransactionRepository
import com.banksms.expensetracker.data.repository.TransactionRepository.RescanResult
import com.banksms.expensetracker.util.DateRange
import com.banksms.expensetracker.util.DateRangePreset
import com.banksms.expensetracker.util.DateUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DashboardUiState(
    val dateRange: DateRange = DateUtils.getDateRange(DateRangePreset.THIS_MONTH),
    val summary: SummaryReport = SummaryReport(),
    val recentTransactions: List<Transaction> = emptyList(),
    val isSyncing: Boolean = false,
    val syncMessage: String? = null,
    val isAiMode: Boolean = false,
    val rescanInFlightId: Long? = null,
    val rescanMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val repository: TransactionRepository
) : ViewModel() {

    private val _dateRange = MutableStateFlow(DateUtils.getDateRange(DateRangePreset.THIS_MONTH))
    private val _isSyncing = MutableStateFlow(false)
    private val _syncMessage = MutableStateFlow<String?>(null)
    private val _rescanInFlightId = MutableStateFlow<Long?>(null)
    private val _rescanMessage = MutableStateFlow<String?>(null)

    private val _summary = _dateRange.flatMapLatest { range ->
        repository.getSummaryReport(range.startTime, range.endTime)
    }

    private val _recentTransactions = _dateRange.flatMapLatest { range ->
        repository.getFilteredTransactions(
            startTime = range.startTime,
            endTime = range.endTime
        ).map { list -> list.take(15) }
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        _dateRange,
        _summary,
        _recentTransactions,
        _isSyncing,
        _syncMessage,
        _rescanInFlightId,
        _rescanMessage,
        repository.parseMode
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val range = args[0] as DateRange
        val summary = args[1] as SummaryReport
        val recents = args[2] as List<Transaction>
        val syncing = args[3] as Boolean
        val msg = args[4] as String?
        val rescanId = args[5] as Long?
        val rescanMsg = args[6] as String?
        val mode = args[7] as ParseMode
        DashboardUiState(
            dateRange = range,
            summary = summary,
            recentTransactions = recents,
            isSyncing = syncing,
            syncMessage = msg,
            isAiMode = mode == ParseMode.AI,
            rescanInFlightId = rescanId,
            rescanMessage = rescanMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    fun setDateRangePreset(preset: DateRangePreset) {
        _dateRange.value = DateUtils.getDateRange(preset)
    }

    fun syncSms() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = null
            try {
                val result = repository.syncTransactionsFromSms()
                _syncMessage.value = when {
                    result.errors.isNotEmpty() -> result.errors.first()
                    result.transactionsImported > 0 -> "Synced! ${result.transactionsImported} new transactions imported."
                    result.messagesScanned > 0 -> "Up to date (${result.messagesScanned} bank SMS scanned)."
                    else -> "No new bank messages found in inbox."
                }
            } catch (e: CancellationException) {
                _syncMessage.value = "Sync stopped — partial results kept."
            } finally {
                _isSyncing.value = false
            }
        }
    }

    /** Stops a running sync (manual or AI): in-flight work finishes, partial results kept. */
    fun stopSync() {
        repository.cancelSync()
    }

    /** AI-only: re-send one stored SMS to the model and overwrite the row. */
    fun rescanTransaction(transaction: Transaction) {
        if (_rescanInFlightId.value != null) return
        viewModelScope.launch {
            _rescanInFlightId.value = transaction.id
            _rescanMessage.value = null
            try {
                _rescanMessage.value = when (val result = repository.rescanTransaction(transaction.id)) {
                    is RescanResult.Updated -> "Re-scanned — details updated."
                    RescanResult.NotTransaction ->
                        "The model doesn't see a transaction here — entry kept."
                    is RescanResult.Failed -> result.reason
                }
            } catch (e: CancellationException) {
                _rescanMessage.value = "Re-scan stopped."
            } finally {
                _rescanInFlightId.value = null
            }
        }
    }

    fun clearRescanMessage() {
        _rescanMessage.value = null
    }

    fun skipTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.skipTransaction(transaction)
            _syncMessage.value = "Transaction marked as skipped"
        }
    }

    fun deleteManualExpense(manualId: String) {
        viewModelScope.launch {
            repository.deleteManualExpense(manualId)
            _syncMessage.value = "Manual expense deleted permanently"
        }
    }

    fun addManualExpense(expense: com.banksms.expensetracker.data.file.ManualExpense) {
        viewModelScope.launch {
            repository.addManualExpense(expense)
            _syncMessage.value = "Expense added successfully"
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    class Factory(private val repository: TransactionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DashboardViewModel(repository) as T
        }
    }
}
