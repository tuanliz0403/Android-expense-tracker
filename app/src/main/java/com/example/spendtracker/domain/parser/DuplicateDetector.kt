package com.example.spendtracker.domain.parser

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object DuplicateDetector {
    fun hash(timestamp: Long, merchant: String, amountCents: Long, notificationText: String): String {
        val canonical = "$timestamp|${merchant.trim().lowercase()}|$amountCents|${notificationText.trim()}"
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
