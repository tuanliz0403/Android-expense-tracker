package com.example.spendtracker.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["notificationHash"], unique = true)]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchant: String,
    val amountCents: Long,
    val transactionTimestamp: Long,
    val notificationHash: String,
    val source: String,
    @ColumnInfo(defaultValue = "NULL") val importedIntoPeriodStartedAt: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val combinedIntoTransactionId: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "spending_periods")
data class SpendingPeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "income_periods")
data class IncomePeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "bill_splits",
    foreignKeys = [ForeignKey(
        entity = TransactionEntity::class,
        parentColumns = ["id"],
        childColumns = ["transactionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["transactionId"], unique = true)]
)
data class BillSplitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    val title: String,
    val totalCents: Long,
    val perPersonCents: Long,
    val accountName: String,
    val payId: String,
    @ColumnInfo(defaultValue = "0") val autoAssignAnonymous: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isClosed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "bill_split_participants",
    foreignKeys = [ForeignKey(
        entity = BillSplitEntity::class,
        parentColumns = ["id"],
        childColumns = ["splitId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("splitId")]
)
data class BillSplitParticipantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val splitId: Long,
    val name: String,
    @ColumnInfo(defaultValue = "0") val isOwner: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isWaived: Boolean = false,
    val isPaid: Boolean = false,
    val paidAt: Long? = null
)

@Entity(
    tableName = "bill_split_transactions",
    primaryKeys = ["splitId", "transactionId"],
    foreignKeys = [
        ForeignKey(entity = BillSplitEntity::class, parentColumns = ["id"], childColumns = ["splitId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transactionId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("splitId"), Index(value = ["transactionId"], unique = true)]
)
data class BillSplitTransactionEntity(
    val splitId: Long,
    val transactionId: Long
)

@Entity(
    tableName = "incoming_payments",
    foreignKeys = [
        ForeignKey(
            entity = BillSplitEntity::class,
            parentColumns = ["id"],
            childColumns = ["splitId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = BillSplitParticipantEntity::class,
            parentColumns = ["id"],
            childColumns = ["participantId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("splitId"), Index("participantId"), Index(value = ["notificationHash"], unique = true)]
)
data class IncomingPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val splitId: Long?,
    val participantId: Long?,
    val senderName: String?,
    val amountCents: Long,
    val receivedAt: Long,
    val notificationHash: String,
    @ColumnInfo(defaultValue = "1") val countsAsIncome: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
