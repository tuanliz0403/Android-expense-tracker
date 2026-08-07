package com.example.spendtracker.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DuplicateDetectorTest {
    @Test fun `same notification has stable identifier`() {
        val first = DuplicateDetector.hash(100, "COLES", 2340, "Purchase: COLES $23.40")
        val second = DuplicateDetector.hash(100, "COLES", 2340, "Purchase: COLES $23.40")
        assertEquals(first, second)
        assertEquals(64, first.length)
    }
    @Test fun `material change creates different identifier`() {
        assertNotEquals(DuplicateDetector.hash(100, "COLES", 2340, "text"), DuplicateDetector.hash(101, "COLES", 2340, "text"))
    }
}
