package com.catetin.app.data.db

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver

@Database(
    entities = [
        TransactionEntity::class,
        SourceEventEntity::class,
        CategoryEntity::class,
        AccountEntity::class,
        AccountBalanceEntity::class,
        BudgetEntity::class,
        RuleEntity::class,
        AllowlistEntity::class,
        GmailSenderEntity::class,
        GmailSyncEntity::class,
        ReviewItemEntity::class,
        AssistantModelEntity::class,
        DiscoveryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class CatetDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun manualEntryDao(): ManualEntryDao
    abstract fun sourceEventDao(): SourceEventDao
    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun budgetDao(): BudgetDao
    abstract fun ruleDao(): RuleDao
    abstract fun allowlistDao(): AllowlistDao
    abstract fun gmailDao(): GmailDao
    abstract fun reviewDao(): ReviewDao
    abstract fun assistantModelDao(): AssistantModelDao
    abstract fun discoveryDao(): DiscoveryDao

    companion object {
        const val DATABASE_NAME = "catet.db"

        fun build(context: Context): CatetDatabase = Room.databaseBuilder(
            context,
            CatetDatabase::class.java,
            DATABASE_NAME,
        )
            .setDriver(AndroidSQLiteDriver())
            .addCallback(SeedCallback())
            .build()
    }
}

private class SeedCallback : RoomDatabase.Callback() {
    override suspend fun onCreate(connection: SQLiteConnection) {
        DefaultSeed.categories.forEachIndexed { index, category ->
            connection.execute(
                "INSERT OR IGNORE INTO categories (name, icon, color, is_default, is_income, sort_order) VALUES (?, ?, ?, 1, ?, ?)",
                listOf(category.name, category.icon, category.color, if (category.isIncome) 1 else 0, index),
            )
        }

        DefaultSeed.allowlist.forEach { (packageName, label) ->
            connection.execute(
                "INSERT OR IGNORE INTO notification_allowlist (package_name, label, enabled, added_by, created_at) VALUES (?, ?, 1, 'DEFAULT', ?)",
                listOf(packageName, label, System.currentTimeMillis()),
            )
        }

        DefaultSeed.gmailSenders.forEach { (pattern, label) ->
            connection.execute(
                "INSERT OR IGNORE INTO gmail_senders (pattern, label, enabled, created_at) VALUES (?, ?, 1, ?)",
                listOf(pattern, label, System.currentTimeMillis()),
            )
        }
        connection.execute(
            "INSERT OR IGNORE INTO gmail_sync_state (id, status) VALUES (1, 'NOT_CONNECTED')",
            emptyList(),
        )
    }
}

private fun SQLiteConnection.execute(sql: String, args: List<Any?>) {
    prepare(sql).use { statement ->
        args.forEachIndexed { index, value ->
            when (value) {
                null -> statement.bindNull(index + 1)
                is String -> statement.bindText(index + 1, value)
                is Int -> statement.bindLong(index + 1, value.toLong())
                is Long -> statement.bindLong(index + 1, value)
                is Boolean -> statement.bindLong(index + 1, if (value) 1 else 0)
                else -> error("Unsupported bind type")
            }
        }
        statement.step()
    }
}
