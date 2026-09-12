package com.banksms.expensetracker.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.banksms.expensetracker.data.file.ManualExpense
import com.banksms.expensetracker.data.file.SkippedTransaction
import com.banksms.expensetracker.data.model.BankSender
import com.banksms.expensetracker.data.model.ParseMode
import com.banksms.expensetracker.data.model.Transaction
import com.banksms.expensetracker.data.model.TransactionType
import com.banksms.expensetracker.data.repository.TransactionRepository
import com.banksms.expensetracker.data.repository.TransactionRepository.RescanResult
import com.banksms.expensetracker.util.DateRange
import com.banksms.expensetracker.util.DateRangePreset
import com.banksms.expensetracker.util.DateUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TransactionsUiState(
    val dateRange: DateRange = DateUtils.getDateRange(DateRangePreset.ALL_TIME),
    val selectedType: TransactionType? = null,
    val selectedBank: String? = null,
    val selectedCategory: String? = null,
    val searchQuery: String = "",
    val manualOnly: Boolean = false,
    val transactions: List<Transaction> = emptyList(),
    val availableBanks: List<String> = emptyList(),
    val skippedCount: Int = 0,
    val isLoading: Boolean = false,
    val isAiMode: Boolean = false,
    val rescanInFlightId: Long? = null,
    val rescanMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModel(
    private val repository: TransactionRepository
) : ViewModel() {

    private val _dateRange = MutableStateFlow(DateUtils.getDateRange(DateRangePreset.ALL_TIME))
    private val _selectedType = MutableStateFlow<TransactionType?>(null)
    private val _selectedBank = MutableStateFlow<String?>(null)
    private val _selectedCategory = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _manualOnly = MutableStateFlow(false)
    private val _rescanInFlightId = MutableStateFlow<Long?>(null)
    private val _rescanMessage = MutableStateFlow<String?>(null)

    private val _senders = repository.getSendersWithStats()

    private val _transactions = combine(
        _dateRange,
        _selectedType,
        _selectedBank,
        _selectedCategory,
        _searchQuery,
        _manualOnly
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        FilterParams(
            range = args[0] as DateRange,
            type = args[1] as TransactionType?,
            bank = args[2] as String?,
            category = args[3] as String?,
            query = args[4] as String,
            manualOnly = args[5] as Boolean
        )
    }.flatMapLatest { params ->
        repository.getFilteredTransactions(
            startTime = params.range.startTime,
            endTime = params.range.endTime,
            type = params.type,
            sender = params.bank,
            category = params.category,
            searchQuery = params.query,
            manualOnly = params.manualOnly
        )
    }

    private val _filterState = combine(
        _dateRange,
        _selectedType,
        _selectedBank,
        _selectedCategory,
        _searchQuery,
        _manualOnly
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        FilterParams(
            range = args[0] as DateRange,
            type = args[1] as TransactionType?,
            bank = args[2] as String?,
            category = args[3] as String?,
            query = args[4] as String,
            manualOnly = args[5] as Boolean
        )
    }

    val uiState: StateFlow<TransactionsUiState> = combine(
        _filterState,
        _transactions,
        _senders,
        repository.skippedTransactions,
        _rescanInFlightId,
        _rescanMessage,
        repository.parseMode
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val filterParams = args[0] as FilterParams
        val transactions = args[1] as List<Transaction>
        val senders = args[2] as List<BankSender>
        val skipped = args[3] as List<SkippedTransaction>
        val rescanId = args[4] as Long?
        val rescanMsg = args[5] as String?
        val mode = args[6] as ParseMode
        TransactionsUiState(
            dateRange = filterParams.range,
            selectedType = filterParams.type,
            selectedBank = filterParams.bank,
            selectedCategory = filterParams.category,
            searchQuery = filterParams.query,
            manualOnly = filterParams.manualOnly,
            transactions = transactions,
            availableBanks = senders.map { it.senderId },
            skippedCount = skipped.size,
            isAiMode = mode == ParseMode.AI,
            rescanInFlightId = rescanId,
            rescanMessage = rescanMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TransactionsUiState()
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setTypeFilter(type: TransactionType?) {
        _selectedType.value = type
    }

    fun setBankFilter(bank: String?) {
        _selectedBank.value = bank
    }

    fun setManualOnlyFilter(manualOnly: Boolean) {
        _manualOnly.value = manualOnly
    }

    fun setDateRangePreset(preset: DateRangePreset) {
        _dateRange.value = DateUtils.getDateRange(preset)
    }

    fun addManualExpense(expense: ManualExpense) {
        viewModelScope.launch {
            repository.addManualExpense(expense)
        }
    }

    fun updateManualExpense(expense: ManualExpense) {
        viewModelScope.launch {
            repository.updateManualExpense(expense)
        }
    }

    fun deleteManualExpense(manualId: String) {
        viewModelScope.launch {
            repository.deleteManualExpense(manualId)
        }
    }

    fun skipTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.skipTransaction(transaction)
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            repository.deleteTransaction(id)
        }
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

    private data class FilterParams(
        val range: DateRange,
        val type: TransactionType?,
        val bank: String?,
        val category: String?,
        val query: String,
        val manualOnly: Boolean = false
    )

    class Factory(private val repository: TransactionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TransactionsViewModel(repository) as T
        }
    }
}
