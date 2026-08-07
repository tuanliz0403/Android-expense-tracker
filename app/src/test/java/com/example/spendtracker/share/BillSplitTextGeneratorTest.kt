package com.example.spendtracker.share

import com.example.spendtracker.domain.model.BillSplitDetails
import com.example.spendtracker.domain.model.BillSplitParticipant
import org.junit.Assert.assertTrue
import org.junit.Test

class BillSplitTextGeneratorTest {
    @Test fun `share text includes progress participants and selected payment details`() {
        val split = BillSplitDetails(
            id = 1,
            transactionId = 2,
            title = "Hangout at the beach",
            totalCents = 4980,
            perPersonCents = 1245,
            accountName = "John Smith",
            payId = "john@example.com",
            autoAssignAnonymous = false,
            isClosed = false,
            participants = listOf(
                BillSplitParticipant(1, "Me", true, false, true, 1),
                BillSplitParticipant(2, "Alex", false, false, true, 2),
                BillSplitParticipant(3, "Sarah", false, false, false, null),
                BillSplitParticipant(4, "Michael", false, false, false, null)
            ),
            payments = emptyList()
        )

        val text = BillSplitTextGenerator.generate(split, 1_754_611_200_000)
        assertTrue(text.contains("Hangout at the beach"))
        assertTrue(text.contains("2 / 4 Paid"))
        assertTrue(text.contains("✓ Alex — Paid"))
        assertTrue(text.contains("○ Sarah — $12.45 owing"))
        assertTrue(text.contains("PayID: john@example.com"))
        assertTrue(text.contains("Account Name: John Smith"))
    }

    @Test fun `closed partial split keeps paid people and marks remaining people covered`() {
        val split = BillSplitDetails(
            id = 1,
            transactionId = 2,
            title = "Dinner",
            totalCents = 4980,
            perPersonCents = 1245,
            accountName = "John Smith",
            payId = "john@example.com",
            autoAssignAnonymous = false,
            isClosed = true,
            participants = listOf(
                BillSplitParticipant(1, "Me", true, false, true, 1),
                BillSplitParticipant(2, "Alex", false, false, true, 2),
                BillSplitParticipant(3, "Sarah", false, true, false, null),
                BillSplitParticipant(4, "Michael", false, true, false, null)
            ),
            payments = emptyList()
        )

        assertTrue(split.isCompleted)
        assertTrue(split.activeParticipants.map { it.name } == listOf("Me", "Alex"))
        val text = BillSplitTextGenerator.generate(split, 1_754_611_200_000)
        assertTrue(text.contains("2 / 4 Paid — Closed ✓"))
        assertTrue(text.contains("• Sarah — Covered by me"))
        assertTrue(text.contains("Split closed — remaining balance covered by me"))
    }
}
