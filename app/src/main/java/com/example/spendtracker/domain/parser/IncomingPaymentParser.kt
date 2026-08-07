package com.example.spendtracker.domain.parser

import javax.inject.Inject

data class ParsedIncomingPayment(val senderName: String?, val amountCents: Long)

class IncomingPaymentParser @Inject constructor() {
    private val anonymousPaymentPattern = Regex(
        "you(?:'|’)?ve\\s+been\\s+paid\\s+\\$?([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s+into\\s+your\\s+account",
        RegexOption.IGNORE_CASE
    )

    private val patterns = listOf(
        Regex("(?:you\\s+)?received\\s+\\$?([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s+from\\s+(.+?)(?=[.!]?(?:\\s+(?:to|into|on|reference|available|balance)\\b)|[.!]?$)", RegexOption.IGNORE_CASE),
        Regex("\\$?([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s+received\\s+from\\s+(.+?)(?=[.!]?(?:\\s+(?:to|into|on|reference|available|balance)\\b)|[.!]?$)", RegexOption.IGNORE_CASE),
        Regex("payment\\s+of\\s+\\$?([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s+from\\s+(.+?)(?=[.!]?(?:\\s+(?:to|into|on|reference|available|balance)\\b)|[.!]?$)", RegexOption.IGNORE_CASE),
        Regex("(.+?)\\s+paid\\s+you\\s+\\$?([0-9][0-9,]*(?:\\.[0-9]{1,2})?)(?=[.!]?(?:\\s+(?:to|into|on|reference|available|balance)\\b)|[.!]?$)", RegexOption.IGNORE_CASE)
    )

    fun parse(text: String): ParsedIncomingPayment? {
        // Markdown markers may be present in pasted parser tests, but are not
        // part of the payment data and should not affect recognition.
        val normalized = text.replace("**", "").replace(Regex("\\s+"), " ").trim()
        anonymousPaymentPattern.find(normalized)?.let { match ->
            val amount = CurrencyParser.parseCents(match.groupValues[1])
                ?: return@let
            return ParsedIncomingPayment(senderName = null, amountCents = amount)
        }
        patterns.forEachIndexed { index, regex ->
            val match = regex.find(normalized) ?: return@forEachIndexed
            val amountRaw = if (index == 3) match.groupValues[2] else match.groupValues[1]
            val senderRaw = if (index == 3) match.groupValues[1] else match.groupValues[2]
            val amount = CurrencyParser.parseCents(amountRaw) ?: return@forEachIndexed
            val sender = senderRaw.trim().trimEnd('.', ',', ';', ':').take(120).ifBlank { null }
            return ParsedIncomingPayment(sender, amount)
        }
        return null
    }
}
