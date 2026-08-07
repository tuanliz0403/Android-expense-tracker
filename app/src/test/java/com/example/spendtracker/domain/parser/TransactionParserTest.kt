package com.example.spendtracker.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionParserTest {
    private val parser = TransactionParser()

    @Test fun `parses supported CommBank wording`() {
        val cases = listOf(
            "You spent $12.50 at WOOLWORTHS" to ("WOOLWORTHS" to 1250L),
            "Card purchase of $8.20 at McDonald's" to ("McDonald's" to 820L),
            "$45.00 spent at AMAZON AU" to ("AMAZON AU" to 4500L),
            "Purchase: COLES $23.40" to ("COLES" to 2340L),
            "Alert: You spent $1,234.56 at ACME STORE using card ending 1234" to ("ACME STORE" to 123456L)
        )
        cases.forEach { (text, expected) ->
            val parsed = parser.parse(text)
            assertEquals(expected.first, parsed?.merchant)
            assertEquals(expected.second, parsed?.amountCents)
        }
    }

    @Test fun `ignores non completed or unrelated notifications`() {
        assertNull(parser.parse("Your card purchase of $9.00 at SHOP was declined"))
        assertNull(parser.parse("You received a $20.00 refund at SHOP"))
        assertNull(parser.parse("Your account balance is $100.00"))
    }

    @Test fun `ignores monthly summary after transaction`() {
        val parsed = parser.parse(
            "$4.50 spent at Replenish Food & Beverage Company. " +
                "So far this month you've spent $26.00 on Groceries."
        )

        assertEquals("Replenish Food & Beverage Company", parsed?.merchant)
        assertEquals(450L, parsed?.amountCents)
    }
}
