package com.example.spendtracker.domain.model

object BillSplitMatcher {
    fun isExactAmount(requiredCents: Long, incomingCents: Long): Boolean =
        requiredCents > 0 && requiredCents == incomingCents

    fun countsAsIncome(activeSplitAmounts: List<Long>, incomingCents: Long): Boolean =
        activeSplitAmounts.none { isExactAmount(it, incomingCents) }

    fun matchingParticipantId(senderName: String?, unpaidParticipants: List<BillSplitParticipant>): Long? {
        val sender = senderName?.normalizedName()?.takeIf { it.isNotBlank() } ?: return null
        return unpaidParticipants.filter { it.name.normalizedName() == sender }.singleOrNull()?.id
    }

    fun participantForPayment(
        senderName: String?,
        unpaidParticipants: List<BillSplitParticipant>,
        autoAssignAnonymous: Boolean
    ): Long? = matchingParticipantId(senderName, unpaidParticipants)
        ?: if (autoAssignAnonymous) unpaidParticipants.firstOrNull { !it.isOwner }?.id else null

    private fun String.normalizedName(): String = lowercase().trim().replace(Regex("\\s+"), " ")
}
