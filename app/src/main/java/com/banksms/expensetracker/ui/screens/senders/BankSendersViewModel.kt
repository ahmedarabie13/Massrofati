package com.banksms.expensetracker.ui.screens.senders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.banksms.expensetracker.data.model.BankSender
import com.banksms.expensetracker.data.reader.DiscoveredSender
import com.banksms.expensetracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class BankSendersUiState(
    val senders: List<BankSender> = emptyList(),
    val discoveredSenders: List<DiscoveredSender> = emptyList(),
    val isDiscovering: Boolean = false,
    val showDiscoveredDialog: Boolean = false
)

class BankSendersViewModel(
    private val repository: TransactionRepository
) : ViewModel() {

    private val _discoveredSenders = MutableStateFlow<List<DiscoveredSender>>(emptyList())
    private val _isDiscovering = MutableStateFlow(false)
    private val _showDiscoveredDialog = MutableStateFlow(false)

    val uiState: StateFlow<BankSendersUiState> = combine(
        repository.getSendersWithStats(),
        _discoveredSenders,
        _isDiscovering,
        _showDiscoveredDialog
    ) { senders, discovered, discovering, showDialog ->
        BankSendersUiState(
            senders = senders,
            discoveredSenders = discovered,
            isDiscovering = discovering,
            showDiscoveredDialog = showDialog
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BankSendersUiState()
    )

    fun toggleMonitored(sender: BankSender) {
        viewModelScope.launch {
            repository.setSenderMonitored(sender.senderId, !sender.isMonitored)
        }
    }

    fun addSender(senderId: String, displayName: String) {
        if (senderId.isBlank()) return
        viewModelScope.launch {
            repository.addCustomSender(
                BankSender(
                    senderId = senderId.trim(),
                    displayName = if (displayName.isNotBlank()) displayName.trim() else senderId.trim(),
                    isMonitored = true
                )
            )
        }
    }

    fun deleteSender(sender: BankSender) {
        viewModelScope.launch {
            repository.deleteSender(sender)
        }
    }

    fun discoverFromInbox() {
        viewModelScope.launch {
            _isDiscovering.value = true
            val discovered = repository.discoverSendersFromInbox()
            _discoveredSenders.value = discovered
            _isDiscovering.value = false
            _showDiscoveredDialog.value = true
        }
    }

    fun closeDiscoveredDialog() {
        _showDiscoveredDialog.value = false
    }

    fun addDiscoveredSender(address: String) {
        addSender(senderId = address, displayName = address)
    }

    class Factory(private val repository: TransactionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BankSendersViewModel(repository) as T
        }
    }
}
