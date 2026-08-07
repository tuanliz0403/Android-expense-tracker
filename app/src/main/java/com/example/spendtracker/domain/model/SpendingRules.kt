package com.example.spendtracker.domain.model

import com.example.spendtracker.data.local.SpendingPeriodEntity

object SpendingRules {
    fun totalCents(transactions: List<Transaction>): Long = transactions.sumOf { it.amountCents }

    fun reset(current: SpendingPeriodEntity?, timestamp: Long): Pair<SpendingPeriodEntity?, SpendingPeriodEntity> =
        current?.copy(endedAt = timestamp) to SpendingPeriodEntity(startedAt = timestamp, createdAt = timestamp)
}
