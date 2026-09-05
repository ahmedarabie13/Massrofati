package com.banksms.expensetracker.ui.screens.skipped

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.banksms.expensetracker.data.file.SkippedTransaction
import com.banksms.expensetracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SkippedUiState(
    val skippedTransactions: List<SkippedTransaction> = emptyList(),
    val searchQuery: String = "",
    val totalSkippedAmount: Double = 0.0,
    val totalSkippedCount: Int = 0,
    val isRestoring: Boolean = false,
    val message: String? = null
)

class SkippedViewModel(
    private val repository: TransactionRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _isRestoring = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SkippedUiState> = combine(
        repository.skippedTransactions,
        _searchQuery,
        _isRestoring,
        _message
    ) { skippedList, query, restoring, msg ->
        val filtered = if (query.isBlank()) {
            skippedList
        } else {
            val q = query.trim().lowercase()
            skippedList.filter {
                it.sender.lowercase().contains(q) ||
                (it.merchant?.lowercase()?.contains(q) == true) ||
                it.rawBody.lowercase().contains(q) ||
                it.amount.toString().contains(q)
            }
        }

        SkippedUiState(
            skippedTransactions = filtered,
            searchQuery = query,
            totalSkippedAmount = skippedList.sumOf { it.amount },
            totalSkippedCount = skippedList.size,
            isRestoring = restoring,
            message = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SkippedUiState()
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun unskipTransaction(skipped: SkippedTransaction) {
        viewModelScope.launch {
            _isRestoring.value = true
            repository.unskipTransaction(skipped)
            _isRestoring.value = false
            _message.value = "Restored transaction of ${skipped.amount} ${skipped.currency} back to active records."
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    class Factory(private val repository: TransactionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SkippedViewModel(repository) as T
        }
    }
}
