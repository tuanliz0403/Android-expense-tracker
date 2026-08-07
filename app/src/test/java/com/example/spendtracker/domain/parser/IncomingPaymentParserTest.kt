package com.example.spendtracker.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomingPaymentParserTest {
    private val parser = IncomingPaymentParser()

    @Test fun `parses incoming payment and sender patterns`() {
        val cases = listOf(
            "You received $12.45 from Alex" to ("Alex" to 1245L),
            "$12.45 received from Sarah." to ("Sarah" to 1245L),
            "Payment of $1,234.56 from Michael into your account" to ("Michael" to 123456L),
            "John paid you $12.45" to ("John" to 1245L)
        )
        cases.forEach { (text, expected) ->
            val result = parser.parse(text)
            assertEquals(expected.first, result?.senderName)
            assertEquals(expected.second, result?.amountCents)
        }
    }

    @Test fun `does not treat purchases as incoming payments`() {
        assertNull(parser.parse("You spent $12.45 at COLES"))
    }

    @Test fun `parses paid into account notification without guessing sender`() {
        listOf(
            "You've been paid $12.45 into your account ending in 8637.",
            "You've been paid **$12.45** into your account ending in **8637**."
        ).forEach { text ->
            val result = parser.parse(text)
            assertEquals(1245L, result?.amountCents)
            assertNull(result?.senderName)
        }
    }
}
