package com.banksms.expensetracker.ui.screens.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.banksms.expensetracker.data.model.SummaryReport
import com.banksms.expensetracker.data.model.Transaction
import com.banksms.expensetracker.data.repository.TransactionRepository
import com.banksms.expensetracker.util.DateRange
import com.banksms.expensetracker.util.DateRangePreset
import com.banksms.expensetracker.util.DateUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

data class ReportsUiState(
    val dateRange: DateRange = DateUtils.getDateRange(DateRangePreset.THIS_MONTH),
    val report: SummaryReport = SummaryReport(),
    val rawTransactions: List<Transaction> = emptyList(),
    val isLoading: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(
    private val repository: TransactionRepository
) : ViewModel() {

    private val _dateRange = MutableStateFlow(DateUtils.getDateRange(DateRangePreset.THIS_MONTH))

    private val _report = _dateRange.flatMapLatest { range ->
        repository.getSummaryReport(range.startTime, range.endTime)
    }

    private val _rawTransactions = _dateRange.flatMapLatest { range ->
        repository.getFilteredTransactions(range.startTime, range.endTime)
    }

    val uiState: StateFlow<ReportsUiState> = combine(
        _dateRange,
        _report,
        _rawTransactions
    ) { range, report, txs ->
        ReportsUiState(
            dateRange = range,
            report = report,
            rawTransactions = txs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReportsUiState()
    )

    fun setDateRangePreset(preset: DateRangePreset) {
        _dateRange.value = DateUtils.getDateRange(preset)
    }

    class Factory(private val repository: TransactionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReportsViewModel(repository) as T
        }
    }
}
