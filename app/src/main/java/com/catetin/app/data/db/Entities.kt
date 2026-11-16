package com.catetin.app.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        Index("occurred_at"),
        Index("category"),
        Index("direction"),
        Index("status"),
        Index("currency"),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "amount") val amount: String,
    @ColumnInfo(name = "currency") val currency: String = "IDR",
    @ColumnInfo(name = "direction") val direction: String,
    @ColumnInfo(name = "merchant") val merchant: String? = null,
    @ColumnInfo(name = "category") val category: String = "Lainnya",
    @ColumnInfo(name = "account_id") val accountId: Long? = null,
    @ColumnInfo(name = "institution") val institution: String? = null,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "narration") val narration: String? = null,
    @ColumnInfo(name = "reference") val reference: String? = null,
    @ColumnInfo(name = "status") val status: String = "CONFIRMED",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "source_events",
    indices = [
        Index(value = ["type", "external_id"], unique = true),
        Index("transaction_id"),
        Index("parse_status"),
        Index("received_at"),
    ],
)
data class SourceEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "external_id") val externalId: String,
    @ColumnInfo(name = "source_app") val sourceApp: String,
    @ColumnInfo(name = "title") val title: String? = null,
    @ColumnInfo(name = "raw_text") val rawText: String? = null,
    @ColumnInfo(name = "received_at") val receivedAt: Long,
    @ColumnInfo(name = "parser_id") val parserId: String? = null,
    @ColumnInfo(name = "parse_status") val parseStatus: String = "PENDING",
    @ColumnInfo(name = "transaction_id") val transactionId: Long? = null,
    @ColumnInfo(name = "institution") val institution: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "icon") val icon: String = "more_horiz",
    @ColumnInfo(name = "color") val color: String = "#5D6B67",
    @ColumnInfo(name = "is_default") val isDefault: Boolean = true,
    @ColumnInfo(name = "is_income") val isIncome: Boolean = false,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
)

@Entity(tableName = "accounts", indices = [Index(value = ["institution", "last4"], unique = true)])
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "institution") val institution: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "last4") val last4: String? = null,
    @ColumnInfo(name = "currency") val currency: String = "IDR",
    @ColumnInfo(name = "kind") val kind: String = "BANK",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "account_balances", indices = [Index("account_id"), Index("recorded_at")])
data class AccountBalanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "account_id") val accountId: Long,
    @ColumnInfo(name = "balance") val balance: String,
    @ColumnInfo(name = "currency") val currency: String = "IDR",
    @ColumnInfo(name = "recorded_at") val recordedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "budgets", indices = [Index("category"), Index("period")])
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "type") val type: String = "LIMIT",
    @ColumnInfo(name = "amount") val amount: String,
    @ColumnInfo(name = "currency") val currency: String = "IDR",
    @ColumnInfo(name = "category") val category: String? = null,
    @ColumnInfo(name = "period") val period: String = "MONTHLY",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "enabled") val enabled: Boolean = true,
    @ColumnInfo(name = "conditions_json") val conditionsJson: String,
    @ColumnInfo(name = "actions_json") val actionsJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "notification_allowlist")
data class AllowlistEntity(
    @PrimaryKey @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "enabled") val enabled: Boolean = true,
    @ColumnInfo(name = "added_by") val addedBy: String = "DEFAULT",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "gmail_senders")
data class GmailSenderEntity(
    @PrimaryKey @ColumnInfo(name = "pattern") val pattern: String,
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "enabled") val enabled: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "gmail_sync_state")
data class GmailSyncEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "account_hint") val accountHint: String? = null,
    @ColumnInfo(name = "last_history_id") val lastHistoryId: String? = null,
    @ColumnInfo(name = "last_sync_at") val lastSyncAt: Long? = null,
    @ColumnInfo(name = "status") val status: String = "NOT_CONNECTED",
    @ColumnInfo(name = "message") val message: String? = null,
    @ColumnInfo(name = "backfill_anchor_at") val backfillAnchorAt: Long? = null,
    @ColumnInfo(name = "next_page_token") val nextPageToken: String? = null,
    @ColumnInfo(name = "sync_mode") val syncMode: String? = null,
)

@Entity(
    tableName = "review_items",
    indices = [Index("kind"), Index("resolved_at"), Index("transaction_id")],
)
data class ReviewItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "transaction_id") val transactionId: Long? = null,
    @ColumnInfo(name = "candidate_transaction_id") val candidateTransactionId: Long? = null,
    @ColumnInfo(name = "source_event_id") val sourceEventId: Long? = null,
    @ColumnInfo(name = "reason") val reason: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "resolved_at") val resolvedAt: Long? = null,
    @ColumnInfo(name = "resolution") val resolution: String = "PENDING",
)

@Entity(tableName = "assistant_model")
data class AssistantModelEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "model_id") val modelId: String = "qwen3.5-0.8b",
    @ColumnInfo(name = "variant") val variant: String = "Q4_K_M",
    @ColumnInfo(name = "file_path") val filePath: String? = null,
    @ColumnInfo(name = "sha256") val sha256: String? = null,
    @ColumnInfo(name = "downloaded_at") val downloadedAt: Long? = null,
)

@Entity(tableName = "notification_discovery")
data class DiscoveryEntity(
    @PrimaryKey @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "last_seen") val lastSeen: Long,
    @ColumnInfo(name = "seen_count") val seenCount: Int,
)
