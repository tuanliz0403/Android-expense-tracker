package com.example.spendtracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendtracker.data.repository.AppSettings
import com.example.spendtracker.data.repository.SpendingRepository
import com.example.spendtracker.domain.parser.TransactionParser
import com.example.spendtracker.domain.parser.IncomingPaymentParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: AppSettings,
    private val parser: TransactionParser,
    private val incomingPaymentParser: IncomingPaymentParser,
    private val repository: SpendingRepository
) : ViewModel() {
    val initialPackage get() = settings.commBankPackage
    val initialCurrency get() = settings.currency
    val initialAccountName get() = settings.accountName
    val initialPayId get() = settings.payId
    fun save(packageName: String, currency: String, accountName: String, payId: String) {
        settings.commBankPackage = packageName
        settings.currency = currency
        settings.accountName = accountName
        settings.payId = payId
    }
    fun test(text: String): ParserTestResult? {
        parser.parse(text)?.let {
            return ParserTestResult(
                type = "Completed purchase",
                nameLabel = "Merchant",
                name = it.merchant,
                amountCents = it.amountCents
            )
        }
        incomingPaymentParser.parse(text)?.let {
            return ParserTestResult(
                type = "Incoming payment",
                nameLabel = "Sender",
                name = it.senderName ?: "Not provided — manual assignment required",
                amountCents = it.amountCents
            )
        }
        return null
    }
    fun export(onReady: (String) -> Unit) = viewModelScope.launch { onReady(repository.exportCsv()) }
    fun importCsv(csv: String, onResult: (String) -> Unit) = viewModelScope.launch {
        val message = runCatching { repository.importCsv(csv) }.fold(
            onSuccess = { "Imported ${it.imported}; skipped ${it.duplicates} duplicates and ${it.invalidRows} invalid rows. Open the History tab to view imported records." },
            onFailure = { it.message ?: "Could not import this CSV file." }
        )
        onResult(message)
    }
    fun deleteAll() = viewModelScope.launch { repository.deleteAll() }
}

data class ParserTestResult(
    val type: String,
    val nameLabel: String,
    val name: String,
    val amountCents: Long
)
