package com.example.spendtracker.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BillSplitMatcherTest {
    private val unpaid = listOf(
        BillSplitParticipant(1, "Alex", false, false, false, null),
        BillSplitParticipant(2, "Sarah Jones", false, false, false, null)
    )

    @Test fun `only exact single share amount is eligible`() {
        assertTrue(BillSplitMatcher.isExactAmount(1245, 1245))
        assertFalse(BillSplitMatcher.isExactAmount(1245, 1244))
        assertFalse(BillSplitMatcher.isExactAmount(1245, 1246))
        assertFalse(BillSplitMatcher.isExactAmount(1245, 2490))
        assertFalse(BillSplitMatcher.countsAsIncome(listOf(1245), 1245))
        assertTrue(BillSplitMatcher.countsAsIncome(listOf(1245), 1244))
        assertTrue(BillSplitMatcher.countsAsIncome(listOf(1245), 1246))
        assertTrue(BillSplitMatcher.countsAsIncome(listOf(1245), 2490))
    }

    @Test fun `sender must uniquely equal participant name`() {
        assertEquals(1L, BillSplitMatcher.matchingParticipantId(" alex ", unpaid))
        assertEquals(2L, BillSplitMatcher.matchingParticipantId("SARAH   JONES", unpaid))
        assertNull(BillSplitMatcher.matchingParticipantId("Sarah", unpaid))
        assertNull(BillSplitMatcher.matchingParticipantId(null, unpaid))
        assertNull(BillSplitMatcher.matchingParticipantId("Alex", unpaid + unpaid.first().copy(id = 3)))
    }

    @Test fun `unnamed split assigns exact payments in arrival order without assigning owner`() {
        val choices = listOf(
            BillSplitParticipant(10, "Me", true, false, false, null),
            BillSplitParticipant(11, "Any sender 1", false, false, false, null),
            BillSplitParticipant(12, "Any sender 2", false, false, false, null)
        )
        assertEquals(11L, BillSplitMatcher.participantForPayment(null, choices, true))
        assertNull(BillSplitMatcher.participantForPayment(null, choices, false))
    }
}
