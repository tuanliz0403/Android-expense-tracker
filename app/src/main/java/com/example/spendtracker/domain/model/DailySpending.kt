package com.example.spendtracker.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class DailySpending(
    val date: LocalDate,
    val totalCents: Long,
    val transactionCount: Int
)

data class DailySpendingSummary(
    val days: List<DailySpending>,
    val averageCents: Long,
    val totalCents: Long
)

fun calculateDailySpending(
    transactions: List<Transaction>,
    periodStartedAt: Long,
    now: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault()
): DailySpendingSummary {
    if (periodStartedAt <= 0L) return DailySpendingSummary(emptyList(), 0L, 0L)

    val startDate = Instant.ofEpochMilli(periodStartedAt).atZone(zoneId).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
    val endDate = maxOf(startDate, today)
    val totalsByDate = transactions
        .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zoneId).toLocalDate() }
        .mapValues { (_, dailyTransactions) ->
            dailyTransactions.sumOf { it.amountCents } to dailyTransactions.size
        }

    val dayCount = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
    val days = (0 until dayCount).map { offset ->
        val date = startDate.plusDays(offset.toLong())
        val (total, count) = totalsByDate[date] ?: (0L to 0)
        DailySpending(date, total, count)
    }.asReversed()
    val total = days.sumOf { it.totalCents }

    return DailySpendingSummary(
        days = days,
        averageCents = total / dayCount,
        totalCents = total
    )
}
