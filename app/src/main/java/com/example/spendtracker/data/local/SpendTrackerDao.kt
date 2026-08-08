package com.example.spendtracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Embedded
import androidx.room.Relation
import kotlinx.coroutines.flow.Flow

@Dao
interface SpendTrackerDao {
    @Query("SELECT * FROM spending_periods WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeCurrentPeriod(): Flow<SpendingPeriodEntity?>

    @Query("SELECT * FROM spending_periods WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun currentPeriod(): SpendingPeriodEntity?

    @Query("SELECT * FROM income_periods WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeCurrentIncomePeriod(): Flow<IncomePeriodEntity?>

    @Query("SELECT * FROM income_periods WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun currentIncomePeriod(): IncomePeriodEntity?

    @Query("SELECT * FROM incoming_payments WHERE countsAsIncome = 1 AND receivedAt >= :startedAt ORDER BY receivedAt DESC")
    fun observeEarningsSince(startedAt: Long): Flow<List<IncomingPaymentEntity>>

    @Query("""
        SELECT t.*,
          COALESCE((SELECT COUNT(*) FROM bill_split_participants p WHERE p.splitId = b.id AND p.isWaived = 0), 0) AS splitParticipantCount,
          COALESCE((SELECT COUNT(*) FROM bill_split_participants p WHERE p.splitId = b.id AND p.isPaid = 1 AND p.isWaived = 0), 0) AS splitPaidCount,
          COALESCE(b.isClosed, 0) AS splitClosed,
          COALESCE((SELECT COUNT(*) FROM incoming_payments ip WHERE ip.splitId = b.id), 0) AS splitPaymentCount
        FROM transactions t
        LEFT JOIN bill_split_transactions bst ON bst.transactionId = t.id
        LEFT JOIN bill_splits b ON b.id = bst.splitId
        WHERE t.combinedIntoTransactionId IS NULL
          AND t.deletedAt IS NULL
          AND (t.transactionTimestamp >= :startedAt OR t.importedIntoPeriodStartedAt = :startedAt)
        ORDER BY t.transactionTimestamp DESC
    """)
    fun observeTransactionsSince(startedAt: Long): Flow<List<TransactionWithSplitStatus>>

    @Query("""
        SELECT COALESCE(SUM(
            b.perPersonCents * (SELECT COUNT(*) FROM bill_split_participants p
                WHERE p.splitId = b.id AND p.isPaid = 1 AND p.isOwner = 0)
        ), 0)
        FROM bill_splits b
        WHERE EXISTS (
            SELECT 1 FROM bill_split_transactions bst
            INNER JOIN transactions t ON t.id = bst.transactionId
            WHERE bst.splitId = b.id
              AND t.deletedAt IS NULL
              AND t.combinedIntoTransactionId IS NULL
              AND (t.transactionTimestamp >= :startedAt OR t.importedIntoPeriodStartedAt = :startedAt)
        )
    """)
    fun observeReimbursementsSince(startedAt: Long): Flow<Long>

    @Query("""
        SELECT t.*,
          COALESCE((SELECT COUNT(*) FROM bill_split_participants p WHERE p.splitId = b.id AND p.isWaived = 0), 0) AS splitParticipantCount,
          COALESCE((SELECT COUNT(*) FROM bill_split_participants p WHERE p.splitId = b.id AND p.isPaid = 1 AND p.isWaived = 0), 0) AS splitPaidCount,
          COALESCE(b.isClosed, 0) AS splitClosed,
          COALESCE((SELECT COUNT(*) FROM incoming_payments ip WHERE ip.splitId = b.id), 0) AS splitPaymentCount
        FROM transactions t
        LEFT JOIN bill_split_transactions bst ON bst.transactionId = t.id
        LEFT JOIN bill_splits b ON b.id = bst.splitId
        WHERE t.combinedIntoTransactionId IS NULL
          AND t.deletedAt IS NULL
        ORDER BY t.transactionTimestamp DESC
    """)
    fun observeAllTransactions(): Flow<List<TransactionWithSplitStatus>>

    @Query("""
        SELECT t.*,
          COALESCE((SELECT COUNT(*) FROM bill_split_participants p WHERE p.splitId = b.id AND p.isWaived = 0), 0) AS splitParticipantCount,
          COALESCE((SELECT COUNT(*) FROM bill_split_participants p WHERE p.splitId = b.id AND p.isPaid = 1 AND p.isWaived = 0), 0) AS splitPaidCount,
          COALESCE(b.isClosed, 0) AS splitClosed,
          COALESCE((SELECT COUNT(*) FROM incoming_payments ip WHERE ip.splitId = b.id), 0) AS splitPaymentCount
        FROM transactions t
        LEFT JOIN bill_split_transactions bst ON bst.transactionId = t.id
        LEFT JOIN bill_splits b ON b.id = bst.splitId
        WHERE t.combinedIntoTransactionId IS NULL AND t.deletedAt IS NOT NULL
        ORDER BY t.deletedAt DESC
    """)
    fun observeDeletedTransactions(): Flow<List<TransactionWithSplitStatus>>

    @Query("SELECT * FROM transactions WHERE combinedIntoTransactionId IS NULL AND deletedAt IS NULL ORDER BY transactionTimestamp DESC")
    suspend fun allTransactions(): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Query("SELECT * FROM transactions WHERE merchant = :merchant AND amountCents = :amountCents AND transactionTimestamp = :timestamp LIMIT 1")
    suspend fun matchingTransaction(merchant: String, amountCents: Long, timestamp: Long): TransactionEntity?

    @Query("UPDATE transactions SET importedIntoPeriodStartedAt = :periodStartedAt WHERE id = :transactionId")
    suspend fun includeTransactionInPeriod(transactionId: Long, periodStartedAt: Long)

    @Query("UPDATE transactions SET deletedAt = :deletedAt WHERE id IN (:transactionIds) AND deletedAt IS NULL")
    suspend fun moveTransactionsToBin(transactionIds: List<Long>, deletedAt: Long): Int

    @Query("UPDATE transactions SET deletedAt = NULL WHERE id IN (:transactionIds) AND deletedAt IS NOT NULL")
    suspend fun restoreTransactions(transactionIds: List<Long>): Int

    @Query("SELECT id FROM transactions WHERE deletedAt IS NOT NULL")
    suspend fun deletedTransactionIds(): List<Long>

    @Query("DELETE FROM transactions WHERE id IN (:transactionIds) AND deletedAt IS NOT NULL")
    suspend fun deleteTransactionsById(transactionIds: List<Long>): Int

    @Query("UPDATE transactions SET combinedIntoTransactionId = :combinedId WHERE id IN (:transactionIds)")
    suspend fun hideTransactionsInCombined(transactionIds: List<Long>, combinedId: Long)

    @Query("UPDATE transactions SET combinedIntoTransactionId = NULL WHERE combinedIntoTransactionId IN (:combinedIds)")
    suspend fun restoreTransactionsFromCombined(combinedIds: List<Long>)

    @Query("SELECT * FROM transactions WHERE combinedIntoTransactionId = :combinedId ORDER BY transactionTimestamp")
    suspend fun transactionsInCombined(combinedId: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE combinedIntoTransactionId = :combinedId ORDER BY transactionTimestamp")
    fun observeTransactionsInCombined(combinedId: Long): Flow<List<TransactionEntity>>

    @Query("DELETE FROM transactions WHERE id = :transactionId")
    suspend fun hardDeleteTransaction(transactionId: Long): Int

    @Query("DELETE FROM bill_splits WHERE id IN (SELECT splitId FROM bill_split_transactions WHERE transactionId IN (:transactionIds))")
    suspend fun deleteSplitsForTransactions(transactionIds: List<Long>): Int

    @Insert
    suspend fun insertPeriod(period: SpendingPeriodEntity): Long

    @Insert
    suspend fun insertIncomePeriod(period: IncomePeriodEntity): Long

    @Insert
    suspend fun insertSplit(split: BillSplitEntity): Long

    @Insert
    suspend fun insertParticipants(participants: List<BillSplitParticipantEntity>)

    @Insert
    suspend fun insertSplitTransactions(rows: List<BillSplitTransactionEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIncomingPayment(payment: IncomingPaymentEntity): Long

    @Transaction
    @Query("SELECT b.* FROM bill_splits b INNER JOIN bill_split_transactions bst ON bst.splitId = b.id WHERE bst.transactionId = :transactionId LIMIT 1")
    fun observeSplitForTransaction(transactionId: Long): Flow<BillSplitAggregate?>

    @Query("""
        SELECT b.* FROM bill_splits b
        WHERE b.perPersonCents = :amountCents
          AND b.isClosed = 0
          AND EXISTS (
              SELECT 1 FROM bill_split_transactions bst
              INNER JOIN transactions t ON t.id = bst.transactionId
              WHERE bst.splitId = b.id AND t.deletedAt IS NULL AND t.combinedIntoTransactionId IS NULL
          )
          AND EXISTS (SELECT 1 FROM bill_split_participants p WHERE p.splitId = b.id AND p.isPaid = 0 AND p.isWaived = 0)
    """)
    suspend fun activeSplitsForAmount(amountCents: Long): List<BillSplitEntity>

    @Query("SELECT * FROM bill_split_participants WHERE splitId = :splitId AND isPaid = 0 AND isWaived = 0")
    suspend fun unpaidParticipants(splitId: Long): List<BillSplitParticipantEntity>

    @Query("SELECT * FROM bill_split_participants WHERE splitId = :splitId ORDER BY id")
    suspend fun participantsForSplit(splitId: Long): List<BillSplitParticipantEntity>

    @Query("SELECT COUNT(*) FROM incoming_payments WHERE splitId = :splitId")
    suspend fun paymentCountForSplit(splitId: Long): Int

    @Query("UPDATE bill_split_participants SET name = :name WHERE id = :participantId AND splitId = :splitId AND isOwner = 0")
    suspend fun renameParticipant(splitId: Long, participantId: Long, name: String): Int

    @Query("DELETE FROM bill_split_participants WHERE id = :participantId AND splitId = :splitId AND isOwner = 0 AND isPaid = 0")
    suspend fun deleteUnpaidParticipant(splitId: Long, participantId: Long): Int

    @Query("UPDATE bill_splits SET perPersonCents = :perPersonCents, autoAssignAnonymous = :autoAssignAnonymous WHERE id = :splitId")
    suspend fun updateSplitPeopleSettings(splitId: Long, perPersonCents: Long, autoAssignAnonymous: Boolean): Int

    @Query("SELECT * FROM bill_split_participants WHERE splitId = :splitId AND isPaid = 0 AND isWaived = 1 AND isOwner = 0")
    suspend fun unpaidParticipantsForReopen(splitId: Long): List<BillSplitParticipantEntity>

    @Query("UPDATE bill_split_participants SET isWaived = 1 WHERE splitId = :splitId AND isOwner = 0 AND isPaid = 0")
    suspend fun waiveUnpaidParticipants(splitId: Long): Int

    @Query("UPDATE bill_splits SET isClosed = 1 WHERE id = :splitId")
    suspend fun closeSplit(splitId: Long): Int

    @Query("UPDATE bill_splits SET isClosed = 0 WHERE id = :splitId")
    suspend fun reopenSplit(splitId: Long): Int

    @Query("UPDATE incoming_payments SET splitId = NULL, participantId = NULL, countsAsIncome = 1 WHERE splitId = :splitId")
    suspend fun preservePaymentsAsIncome(splitId: Long): Int

    @Query("DELETE FROM bill_splits WHERE id = :splitId")
    suspend fun deleteSplit(splitId: Long): Int

    @Query("UPDATE bill_split_participants SET isWaived = 0 WHERE splitId = :splitId AND id IN (:participantIds) AND isPaid = 0")
    suspend fun reactivateParticipants(splitId: Long, participantIds: List<Long>): Int

    @Query("UPDATE bill_split_participants SET isPaid = 1, paidAt = :paidAt WHERE id = :participantId AND isPaid = 0")
    suspend fun markParticipantPaid(participantId: Long, paidAt: Long): Int

    @Query("UPDATE bill_split_participants SET isPaid = 0, paidAt = NULL WHERE id = :participantId AND isPaid = 1")
    suspend fun markParticipantUnpaid(participantId: Long): Int

    @Query("UPDATE incoming_payments SET participantId = NULL WHERE participantId = :participantId")
    suspend fun unassignPaymentsFromParticipant(participantId: Long): Int

    @Query("UPDATE incoming_payments SET participantId = :participantId WHERE id = :paymentId AND participantId IS NULL")
    suspend fun assignPayment(paymentId: Long, participantId: Long): Int

    @Query("UPDATE incoming_payments SET splitId = :splitId, countsAsIncome = 0 WHERE id = :paymentId AND splitId IS NULL")
    suspend fun attachPaymentToSplit(paymentId: Long, splitId: Long): Int

    @Query("SELECT * FROM bill_splits WHERE id = :splitId LIMIT 1")
    suspend fun splitById(splitId: Long): BillSplitEntity?

    @Query("SELECT * FROM bill_splits WHERE transactionId = :transactionId LIMIT 1")
    suspend fun splitForTransaction(transactionId: Long): BillSplitEntity?

    @Query("SELECT * FROM incoming_payments WHERE id = :paymentId LIMIT 1")
    suspend fun paymentById(paymentId: Long): IncomingPaymentEntity?

    @Query("SELECT * FROM bill_split_participants WHERE id = :participantId LIMIT 1")
    suspend fun participantById(participantId: Long): BillSplitParticipantEntity?

    @Query("UPDATE spending_periods SET endedAt = :endedAt WHERE id = :id")
    suspend fun endPeriod(id: Long, endedAt: Long)

    @Query("UPDATE income_periods SET endedAt = :endedAt WHERE id = :id")
    suspend fun endIncomePeriod(id: Long, endedAt: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteTransactions()

    @Query("DELETE FROM spending_periods")
    suspend fun deletePeriods()

    @Query("DELETE FROM income_periods")
    suspend fun deleteIncomePeriods()

    @Query("DELETE FROM incoming_payments")
    suspend fun deleteIncomingPayments()
}

data class TransactionWithSplitStatus(
    val id: Long,
    val merchant: String,
    val amountCents: Long,
    val transactionTimestamp: Long,
    val notificationHash: String,
    val source: String,
    val importedIntoPeriodStartedAt: Long?,
    val combinedIntoTransactionId: Long?,
    val deletedAt: Long?,
    val createdAt: Long,
    val splitParticipantCount: Int,
    val splitPaidCount: Int,
    val splitClosed: Boolean,
    val splitPaymentCount: Int
)

data class BillSplitAggregate(
    @Embedded val split: BillSplitEntity,
    @Relation(parentColumn = "id", entityColumn = "splitId")
    val participants: List<BillSplitParticipantEntity>,
    @Relation(parentColumn = "id", entityColumn = "splitId")
    val payments: List<IncomingPaymentEntity>,
    @Relation(parentColumn = "transactionId", entityColumn = "combinedIntoTransactionId")
    val includedTransactions: List<TransactionEntity>
)
