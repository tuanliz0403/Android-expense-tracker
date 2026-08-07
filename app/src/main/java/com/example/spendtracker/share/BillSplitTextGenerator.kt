package com.example.spendtracker.share

import android.content.Context
import android.content.Intent
import com.example.spendtracker.domain.model.BillSplitDetails
import com.example.spendtracker.ui.formatAud
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BillSplitTextGenerator {
    fun generate(split: BillSplitDetails, transactionTimestamp: Long): String = buildString {
        appendLine(split.title)
        appendLine(SimpleDateFormat("EEEE, d MMMM yyyy", Locale.forLanguageTag("en-AU")).format(Date(transactionTimestamp)))
        appendLine()
        appendLine("Total: ${formatAud(split.totalCents)}")
        appendLine("${split.participants.size} people · ${formatAud(split.perPersonCents)} each")
        appendLine()
        val progress = when {
            split.isClosed -> "${split.paidCount} / ${split.participants.size} Paid — Closed ✓"
            split.isCompleted -> "${split.paidCount} / ${split.participants.size} Paid — Completed ✓"
            else -> "${split.paidCount} / ${split.participants.size} Paid"
        }
        appendLine("Payment Progress — $progress")
        appendLine()
        split.participants.forEach { participant ->
            when {
                participant.isPaid -> appendLine("✓ ${participant.name} — Paid")
                participant.isWaived -> appendLine("• ${participant.name} — Covered by me")
                else -> appendLine("○ ${participant.name} — ${formatAud(split.perPersonCents)} owing")
            }
        }
        appendLine()
        appendLine(if (split.isClosed) "Split closed — remaining balance covered by me" else "Please pay ${formatAud(split.perPersonCents)}")
        appendLine("PayID: ${split.payId}")
        appendLine("Account Name: ${split.accountName}")
        appendLine()
        append("Please use the exact amount of ${formatAud(split.perPersonCents)} so Expense Tracker can automatically match your payment.")
    }

    fun share(context: Context, text: String) {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }, "Share payment details"))
    }
}
