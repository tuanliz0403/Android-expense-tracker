package com.example.spendtracker.domain.model

import com.example.spendtracker.data.local.IncomePeriodEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomeRulesTest {
    @Test fun `income reset closes current boundary and opens a new period`() {
        val current = IncomePeriodEntity(id = 9, startedAt = 100, createdAt = 100)
        val (ended, next) = IncomeRules.reset(current, 800)
        assertEquals(9L, ended?.id)
        assertEquals(800L, ended?.endedAt)
        assertEquals(800L, next.startedAt)
        assertNull(next.endedAt)
    }
}
