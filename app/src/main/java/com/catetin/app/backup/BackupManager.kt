package com.catetin.app.backup

import com.catetin.app.data.db.AccountBalanceEntity
import com.catetin.app.data.db.AccountDao
import com.catetin.app.data.db.AccountEntity
import com.catetin.app.data.db.AllowlistDao
import com.catetin.app.data.db.AllowlistEntity
import com.catetin.app.data.db.BudgetDao
import com.catetin.app.data.db.BudgetEntity
import com.catetin.app.data.db.CategoryDao
import com.catetin.app.data.db.CategoryEntity
import com.catetin.app.data.db.DefaultSeed
import com.catetin.app.data.db.GmailDao
import com.catetin.app.data.db.GmailSenderEntity
import com.catetin.app.data.db.ReviewDao
import com.catetin.app.data.db.ReviewItemEntity
import com.catetin.app.data.db.RuleDao
import com.catetin.app.data.db.RuleEntity
import com.catetin.app.data.db.SourceEventDao
import com.catetin.app.data.db.SourceEventEntity
import com.catetin.app.data.db.TransactionDao
import com.catetin.app.data.db.TransactionEntity
import com.catetin.app.data.prefs.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

@Singleton
class BackupManager @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao,
    private val budgetDao: BudgetDao,
    private val ruleDao: RuleDao,
    private val allowlistDao: AllowlistDao,
    private val gmailDao: GmailDao,
    private val sourceEventDao: SourceEventDao,
    private val reviewDao: ReviewDao,
    private val settingsRepository: SettingsRepository,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun createJson(includeRawText: Boolean): String {
        val settings = settingsRepository.settings.first()
        val file = BackupFile(
            exportedAt = System.currentTimeMillis(),
            transactions = transactionDao.getAll().map { it.toBackup() },
            categories = categoryDao.getAll().map { it.toBackup() },
            accounts = accountDao.getAll().map { it.toBackup() },
            balances = accountDao.getAllBalances().map { it.toBackup() },
            budgets = budgetDao.getAll().map { it.toBackup() },
            rules = ruleDao.getAll().map { it.toBackup() },
            allowlist = allowlistDao.getAll().map { it.toBackup() },
            gmailSenders = gmailDao.getAllSenders().map { it.toBackup() },
            sourceEvents = sourceEventDao.getAll().map { it.toBackup(if (includeRawText) it.rawText else null) },
            reviewItems = reviewDao.getAll().map { it.toBackup() },
            settings = BackupSettings(
                defaultCurrency = settings.defaultCurrency,
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
                appLockEnabled = settings.appLockEnabled,
                lockTimeoutSeconds = settings.lockTimeoutSeconds,
                rawTextRetentionDays = settings.rawTextRetentionDays,
                gmailBackfillDays = settings.gmailBackfillDays,
                keepScreenSecure = settings.keepScreenSecure,
            ),
        )
        return json.encodeToString(BackupFile.serializer(), file)
    }

    suspend fun restore(raw: String): Result<BackupFile> = runCatching {
        val file = json.decodeFromString(BackupFile.serializer(), raw)
        require(file.format == "catetin-backup") { "Bukan berkas cadangan Catet.in" }
        require(file.version in 1..1) { "Versi cadangan tidak didukung" }

        reviewDao.deleteAll()
        sourceEventDao.deleteAll()
        transactionDao.deleteAll()
        budgetDao.deleteAll()
        ruleDao.deleteAll()
        accountDao.deleteAllBalances()
        accountDao.deleteAll()
        categoryDao.deleteAll()

        categoryDao.insertAll((file.categories.ifEmpty { DefaultSeed.categories.map { it.toBackup() } }).map { it.toEntity() })
        file.transactions.forEach { transactionDao.insert(it.toEntity()) }
        file.accounts.forEach { accountDao.insert(it.toEntity()) }
        file.balances.forEach { accountDao.insertBalance(it.toEntity()) }
        file.budgets.forEach { budgetDao.insert(it.toEntity()) }
        file.rules.forEach { ruleDao.insert(it.toEntity()) }
        allowlistDao.insertAll(file.allowlist.map { it.toEntity() })
        file.gmailSenders.forEach { gmailDao.upsertSender(it.toEntity()) }
        file.sourceEvents.forEach { sourceEventDao.insertIgnore(it.toEntity()) }
        file.reviewItems.forEach { reviewDao.insert(it.toEntity()) }
        file
    }
}

private fun TransactionEntity.toBackup() = BackupTransaction(
    id = id, amount = amount, currency = currency, direction = direction, merchant = merchant,
    category = category, accountId = accountId, institution = institution, occurredAt = occurredAt,
    narration = narration, reference = reference, status = status, createdAt = createdAt, updatedAt = updatedAt,
)
private fun BackupTransaction.toEntity() = TransactionEntity(
    id = id, amount = amount, currency = currency, direction = direction, merchant = merchant,
    category = category, accountId = accountId, institution = institution, occurredAt = occurredAt,
    narration = narration, reference = reference, status = status, createdAt = createdAt, updatedAt = updatedAt,
)
private fun CategoryEntity.toBackup() = BackupCategory(id, name, icon, color, isDefault, isIncome, sortOrder)
private fun BackupCategory.toEntity() = CategoryEntity(id, name, icon, color, isDefault, isIncome, sortOrder)
private fun AccountEntity.toBackup() = BackupAccount(id, institution, displayName, last4, currency, kind, createdAt)
private fun BackupAccount.toEntity() = AccountEntity(id, institution, displayName, last4, currency, kind, createdAt)
private fun AccountBalanceEntity.toBackup() = BackupBalance(id, accountId, balance, currency, recordedAt)
private fun BackupBalance.toEntity() = AccountBalanceEntity(id, accountId, balance, currency, recordedAt)
private fun BudgetEntity.toBackup() = BackupBudget(id, name, type, amount, currency, category, period, createdAt)
private fun BackupBudget.toEntity() = BudgetEntity(id, name, type, amount, currency, category, period, createdAt)
private fun RuleEntity.toBackup() = BackupRule(id, name, enabled, conditionsJson, actionsJson, createdAt, updatedAt)
private fun BackupRule.toEntity() = RuleEntity(id, name, enabled, conditionsJson, actionsJson, createdAt, updatedAt)
private fun AllowlistEntity.toBackup() = BackupAllowlist(packageName, label, enabled, addedBy, createdAt)
private fun BackupAllowlist.toEntity() = AllowlistEntity(packageName, label, enabled, addedBy, createdAt)
private fun GmailSenderEntity.toBackup() = BackupSender(pattern, label, enabled, createdAt)
private fun BackupSender.toEntity() = GmailSenderEntity(pattern, label, enabled, createdAt)
private fun SourceEventEntity.toBackup(raw: String?) = BackupSourceEvent(
    id, type, externalId, sourceApp, title, raw, receivedAt, parserId, parseStatus, transactionId, institution, createdAt,
)
private fun BackupSourceEvent.toEntity() = SourceEventEntity(
    id, type, externalId, sourceApp, title, rawText, receivedAt, parserId, parseStatus, transactionId, institution, createdAt,
)
private fun ReviewItemEntity.toBackup() = BackupReviewItem(
    id, kind, transactionId, candidateTransactionId, sourceEventId, reason, createdAt, resolvedAt, resolution,
)
private fun BackupReviewItem.toEntity() = ReviewItemEntity(
    id, kind, transactionId, candidateTransactionId, sourceEventId, reason, createdAt, resolvedAt, resolution,
)
