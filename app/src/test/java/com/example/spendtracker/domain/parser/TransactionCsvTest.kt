package com.example.spendtracker.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TransactionCsvTest {
    @Test fun `parses exported rows including commas and escaped quotes`() {
        val csv = """
            Merchant,Amount AUD,Transaction time,Source
            "Cafe, Sydney",12.45,1723075200000,MANUAL
            "The ""Best"" Shop",1,1723075300000,COMMBANK_NOTIFICATION
        """.trimIndent()

        val result = TransactionCsv.parse(csv)
        assertEquals(2, result.transactions.size)
        assertEquals("Cafe, Sydney", result.transactions[0].merchant)
        assertEquals(1245L, result.transactions[0].amountCents)
        assertEquals("The \"Best\" Shop", result.transactions[1].merchant)
        assertEquals(100L, result.transactions[1].amountCents)
        assertEquals(0, result.invalidRows)
    }

    @Test fun `counts malformed rows and rejects unrelated files`() {
        val result = TransactionCsv.parse("${TransactionCsv.HEADER}\nSHOP,nope,123,MANUAL")
        assertEquals(0, result.transactions.size)
        assertEquals(1, result.invalidRows)
        assertThrows(IllegalArgumentException::class.java) { TransactionCsv.parse("name,value\nanything,1") }
    }
}
