package com.example.spendtracker.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.spendtracker.data.repository.AppSettings
import com.example.spendtracker.data.repository.SpendingRepository
import com.example.spendtracker.domain.parser.DuplicateDetector
import com.example.spendtracker.domain.parser.TransactionParser
import com.example.spendtracker.domain.parser.IncomingPaymentParser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CommBankNotificationListener : NotificationListenerService() {
    @Inject lateinit var parser: TransactionParser
    @Inject lateinit var incomingPaymentParser: IncomingPaymentParser
    @Inject lateinit var repository: SpendingRepository
    @Inject lateinit var settings: AppSettings
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != settings.commBankPackage) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val text = listOf(title, body).filter { it.isNotBlank() }.joinToString(" ")
        val parsed = parser.parse(text)
        if (parsed != null) {
            val hash = DuplicateDetector.hash(sbn.postTime, parsed.merchant, parsed.amountCents, text)
            scope.launch { repository.addParsed(parsed.merchant, parsed.amountCents, sbn.postTime, hash) }
            return
        }
        val incoming = incomingPaymentParser.parse(text) ?: return
        val hash = DuplicateDetector.hash(sbn.postTime, incoming.senderName.orEmpty(), incoming.amountCents, text)
        scope.launch { repository.recordIncomingPayment(incoming.senderName, incoming.amountCents, sbn.postTime, hash) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
