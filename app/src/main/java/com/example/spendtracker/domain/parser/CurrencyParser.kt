package com.example.spendtracker.domain.parser

import java.math.BigDecimal
import java.math.RoundingMode

object CurrencyParser {
    fun parseCents(raw: String): Long? = runCatching {
        val cleaned = raw.trim().removePrefix("$").replace(",", "")
        if (!cleaned.matches(Regex("\\d+(?:\\.\\d{1,2})?"))) return null
        BigDecimal(cleaned).setScale(2, RoundingMode.UNNECESSARY)
            .movePointRight(2).longValueExact()
            .takeIf { it > 0 }
    }.getOrNull()
}
