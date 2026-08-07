package com.example.spendtracker.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrencyParserTest {
    @Test fun `parses dollars exactly into integer cents`() {
        assertEquals(1250L, CurrencyParser.parseCents("$12.50"))
        assertEquals(123456L, CurrencyParser.parseCents("1,234.56"))
        assertEquals(800L, CurrencyParser.parseCents("8"))
    }
    @Test fun `rejects invalid zero and over precise amounts`() {
        assertNull(CurrencyParser.parseCents("12.345"))
        assertNull(CurrencyParser.parseCents("0"))
        assertNull(CurrencyParser.parseCents("hello"))
    }
}
