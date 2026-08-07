package com.example.spendtracker.data.local

import androidx.room.Database
import androidx.room.AutoMigration
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.RoomDatabase

@Database(
    entities = [
        TransactionEntity::class,
        SpendingPeriodEntity::class,
        IncomePeriodEntity::class,
        BillSplitEntity::class,
        BillSplitParticipantEntity::class,
        BillSplitTransactionEntity::class,
        IncomingPaymentEntity::class
    ],
    version = 8,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4, spec = Migration3To4::class),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8)
    ],
    exportSchema = true
)
abstract class SpendTrackerDatabase : RoomDatabase() {
    abstract fun dao(): SpendTrackerDao
}

class Migration3To4 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT OR IGNORE INTO bill_split_transactions(splitId, transactionId) SELECT id, transactionId FROM bill_splits")
    }
}
