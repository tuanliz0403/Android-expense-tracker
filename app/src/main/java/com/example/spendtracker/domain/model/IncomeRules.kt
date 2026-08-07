package com.example.spendtracker.domain.model

import com.example.spendtracker.data.local.IncomePeriodEntity

object IncomeRules {
    fun reset(current: IncomePeriodEntity?, timestamp: Long): Pair<IncomePeriodEntity?, IncomePeriodEntity> =
        current?.copy(endedAt = timestamp) to IncomePeriodEntity(startedAt = timestamp, createdAt = timestamp)
}
