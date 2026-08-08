package com.example.spendtracker.data.repository

import androidx.room.withTransaction
import com.example.spendtracker.data.local.SpendTrackerDatabase
import com.example.spendtracker.data.local.SpendingPeriodEntity
import com.example.spendtracker.data.local.TransactionEntity
import com.example.spendtracker.data.local.BillSplitEntity
import com.example.spendtracker.data.local.BillSplitParticipantEntity
import com.example.spendtracker.data.local.IncomingPaymentEntity
import com.example.spendtracker.data.local.IncomePeriodEntity
import com.example.spendtracker.data.local.BillSplitTransactionEntity
import com.example.spendtracker.domain.model.BillSplitDetails
import com.example.spendtracker.domain.model.BillSplitParticipant
import com.example.spendtracker.domain.model.IncomingPayment
import com.example.spendtracker.domain.model.BillSplitMatcher
import com.example.spendtracker.domain.model.IncomeSnapshot
import com.example.spendtracker.domain.model.IncomeRules
import com.example.spendtracker.domain.model.SpendingSnapshot
import com.example.spendtracker.domain.model.Transaction
import com.example.spendtracker.domain.model.SplitParticipantEdit
import com.example.spendtracker.domain.model.SplitLineItem
import com.example.spendtracker.domain.model.SpendingRules
import com.example.spendtracker.domain.parser.DuplicateDetector
import com.example.spendtracker.domain.parser.TransactionCsv
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpendingRepository @Inject constructor(private val database: SpendTrackerDatabase) {
    private val dao = database.dao()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeSnapshot(): Flow<SpendingSnapshot> = dao.observeCurrentPeriod().flatMapLatest { period ->
        val start = period?.startedAt ?: 0L
        combine(dao.observeTransactionsSince(start), dao.observeReimbursementsSince(start)) { rows, reimbursements ->
            SpendingSnapshot(
                start,
                rows.map { Transaction(it.id, it.merchant, it.amountCents, it.transactionTimestamp, it.source, it.splitParticipantCount, it.splitPaidCount, it.splitClosed, it.splitPaymentCount) },
                reimbursements
            )
        }
    }

    fun observeAllTransactions(): Flow<List<Transaction>> = dao.observeAllTransactions().map { rows ->
        rows.map { Transaction(it.id, it.merchant, it.amountCents, it.transactionTimestamp, it.source, it.splitParticipantCount, it.splitPaidCount, it.splitClosed, it.splitPaymentCount) }
    }

    fun observeDeletedTransactions(): Flow<List<Transaction>> = dao.observeDeletedTransactions().map { rows ->
        rows.map { Transaction(it.id, it.merchant, it.amountCents, it.transactionTimestamp, it.source, it.splitParticipantCount, it.splitPaidCount, it.splitClosed, it.splitPaymentCount) }
    }

    fun observeCombinedLineItems(transactionId: Long): Flow<List<SplitLineItem>> =
        dao.observeTransactionsInCombined(transactionId).map { rows ->
            rows.map { SplitLineItem(it.id, it.merchant, it.amountCents, it.transactionTimestamp) }
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeIncomeSnapshot(): Flow<IncomeSnapshot> = dao.observeCurrentIncomePeriod().flatMapLatest { period ->
        val start = period?.startedAt ?: 0L
        dao.observeEarningsSince(start).map { rows ->
            IncomeSnapshot(start, rows.map {
                IncomingPayment(it.id, it.senderName, it.amountCents, it.receivedAt, it.participantId)
            })
        }
    }

    fun observeSplit(transactionId: Long): Flow<BillSplitDetails?> =
        dao.observeSplitForTransaction(transactionId).map { aggregate -> aggregate?.let {
            BillSplitDetails(
                id = it.split.id,
                transactionId = it.split.transactionId,
                title = it.split.title,
                totalCents = it.split.totalCents,
                perPersonCents = it.split.perPersonCents,
                accountName = it.split.accountName,
                payId = it.split.payId,
                autoAssignAnonymous = it.split.autoAssignAnonymous,
                isClosed = it.split.isClosed,
                participants = it.participants.sortedBy { participant -> participant.id }.map { participant ->
                    BillSplitParticipant(participant.id, participant.name, participant.isOwner, participant.isWaived, participant.isPaid, participant.paidAt)
                },
                payments = it.payments.sortedByDescending { payment -> payment.receivedAt }.map { payment ->
                    IncomingPayment(payment.id, payment.senderName, payment.amountCents, payment.receivedAt, payment.participantId)
                },
                lineItems = it.includedTransactions.sortedBy { item -> item.transactionTimestamp }.map { item ->
                    SplitLineItem(item.id, item.merchant, item.amountCents, item.transactionTimestamp)
                }
            )
        } }

    suspend fun createSplit(
        transaction: Transaction,
        title: String,
        participantNames: List<String>,
        accountName: String,
        payId: String
    ) = createCombinedSplit(listOf(transaction), title, participantNames, accountName, payId, false)

    suspend fun createCombinedSplit(
        transactions: List<Transaction>,
        title: String,
        participantNames: List<String>,
        accountName: String,
        payId: String,
        includeInCurrentPeriod: Boolean
    ) = database.withTransaction {
        require(transactions.isNotEmpty())
        require(participantNames.isNotEmpty())
        require(accountName.isNotBlank() && payId.isNotBlank())
        require(transactions.all { transaction ->
            !transaction.hasSplit || (!transaction.splitClosed && transaction.splitPaidCount <= 1 && transaction.splitPaymentCount == 0)
        })
        val componentIds = mutableListOf<Long>()
        transactions.forEach { transaction ->
            if (transaction.source == "COMBINED_SPLIT") {
                val children = dao.transactionsInCombined(transaction.id)
                require(children.isNotEmpty())
                dao.restoreTransactionsFromCombined(listOf(transaction.id))
                dao.deleteSplitsForTransactions(listOf(transaction.id))
                check(dao.hardDeleteTransaction(transaction.id) == 1)
                componentIds.addAll(children.map { it.id })
            } else {
                componentIds.add(transaction.id)
            }
        }
        val totalCents = transactions.sumOf { it.amountCents }
        val perPerson = totalCents / (participantNames.size + 1)
        require(perPerson > 0)
        val splitTitle = title.trim().ifBlank { if (transactions.size == 1) transactions.first().merchant else "Combined transactions" }
        val displayTransaction = if (transactions.size == 1) transactions.first() else {
            val timestamp = transactions.maxOf { it.timestamp }
            val currentPeriodStart = if (includeInCurrentPeriod) dao.currentPeriod()?.startedAt else null
            val combinedId = dao.insertTransaction(TransactionEntity(
                merchant = splitTitle,
                amountCents = totalCents,
                transactionTimestamp = timestamp,
                notificationHash = DuplicateDetector.hash(timestamp, splitTitle, totalCents, "combined:${transactions.map { it.id }.sorted().joinToString()}") ,
                source = "COMBINED_SPLIT",
                importedIntoPeriodStartedAt = currentPeriodStart
            ))
            check(combinedId != -1L)
            dao.hideTransactionsInCombined(componentIds, combinedId)
            Transaction(combinedId, splitTitle, totalCents, timestamp, "COMBINED_SPLIT")
        }
        val splitId = dao.insertSplit(BillSplitEntity(
            transactionId = displayTransaction.id,
            title = splitTitle,
            totalCents = totalCents,
            perPersonCents = perPerson,
            accountName = accountName.trim(),
            payId = payId.trim(),
            autoAssignAnonymous = participantNames.none { it.isNotBlank() }
        ))
        dao.insertParticipants(
            listOf(BillSplitParticipantEntity(splitId = splitId, name = "Me", isOwner = true, isPaid = true, paidAt = System.currentTimeMillis())) +
                participantNames.mapIndexed { index, name ->
                    BillSplitParticipantEntity(splitId = splitId, name = name.trim().ifBlank { "Any sender ${index + 1}" })
                }
        )
        dao.insertSplitTransactions(listOf(BillSplitTransactionEntity(splitId, displayTransaction.id)))
    }

    suspend fun recordIncomingPayment(senderName: String?, amountCents: Long, timestamp: Long, hash: String): Boolean =
        database.withTransaction {
            if (dao.currentIncomePeriod() == null) dao.insertIncomePeriod(IncomePeriodEntity(startedAt = timestamp))
            val matchingSplits = dao.activeSplitsForAmount(amountCents)
            val split = matchingSplits.singleOrNull()
            val participant = split?.let { candidate ->
                val unpaid = dao.unpaidParticipants(candidate.id)
                val matchId = BillSplitMatcher.participantForPayment(senderName, unpaid.map {
                    BillSplitParticipant(it.id, it.name, it.isOwner, it.isWaived, it.isPaid, it.paidAt)
                }, candidate.autoAssignAnonymous)
                unpaid.singleOrNull { it.id == matchId }
            }
            val paymentId = dao.insertIncomingPayment(IncomingPaymentEntity(
                splitId = split?.id,
                participantId = participant?.id,
                senderName = senderName,
                amountCents = amountCents,
                receivedAt = timestamp,
                notificationHash = hash,
                countsAsIncome = BillSplitMatcher.countsAsIncome(matchingSplits.map { it.perPersonCents }, amountCents)
            ))
            if (paymentId == -1L) return@withTransaction false
            participant?.let { dao.markParticipantPaid(it.id, timestamp) }
            true
        }

    suspend fun assignPayment(paymentId: Long, participantId: Long) = database.withTransaction {
        val payment = requireNotNull(dao.paymentById(paymentId))
        val participant = requireNotNull(dao.participantById(participantId))
        val split = requireNotNull(dao.splitById(participant.splitId))
        require(!participant.isPaid && payment.participantId == null)
        require(payment.amountCents == split.perPersonCents)
        require(payment.splitId == null || payment.splitId == split.id)
        if (payment.splitId == null) dao.attachPaymentToSplit(payment.id, split.id)
        check(dao.assignPayment(payment.id, participant.id) == 1)
        dao.markParticipantPaid(participant.id, payment.receivedAt)
    }

    suspend fun markParticipantPaid(participantId: Long, paidAt: Long = System.currentTimeMillis()) {
        dao.markParticipantPaid(participantId, paidAt)
    }

    suspend fun undoParticipantPaid(participantId: Long) = database.withTransaction {
        dao.unassignPaymentsFromParticipant(participantId)
        dao.markParticipantUnpaid(participantId)
    }

    suspend fun closeSplit(splitId: Long) = database.withTransaction {
        dao.waiveUnpaidParticipants(splitId)
        dao.closeSplit(splitId)
    }

    suspend fun reopenSplit(splitId: Long, participantIds: Set<Long>) = database.withTransaction {
        require(participantIds.isNotEmpty())
        val split = requireNotNull(dao.splitById(splitId))
        val allowed = dao.unpaidParticipantsForReopen(splitId).map { it.id }.toSet()
        require(participantIds.all { it in allowed })
        dao.reactivateParticipants(split.id, participantIds.toList())
        dao.reopenSplit(split.id)
    }

    suspend fun cancelSplit(splitId: Long) = database.withTransaction {
        requireNotNull(dao.splitById(splitId))
        dao.preservePaymentsAsIncome(splitId)
        check(dao.deleteSplit(splitId) == 1)
    }

    suspend fun editSplitParticipants(splitId: Long, edits: List<SplitParticipantEdit>) = database.withTransaction {
        require(edits.isNotEmpty() && edits.size <= 49)
        val split = requireNotNull(dao.splitById(splitId))
        val existing = dao.participantsForSplit(splitId).filterNot { it.isOwner }
        val existingById = existing.associateBy { it.id }
        val retainedIds = edits.mapNotNull { it.id }.toSet()
        require(retainedIds.size == edits.count { it.id != null })
        require(retainedIds.all { it in existingById })

        val countChanged = edits.size != existing.size || retainedIds != existingById.keys
        if (countChanged) {
            require(!split.isClosed)
            require(existing.none { it.isPaid })
            require(dao.paymentCountForSplit(splitId) == 0)
        }

        val anonymous = edits.all { it.name.isBlank() }
        edits.forEachIndexed { index, edit ->
            val storedName = edit.name.trim().ifBlank { "Any sender ${index + 1}" }
            edit.id?.let { dao.renameParticipant(splitId, it, storedName) }
        }
        existing.filter { it.id !in retainedIds }.forEach {
            check(dao.deleteUnpaidParticipant(splitId, it.id) == 1)
        }
        val additions = edits.mapIndexedNotNull { index, edit ->
            if (edit.id != null) null else BillSplitParticipantEntity(
                splitId = splitId,
                name = edit.name.trim().ifBlank { "Any sender ${index + 1}" }
            )
        }
        if (additions.isNotEmpty()) dao.insertParticipants(additions)
        val perPersonCents = split.totalCents / (edits.size + 1)
        require(perPersonCents > 0)
        check(dao.updateSplitPeopleSettings(splitId, perPersonCents, anonymous) == 1)
    }

    suspend fun undoCombination(transactionId: Long) = database.withTransaction {
        val split = dao.splitForTransaction(transactionId)
        if (split != null) {
            require(dao.paymentCountForSplit(split.id) == 0)
            require(dao.participantsForSplit(split.id).none { !it.isOwner && it.isPaid })
        }
        dao.restoreTransactionsFromCombined(listOf(transactionId))
        if (split != null) dao.deleteSplitsForTransactions(listOf(transactionId))
        check(dao.hardDeleteTransaction(transactionId) == 1)
    }

    suspend fun ensurePeriod(now: Long = System.currentTimeMillis()) = database.withTransaction {
        if (dao.currentPeriod() == null) dao.insertPeriod(SpendingPeriodEntity(startedAt = now))
    }

    suspend fun ensureIncomePeriod(now: Long = System.currentTimeMillis()) = database.withTransaction {
        if (dao.currentIncomePeriod() == null) dao.insertIncomePeriod(IncomePeriodEntity(startedAt = now))
    }

    suspend fun addParsed(merchant: String, amountCents: Long, timestamp: Long, hash: String): Boolean {
        ensurePeriod(timestamp)
        return dao.insertTransaction(TransactionEntity(merchant = merchant, amountCents = amountCents,
            transactionTimestamp = timestamp, notificationHash = hash, source = "COMMBANK_NOTIFICATION")) != -1L
    }

    suspend fun addManual(merchant: String, amountCents: Long, timestamp: Long) {
        ensurePeriod()
        val hash = DuplicateDetector.hash(timestamp, merchant, amountCents, "manual:${System.nanoTime()}")
        dao.insertTransaction(TransactionEntity(merchant = merchant.trim(), amountCents = amountCents,
            transactionTimestamp = timestamp, notificationHash = hash, source = "MANUAL"))
    }

    suspend fun reset(now: Long = System.currentTimeMillis()) = database.withTransaction {
        val (ended, next) = SpendingRules.reset(dao.currentPeriod(), now)
        ended?.let { dao.endPeriod(it.id, requireNotNull(it.endedAt)) }
        dao.insertPeriod(next)
    }

    suspend fun resetIncome(now: Long = System.currentTimeMillis()) = database.withTransaction {
        val (ended, next) = IncomeRules.reset(dao.currentIncomePeriod(), now)
        ended?.let { dao.endIncomePeriod(it.id, requireNotNull(it.endedAt)) }
        dao.insertIncomePeriod(next)
    }

    suspend fun resetBoth(now: Long = System.currentTimeMillis()) = database.withTransaction {
        val (ended, next) = SpendingRules.reset(dao.currentPeriod(), now)
        ended?.let { dao.endPeriod(it.id, requireNotNull(it.endedAt)) }
        dao.insertPeriod(next)
        val (endedIncome, nextIncome) = IncomeRules.reset(dao.currentIncomePeriod(), now)
        endedIncome?.let { dao.endIncomePeriod(it.id, requireNotNull(it.endedAt)) }
        dao.insertIncomePeriod(nextIncome)
    }

    suspend fun exportCsv(): String {
        val header = TransactionCsv.HEADER + "\n"
        return header + dao.allTransactions().joinToString("\n") {
            val merchant = "\"${it.merchant.replace("\"", "\"\"")}\""
            "$merchant,${"%.2f".format(java.util.Locale.US, it.amountCents / 100.0)},${it.transactionTimestamp},${it.source}"
        }
    }

    suspend fun importCsv(csv: String): ImportResult = database.withTransaction {
        val parsed = TransactionCsv.parse(csv)
        val periodStartedAt = dao.currentPeriod()?.startedAt ?: System.currentTimeMillis().also {
            dao.insertPeriod(SpendingPeriodEntity(startedAt = it))
        }
        var imported = 0
        var duplicates = 0
        parsed.transactions.forEach { row ->
            val existing = dao.matchingTransaction(row.merchant, row.amountCents, row.timestamp)
            if (existing != null) {
                dao.includeTransactionInPeriod(existing.id, periodStartedAt)
                duplicates++
            } else {
                val hash = DuplicateDetector.hash(row.timestamp, row.merchant, row.amountCents, "csv-import")
                val inserted = dao.insertTransaction(TransactionEntity(
                    merchant = row.merchant,
                    amountCents = row.amountCents,
                    transactionTimestamp = row.timestamp,
                    notificationHash = hash,
                    source = "CSV_IMPORT",
                    importedIntoPeriodStartedAt = periodStartedAt
                ))
                if (inserted == -1L) duplicates++ else imported++
            }
        }
        ImportResult(imported, duplicates, parsed.invalidRows)
    }

    suspend fun deleteTransactions(transactionIds: Set<Long>): Int =
        if (transactionIds.isEmpty()) 0 else dao.moveTransactionsToBin(transactionIds.toList(), System.currentTimeMillis())

    suspend fun restoreTransactions(transactionIds: Set<Long>): Int =
        if (transactionIds.isEmpty()) 0 else dao.restoreTransactions(transactionIds.toList())

    suspend fun permanentlyDeleteTransactions(transactionIds: Set<Long>): Int = database.withTransaction {
        if (transactionIds.isEmpty()) 0 else permanentlyDelete(transactionIds.toList())
    }

    suspend fun emptyRecycleBin(): Int = database.withTransaction {
        permanentlyDelete(dao.deletedTransactionIds())
    }

    private suspend fun permanentlyDelete(ids: List<Long>): Int {
        if (ids.isEmpty()) return 0
        dao.restoreTransactionsFromCombined(ids)
        dao.deleteSplitsForTransactions(ids)
        return dao.deleteTransactionsById(ids)
    }

    suspend fun deleteAll(now: Long = System.currentTimeMillis()) = database.withTransaction {
        dao.deleteIncomingPayments(); dao.deleteTransactions(); dao.deletePeriods(); dao.deleteIncomePeriods()
        dao.insertPeriod(SpendingPeriodEntity(startedAt = now))
        dao.insertIncomePeriod(IncomePeriodEntity(startedAt = now))
    }

}

data class ImportResult(val imported: Int, val duplicates: Int, val invalidRows: Int)
