package com.banksms.expensetracker.ui.screens.senders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.banksms.expensetracker.data.model.BankSender
import com.banksms.expensetracker.data.model.MessageTemplate
import com.banksms.expensetracker.data.reader.DiscoveredSender
import com.banksms.expensetracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class BankSendersUiState(
    val senders: List<BankSender> = emptyList(),
    val templates: List<MessageTemplate> = emptyList(),
    val selectedTab: Int = 0, // 0 = Monitored Banks, 1 = SMS Templates
    val discoveredSenders: List<DiscoveredSender> = emptyList(),
    val isDiscovering: Boolean = false,
    val showDiscoveredDialog: Boolean = false,
    val feedbackMessage: String? = null,
    val storagePath: String = "",
    val isPublicStorageActive: Boolean = false
)

class BankSendersViewModel(
    private val repository: TransactionRepository
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)
    private val _discoveredSenders = MutableStateFlow<List<DiscoveredSender>>(emptyList())
    private val _isDiscovering = MutableStateFlow(false)
    private val _showDiscoveredDialog = MutableStateFlow(false)
    private val _feedbackMessage = MutableStateFlow<String?>(null)

    private data class DiscoveryState(
        val discoveredSenders: List<DiscoveredSender> = emptyList(),
        val isDiscovering: Boolean = false,
        val showDiscoveredDialog: Boolean = false
    )

    private val _discoveryState = combine(
        _discoveredSenders,
        _isDiscovering,
        _showDiscoveredDialog
    ) { discovered, discovering, showDialog ->
        DiscoveryState(discovered, discovering, showDialog)
    }

    val uiState: StateFlow<BankSendersUiState> = combine(
        repository.getSendersWithStats(),
        repository.messageTemplates,
        _selectedTab,
        _discoveryState,
        _feedbackMessage
    ) { senders, templates, tab, discovery, msg ->
        BankSendersUiState(
            senders = senders,
            templates = templates,
            selectedTab = tab,
            discoveredSenders = discovery.discoveredSenders,
            isDiscovering = discovery.isDiscovering,
            showDiscoveredDialog = discovery.showDiscoveredDialog,
            feedbackMessage = msg,
            storagePath = repository.getStorageDirectoryPath(),
            isPublicStorageActive = repository.isPublicStorageActive()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BankSendersUiState()
    )

    fun selectTab(tabIndex: Int) {
        _selectedTab.value = tabIndex
    }

    fun toggleMonitored(sender: BankSender) {
        viewModelScope.launch {
            repository.setSenderMonitored(sender.senderId, !sender.isMonitored)
        }
    }

    fun addSender(senderId: String, displayName: String, customRegex: String? = null) {
        if (senderId.isBlank()) return
        viewModelScope.launch {
            repository.addCustomSender(
                BankSender(
                    senderId = senderId.trim(),
                    displayName = if (displayName.isNotBlank()) displayName.trim() else senderId.trim(),
                    isMonitored = true,
                    customRegex = customRegex?.trim()?.takeIf { it.isNotBlank() }
                )
            )
            _feedbackMessage.value = "Bank added successfully"
        }
    }

    fun updateSender(oldSenderId: String, updated: BankSender) {
        if (updated.senderId.isBlank()) return
        viewModelScope.launch {
            repository.updateSender(oldSenderId, updated)
            _feedbackMessage.value = "Bank '${updated.displayName}' updated"
        }
    }

    fun deleteSender(sender: BankSender) {
        viewModelScope.launch {
            repository.deleteSender(sender)
            _feedbackMessage.value = "Bank '${sender.displayName}' removed"
        }
    }

    fun clearAllPersistenceFiles() {
        viewModelScope.launch {
            repository.clearAllPersistenceFiles()
            _feedbackMessage.value = "All saved persistence files cleared from the filesystem"
        }
    }

    // ── Template Management ──────────────────────────────────────────────

    fun saveTemplate(template: MessageTemplate) {
        viewModelScope.launch {
            repository.saveMessageTemplate(template)
            _feedbackMessage.value = "Template '${template.name}' saved to file system"
        }
    }

    fun deleteTemplate(id: String) {
        viewModelScope.launch {
            repository.deleteMessageTemplate(id)
            _feedbackMessage.value = "Template deleted permanently"
        }
    }

    fun toggleTemplate(id: String, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.toggleMessageTemplate(id, isEnabled)
        }
    }

    fun clearFeedbackMessage() {
        _feedbackMessage.value = null
    }

    // ── Discovery ────────────────────────────────────────────────────────

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
