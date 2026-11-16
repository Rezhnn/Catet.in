package com.catetin.app.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupFile(
    val format: String = "catetin-backup",
    val version: Int = 1,
    val exportedAt: Long = 0L,
    val transactions: List<BackupTransaction> = emptyList(),
    val categories: List<BackupCategory> = emptyList(),
    val accounts: List<BackupAccount> = emptyList(),
    val balances: List<BackupBalance> = emptyList(),
    val budgets: List<BackupBudget> = emptyList(),
    val rules: List<BackupRule> = emptyList(),
    val allowlist: List<BackupAllowlist> = emptyList(),
    val gmailSenders: List<BackupSender> = emptyList(),
    val sourceEvents: List<BackupSourceEvent> = emptyList(),
    val reviewItems: List<BackupReviewItem> = emptyList(),
    val settings: BackupSettings = BackupSettings(),
)

@Serializable
data class BackupTransaction(
    val id: Long = 0,
    val amount: String = "0",
    val currency: String = "IDR",
    val direction: String = "EXPENSE",
    val merchant: String? = null,
    val category: String = "Lainnya",
    val accountId: Long? = null,
    val institution: String? = null,
    val occurredAt: Long = 0,
    val narration: String? = null,
    val reference: String? = null,
    val status: String = "CONFIRMED",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class BackupCategory(
    val id: Long = 0,
    val name: String = "",
    val icon: String = "more_horiz",
    val color: String = "#5D6B67",
    val isDefault: Boolean = true,
    val isIncome: Boolean = false,
    val sortOrder: Int = 0,
)

@Serializable
data class BackupAccount(
    val id: Long = 0,
    val institution: String = "",
    val displayName: String = "",
    val last4: String? = null,
    val currency: String = "IDR",
    val kind: String = "BANK",
    val createdAt: Long = 0,
)

@Serializable
data class BackupBalance(
    val id: Long = 0,
    val accountId: Long = 0,
    val balance: String = "0",
    val currency: String = "IDR",
    val recordedAt: Long = 0,
)

@Serializable
data class BackupBudget(
    val id: Long = 0,
    val name: String = "",
    val type: String = "LIMIT",
    val amount: String = "0",
    val currency: String = "IDR",
    val category: String? = null,
    val period: String = "MONTHLY",
    val createdAt: Long = 0,
)

@Serializable
data class BackupRule(
    val id: Long = 0,
    val name: String = "",
    val enabled: Boolean = true,
    val conditionsJson: String = "{}",
    val actionsJson: String = "{}",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class BackupAllowlist(
    val packageName: String = "",
    val label: String = "",
    val enabled: Boolean = true,
    val addedBy: String = "DEFAULT",
    val createdAt: Long = 0,
)

@Serializable
data class BackupSender(
    val pattern: String = "",
    val label: String = "",
    val enabled: Boolean = true,
    val createdAt: Long = 0,
)

@Serializable
data class BackupSourceEvent(
    val id: Long = 0,
    val type: String = "MANUAL",
    val externalId: String = "",
    val sourceApp: String = "",
    val title: String? = null,
    val rawText: String? = null,
    val receivedAt: Long = 0,
    val parserId: String? = null,
    val parseStatus: String = "PARSED",
    val transactionId: Long? = null,
    val institution: String? = null,
    val createdAt: Long = 0,
)

@Serializable
data class BackupReviewItem(
    val id: Long = 0,
    val kind: String = "UNPARSED",
    val transactionId: Long? = null,
    val candidateTransactionId: Long? = null,
    val sourceEventId: Long? = null,
    val reason: String = "",
    val createdAt: Long = 0,
    val resolvedAt: Long? = null,
    val resolution: String = "PENDING",
)

@Serializable
data class BackupSettings(
    val defaultCurrency: String = "IDR",
    val themeMode: String = "SYSTEM",
    val dynamicColor: Boolean = false,
    val appLockEnabled: Boolean = false,
    val lockTimeoutSeconds: Int = 60,
    val rawTextRetentionDays: Int = 90,
    val gmailBackfillDays: Int = 30,
    val keepScreenSecure: Boolean = false,
)
