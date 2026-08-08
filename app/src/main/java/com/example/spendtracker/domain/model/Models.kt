package com.example.spendtracker.domain.model

data class Transaction(
    val id: Long,
    val merchant: String,
    val amountCents: Long,
    val timestamp: Long,
    val source: String,
    val splitParticipantCount: Int = 0,
    val splitPaidCount: Int = 0,
    val splitClosed: Boolean = false,
    val splitPaymentCount: Int = 0
) {
    val hasSplit: Boolean get() = splitParticipantCount > 0
    val splitCompleted: Boolean get() = hasSplit && (splitClosed || splitPaidCount == splitParticipantCount)
}

data class SplitLineItem(
    val id: Long,
    val title: String,
    val amountCents: Long,
    val timestamp: Long
)

data class BillSplitParticipant(
    val id: Long,
    val name: String,
    val isOwner: Boolean,
    val isWaived: Boolean,
    val isPaid: Boolean,
    val paidAt: Long?
)

data class SplitParticipantEdit(
    val id: Long?,
    val name: String
)

data class IncomingPayment(
    val id: Long,
    val senderName: String?,
    val amountCents: Long,
    val receivedAt: Long,
    val participantId: Long?
)

data class IncomeSnapshot(
    val periodStartedAt: Long,
    val earnings: List<IncomingPayment>
) {
    val totalCents: Long = earnings.sumOf { it.amountCents }
}

data class BillSplitDetails(
    val id: Long,
    val transactionId: Long,
    val title: String,
    val totalCents: Long,
    val perPersonCents: Long,
    val accountName: String,
    val payId: String,
    val autoAssignAnonymous: Boolean,
    val isClosed: Boolean,
    val participants: List<BillSplitParticipant>,
    val payments: List<IncomingPayment>,
    val lineItems: List<SplitLineItem> = emptyList()
) {
    val paidCount get() = participants.count { it.isPaid }
    val activeParticipants get() = participants.filterNot { it.isWaived }
    val isCompleted get() = isClosed || (activeParticipants.isNotEmpty() && activeParticipants.all { it.isPaid })
    val unassignedPayments get() = payments.filter { it.participantId == null }
}

data class SpendingSnapshot(
    val periodStartedAt: Long,
    val transactions: List<Transaction>,
    val reimbursementsCents: Long = 0
) {
    val grossCents: Long = SpendingRules.totalCents(transactions)
    val totalCents: Long = (grossCents - reimbursementsCents).coerceAtLeast(0)
}
