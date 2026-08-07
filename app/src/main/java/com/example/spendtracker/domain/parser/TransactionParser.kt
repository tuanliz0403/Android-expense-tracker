package com.example.spendtracker.domain.parser

import javax.inject.Inject

data class ParsedTransaction(val merchant: String, val amountCents: Long)

class TransactionParser @Inject constructor() {
    private val patterns = listOf(
        Regex("(?:you\\s+)?spent\\s+\\$?([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s+at\\s+(.+?)(?=[.!]?(?:\\s+(?:using|with|on|from|balance|available|card ending|so far)\\b)|[.!]?$)", RegexOption.IGNORE_CASE),
        Regex("card\\s+purchase\\s+of\\s+\\$?([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s+at\\s+(.+?)(?=[.!]?(?:\\s+(?:using|with|on|from|balance|available|card ending|so far)\\b)|[.!]?$)", RegexOption.IGNORE_CASE),
        Regex("\\$?([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s+spent\\s+at\\s+(.+?)(?=[.!]?(?:\\s+(?:using|with|on|from|balance|available|card ending|so far)\\b)|[.!]?$)", RegexOption.IGNORE_CASE),
        Regex("purchase\\s*:\\s*(.+?)\\s+\\$?([0-9][0-9,]*(?:\\.[0-9]{1,2})?)(?=[.!]?(?:\\s+(?:using|with|on|from|balance|available|card ending|so far)\\b)|[.!]?$)", RegexOption.IGNORE_CASE),
        Regex("payment\\s+(?:of\\s+)?\\$?([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s+(?:to|at)\\s+(.+?)(?=[.!]?(?:\\s+(?:using|with|on|from|balance|available|card ending|so far)\\b)|[.!]?$)", RegexOption.IGNORE_CASE)
    )

    fun parse(text: String): ParsedTransaction? {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank() || normalized.contains(Regex("declined|reversed|refund|cash withdrawal|pending", RegexOption.IGNORE_CASE))) return null
        patterns.forEachIndexed { index, regex ->
            val match = regex.find(normalized) ?: return@forEachIndexed
            val amountRaw = if (index == 3) match.groupValues[2] else match.groupValues[1]
            val merchantRaw = if (index == 3) match.groupValues[1] else match.groupValues[2]
            val cents = CurrencyParser.parseCents(amountRaw) ?: return@forEachIndexed
            val merchant = merchantRaw.trim().trimEnd('.', ',', ';', ':').take(120)
            if (merchant.isNotBlank()) return ParsedTransaction(merchant, cents)
        }
        return null
    }
}
