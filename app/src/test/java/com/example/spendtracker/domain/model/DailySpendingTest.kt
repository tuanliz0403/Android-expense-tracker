package com.example.spendtracker.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class DailySpendingTest {
    private val zone = ZoneId.of("Australia/Sydney")

    @Test
    fun `groups transactions by local day and includes zero spending days in average`() {
        val start = millis(2026, 8, 1, 9)
        val now = millis(2026, 8, 3, 18)
        val transactions = listOf(
            transaction(1, 1_000, millis(2026, 8, 1, 10)),
            transaction(2, 500, millis(2026, 8, 1, 22)),
            transaction(3, 1_500, millis(2026, 8, 3, 8))
        )

        val result = calculateDailySpending(transactions, start, now, zone)

        assertEquals(3_000, result.totalCents)
        assertEquals(1_000, result.averageCents)
        assertEquals(listOf(1_500L, 0L, 1_500L), result.days.map { it.totalCents })
        assertEquals(listOf(1, 0, 2), result.days.map { it.transactionCount })
    }

    @Test
    fun `returns empty summary while period is being initialised`() {
        assertEquals(DailySpendingSummary(emptyList(), 0, 0), calculateDailySpending(emptyList(), 0, zoneId = zone))
    }

    private fun millis(year: Int, month: Int, day: Int, hour: Int): Long =
        LocalDateTime.of(year, month, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun transaction(id: Long, cents: Long, timestamp: Long) =
        Transaction(id, "Merchant $id", cents, timestamp, "MANUAL")
}
