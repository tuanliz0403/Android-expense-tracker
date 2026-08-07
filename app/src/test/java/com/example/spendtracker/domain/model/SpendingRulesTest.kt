package com.example.spendtracker.domain.model

import com.example.spendtracker.data.local.SpendingPeriodEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpendingRulesTest {
    @Test fun `totals integer cents`() {
        val rows = listOf(Transaction(1, "A", 1250, 1, "MANUAL"), Transaction(2, "B", 820, 2, "MANUAL"))
        assertEquals(2070L, SpendingRules.totalCents(rows))
        assertEquals(1245L, SpendingSnapshot(1, rows, reimbursementsCents = 825L).totalCents)
    }

    @Test fun `reset closes current boundary and opens another without deleting history`() {
        val current = SpendingPeriodEntity(id = 7, startedAt = 100, createdAt = 100)
        val (ended, next) = SpendingRules.reset(current, 500)
        assertEquals(7L, ended?.id)
        assertEquals(500L, ended?.endedAt)
        assertEquals(500L, next.startedAt)
        assertNull(next.endedAt)
    }
}
