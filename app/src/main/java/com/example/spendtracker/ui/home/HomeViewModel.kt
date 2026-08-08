package com.example.spendtracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendtracker.data.repository.SpendingRepository
import com.example.spendtracker.data.repository.AppSettings
import com.example.spendtracker.domain.model.SpendingSnapshot
import com.example.spendtracker.domain.model.BillSplitDetails
import com.example.spendtracker.domain.model.Transaction
import com.example.spendtracker.domain.model.IncomeSnapshot
import com.example.spendtracker.domain.model.SplitParticipantEdit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(private val repository: SpendingRepository, private val settings: AppSettings) : ViewModel() {
    val snapshot = repository.observeSnapshot().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpendingSnapshot(0, emptyList()))
    val incomeSnapshot = repository.observeIncomeSnapshot().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IncomeSnapshot(0, emptyList()))
    val allTransactions = repository.observeAllTransactions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val deletedTransactions = repository.observeDeletedTransactions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val savedAccountName get() = settings.accountName
    val savedPayId get() = settings.payId

    init { viewModelScope.launch { repository.ensurePeriod(); repository.ensureIncomePeriod() } }
    private val selectedTransactionId = MutableStateFlow<Long?>(null)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedSplit = selectedTransactionId.flatMapLatest { id ->
        if (id == null) flowOf(SelectedSplitState())
        else combine(repository.observeSplit(id), repository.observeCombinedLineItems(id)) { split, lineItems ->
            SelectedSplitState(id, split, false, lineItems)
        }
            .onStart { emit(SelectedSplitState(id, null, true)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SelectedSplitState())

    fun selectTransaction(id: Long?) { selectedTransactionId.value = id }
    fun reset() = viewModelScope.launch { repository.reset() }
    fun resetIncome() = viewModelScope.launch { repository.resetIncome() }
    fun resetBoth() = viewModelScope.launch { repository.resetBoth() }
    fun addManual(merchant: String, cents: Long, timestamp: Long) = viewModelScope.launch {
        repository.addManual(merchant, cents, timestamp)
    }
    fun createSplit(transaction: Transaction, title: String, names: List<String>, accountName: String, payId: String) =
        viewModelScope.launch { savePaymentDetails(accountName, payId); repository.createSplit(transaction, title, names, accountName, payId) }
    fun createCombinedSplit(transactions: List<Transaction>, title: String, names: List<String>, accountName: String, payId: String, includeInCurrentPeriod: Boolean) =
        viewModelScope.launch { savePaymentDetails(accountName, payId); repository.createCombinedSplit(transactions, title, names, accountName, payId, includeInCurrentPeriod) }
    fun deleteTransactions(ids: Set<Long>) = viewModelScope.launch { repository.deleteTransactions(ids) }
    fun restoreTransactions(ids: Set<Long>) = viewModelScope.launch { repository.restoreTransactions(ids) }
    fun permanentlyDeleteTransactions(ids: Set<Long>) = viewModelScope.launch { repository.permanentlyDeleteTransactions(ids) }
    fun emptyRecycleBin() = viewModelScope.launch { repository.emptyRecycleBin() }
    fun assignPayment(paymentId: Long, participantId: Long) = viewModelScope.launch {
        repository.assignPayment(paymentId, participantId)
    }
    fun markParticipantPaid(participantId: Long) = viewModelScope.launch {
        repository.markParticipantPaid(participantId)
    }
    fun undoParticipantPaid(participantId: Long) = viewModelScope.launch {
        repository.undoParticipantPaid(participantId)
    }
    fun closeSplit(splitId: Long) = viewModelScope.launch { repository.closeSplit(splitId) }
    fun reopenSplit(splitId: Long, participantIds: Set<Long>) = viewModelScope.launch {
        repository.reopenSplit(splitId, participantIds)
    }
    fun cancelSplit(splitId: Long) = viewModelScope.launch { repository.cancelSplit(splitId) }
    fun editSplitParticipants(splitId: Long, edits: List<SplitParticipantEdit>) = viewModelScope.launch {
        repository.editSplitParticipants(splitId, edits)
    }
    fun undoCombination(transactionId: Long) = viewModelScope.launch { repository.undoCombination(transactionId) }
    private fun savePaymentDetails(accountName: String, payId: String) {
        settings.accountName = accountName
        settings.payId = payId
    }
}

data class SelectedSplitState(
    val transactionId: Long? = null,
    val details: BillSplitDetails? = null,
    val loading: Boolean = false,
    val lineItems: List<com.example.spendtracker.domain.model.SplitLineItem> = emptyList()
)
