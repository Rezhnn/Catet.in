package com.catetin.app.data.repo

import com.catetin.app.data.db.AccountBalanceEntity
import com.catetin.app.data.db.AccountDao
import com.catetin.app.data.db.AccountEntity
import com.catetin.app.data.db.AllowlistDao
import com.catetin.app.data.db.AllowlistEntity
import com.catetin.app.data.db.AssistantModelDao
import com.catetin.app.data.db.AssistantModelEntity
import com.catetin.app.data.db.BudgetDao
import com.catetin.app.data.db.BudgetEntity
import com.catetin.app.data.db.CategoryDao
import com.catetin.app.data.db.CategoryEntity
import com.catetin.app.data.db.DefaultSeed
import com.catetin.app.data.db.GmailDao
import com.catetin.app.data.db.GmailSenderEntity
import com.catetin.app.data.db.GmailSyncEntity
import com.catetin.app.data.db.ReviewDao
import com.catetin.app.data.db.ReviewItemEntity
import com.catetin.app.data.db.RuleDao
import com.catetin.app.data.db.RuleEntity
import com.catetin.app.data.db.SourceEventDao
import com.catetin.app.data.db.SourceEventEntity
import com.catetin.app.data.db.TransactionDao
import com.catetin.app.data.db.TransactionEntity
import com.catetin.app.domain.dedupe.DedupeCandidate
import com.catetin.app.domain.dedupe.DedupeDecision
import com.catetin.app.domain.dedupe.DedupeEngine
import com.catetin.app.domain.dedupe.DedupeInput
import com.catetin.app.domain.dedupe.DedupeMatch
import com.catetin.app.domain.rules.RuleActionConfig
import com.catetin.app.domain.rules.RuleConfig
import com.catetin.app.domain.rules.RuleDraft
import com.catetin.app.domain.rules.RuleEngine
import com.catetin.app.util.Dates
import com.catetin.app.util.PeriodPreset
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class IngestOutcome(
    val sourceEventId: Long,
    val transactionId: Long? = null,
    val status: String,
    val duplicate: Boolean = false,
)

@Singleton
class FinanceRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val sourceEventDao: SourceEventDao,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao,
    private val budgetDao: BudgetDao,
    private val ruleDao: RuleDao,
    private val allowlistDao: AllowlistDao,
    private val reviewDao: ReviewDao,
    private val gmailDao: GmailDao,
    private val assistantModelDao: AssistantModelDao,
    private val discoveryDao: com.catetin.app.data.db.DiscoveryDao,
) {
    // Notification callbacks and the Gmail worker can ingest at the same time.
    // Candidate lookup and insert must be one critical section, otherwise both
    // can observe an empty ledger and persist the same real-world payment twice.
    private val dedupeMutex = Mutex()

    val recentTransactions: Flow<List<TransactionEntity>> = transactionDao.observeRecent(20)
    val categories: Flow<List<CategoryEntity>> = categoryDao.observeAll()
    val budgets: Flow<List<BudgetEntity>> = budgetDao.observeAll()
    val rules: Flow<List<RuleEntity>> = ruleDao.observeAll()
    val allowlist: Flow<List<AllowlistEntity>> = allowlistDao.observeAll()
    val pendingReviews: Flow<List<ReviewItemEntity>> = reviewDao.observePending()
    val pendingReviewCount: Flow<Int> = reviewDao.observePendingCount()
    val unparsed: Flow<List<SourceEventEntity>> = sourceEventDao.observeUnparsed()
    val notificationStats: Flow<List<com.catetin.app.data.db.SourceStat>> = sourceEventDao.observeNotificationStats()
    val gmailSync: Flow<GmailSyncEntity?> = gmailDao.observeSync()
    val gmailSenders: Flow<List<GmailSenderEntity>> = gmailDao.observeSenders()
    val accounts: Flow<List<AccountEntity>> = accountDao.observeAll()
    val assistantModel: Flow<AssistantModelEntity?> = assistantModelDao.observe()

    fun observeTransactions(preset: PeriodPreset): Flow<List<TransactionEntity>> {
        val range = Dates.range(preset)
        return transactionDao.observeBetween(range.start, range.end)
    }

    fun observeTransactions(start: Long, end: Long): Flow<List<TransactionEntity>> =
        transactionDao.observeBetween(start, end)

    fun searchTransactions(
        query: String,
        type: String?,
        category: String?,
    ): Flow<List<TransactionEntity>> {
        val all = Dates.range(PeriodPreset.ALL)
        return transactionDao.search(
            query = query.takeIf { it.isNotBlank() },
            type = type,
            category = category,
            start = all.start,
            end = all.end,
            limit = 300,
        )
    }

    fun observeTransaction(id: Long): Flow<TransactionEntity?> = transactionDao.observeById(id)

    fun observeSources(transactionId: Long): Flow<List<SourceEventEntity>> =
        sourceEventDao.observeByTransaction(transactionId)

    suspend fun getTransaction(id: Long): TransactionEntity? = transactionDao.getById(id)

    suspend fun getSourcesFor(transactionId: Long): List<SourceEventEntity> =
        sourceEventDao.getByTransaction(transactionId)

    suspend fun getSourceEvent(id: Long): SourceEventEntity? = sourceEventDao.getById(id)

    suspend fun getTransactionsBetween(start: Long, end: Long): List<TransactionEntity> =
        transactionDao.getBetween(start, end)

    suspend fun getAccounts(): List<AccountEntity> = accountDao.getAll()

    suspend fun getLatestBalances(): List<AccountBalanceEntity> = accountDao.getAllBalances()

    suspend fun addManual(
        amount: BigDecimal,
        direction: String,
        merchant: String?,
        category: String,
        occurredAt: Long = System.currentTimeMillis(),
        narration: String? = null,
        currency: String = "IDR",
    ): Long {
        val now = System.currentTimeMillis()
        val transactionId = transactionDao.insert(
            TransactionEntity(
                amount = amount.stripTrailingZeros().toPlainString(),
                currency = currency,
                direction = direction,
                merchant = merchant?.trim()?.ifBlank { null },
                category = category,
                occurredAt = occurredAt,
                narration = narration,
                status = "CONFIRMED",
                createdAt = now,
                updatedAt = now,
            ),
        )
        sourceEventDao.insertIgnore(
            SourceEventEntity(
                type = "MANUAL",
                externalId = UUID.randomUUID().toString(),
                sourceApp = "manual",
                title = "Input manual",
                receivedAt = occurredAt,
                parserId = "manual",
                parseStatus = "PARSED",
                transactionId = transactionId,
            ),
        )
        return transactionId
    }

    suspend fun ingest(artifact: com.catetin.parser.model.RawArtifact): IngestOutcome {
        val externalId = artifact.externalId
            ?: (artifact.sourceType.name + ":" + artifact.sourceApp + ":" + artifact.timestampMillis + ":" +
                artifact.text.hashCode())
        val eventId = sourceEventDao.insertIgnore(
            SourceEventEntity(
                type = artifact.sourceType.name,
                externalId = externalId,
                sourceApp = artifact.sourceApp,
                title = artifact.title,
                rawText = artifact.text.take(12_000),
                receivedAt = artifact.timestampMillis,
                parseStatus = "PENDING",
            ),
        )
        val event = if (eventId == -1L) {
            sourceEventDao.getByExternal(artifact.sourceType.name, externalId)
        } else {
            sourceEventDao.getById(eventId)
        } ?: return IngestOutcome(0, status = "IGNORED", duplicate = true)

        if (eventId == -1L) {
            return IngestOutcome(event.id, event.transactionId, event.parseStatus, duplicate = true)
        }

        return when (val parsed = com.catetin.parser.ParseEngine.parse(artifact)) {
            is com.catetin.parser.model.ParseResult.Parsed -> {
                val draft = RuleDraft(
                    amount = parsed.transaction.amount,
                    currency = parsed.transaction.currency,
                    direction = parsed.transaction.direction.name,
                    category = parsed.transaction.category,
                    merchant = parsed.transaction.merchant,
                    narration = parsed.transaction.narration,
                    institution = parsed.transaction.institution,
                    reference = parsed.transaction.reference,
                    occurredAt = parsed.transaction.occurredAtMillis,
                )
                val rules = ruleDao.getEnabled().map { rule ->
                    rule.id to (RuleEngine.decodeConditions(rule.conditionsJson) to RuleEngine.decodeActions(rule.actionsJson))
                }
                val outcome = RuleEngine.apply(rules, draft, artifact.text, artifact.sourceType.name)
                if (outcome.blocked) {
                    sourceEventDao.markParsed(
                        event.id,
                        "IGNORED",
                        parsed.transaction.parserId,
                        null,
                        parsed.transaction.institution,
                    )
                    return IngestOutcome(event.id, status = "IGNORED")
                }

                val finalDraft = outcome.draft
                val accountId = ensureAccount(
                    institution = finalDraft.institution ?: parsed.transaction.institution,
                    last4 = parsed.transaction.accountHint,
                    currency = finalDraft.currency,
                )
                val transactionId = saveWithDedupe(
                    event = event,
                    draft = finalDraft,
                    parserId = parsed.transaction.parserId,
                    accountId = accountId,
                    balanceAfter = parsed.transaction.balanceAfter,
                    confidence = parsed.transaction.confidence.name,
                )
                sourceEventDao.markParsed(
                    event.id,
                    "PARSED",
                    parsed.transaction.parserId,
                    transactionId,
                    parsed.transaction.institution,
                )
                IngestOutcome(event.id, transactionId, "PARSED")
            }
            is com.catetin.parser.model.ParseResult.Ignored -> {
                sourceEventDao.markParsed(event.id, "IGNORED", null, null, null)
                IngestOutcome(event.id, status = "IGNORED")
            }
            is com.catetin.parser.model.ParseResult.Unparsed -> {
                sourceEventDao.markParsed(event.id, "UNPARSED", null, null, null)
                reviewDao.insert(
                    ReviewItemEntity(
                        kind = "UNPARSED",
                        sourceEventId = event.id,
                        reason = parsed.reason,
                    ),
                )
                IngestOutcome(event.id, status = "UNPARSED")
            }
        }
    }

    private suspend fun saveWithDedupe(
        event: SourceEventEntity,
        draft: RuleDraft,
        parserId: String,
        accountId: Long?,
        balanceAfter: BigDecimal?,
        confidence: String,
    ): Long = dedupeMutex.withLock {
        saveWithDedupeLocked(event, draft, parserId, accountId, balanceAfter, confidence)
    }

    private suspend fun saveWithDedupeLocked(
        event: SourceEventEntity,
        draft: RuleDraft,
        parserId: String,
        accountId: Long?,
        balanceAfter: BigDecimal?,
        confidence: String,
    ): Long {
        val now = System.currentTimeMillis()
        val candidates = collectCandidates(draft, event.type)
        val decision = DedupeEngine.decide(
            DedupeInput(
                amount = draft.amount,
                currency = draft.currency,
                direction = draft.direction,
                institution = draft.institution,
                merchant = draft.merchant,
                reference = draft.reference,
                occurredAt = draft.occurredAt,
                sourceType = event.type,
            ),
            candidates,
        )

        if (decision.candidateId != null &&
            decision.match in setOf(DedupeMatch.STRONG_REFERENCE, DedupeMatch.EXACT_CROSS_SOURCE, DedupeMatch.EXACT_SAME_SOURCE, DedupeMatch.WEAK)
        ) {
            mergeInto(decision, draft, event)
            if (balanceAfter != null && accountId != null) {
                accountDao.insertBalance(
                    AccountBalanceEntity(
                        accountId = accountId,
                        balance = balanceAfter.toPlainString(),
                        currency = draft.currency,
                        recordedAt = draft.occurredAt,
                    ),
                )
            }
            return decision.candidateId
        }

        val needsReview = decision.match == DedupeMatch.AMBIGUOUS ||
            confidence == "LOW" ||
            draft.direction == "TRANSFER"
        val transactionId = transactionDao.insert(
            TransactionEntity(
                amount = draft.amount.stripTrailingZeros().toPlainString(),
                currency = draft.currency,
                direction = draft.direction,
                merchant = draft.merchant,
                category = draft.category,
                accountId = accountId,
                institution = draft.institution,
                occurredAt = draft.occurredAt,
                narration = draft.narration,
                reference = draft.reference,
                status = if (needsReview) "NEEDS_REVIEW" else "CONFIRMED",
                createdAt = now,
                updatedAt = now,
            ),
        )
        if (decision.match == DedupeMatch.AMBIGUOUS) {
            reviewDao.insert(
                ReviewItemEntity(
                    kind = "DUPLICATE_CANDIDATE",
                    transactionId = transactionId,
                    candidateTransactionId = candidates.firstOrNull()?.transactionId,
                    sourceEventId = event.id,
                    reason = decision.reason,
                ),
            )
        }
        if (confidence == "LOW") {
            reviewDao.insert(
                ReviewItemEntity(
                    kind = "LOW_CONFIDENCE",
                    transactionId = transactionId,
                    sourceEventId = event.id,
                    reason = "Parser hanya yakin sebagian",
                ),
            )
        }
        if (balanceAfter != null && accountId != null) {
            accountDao.insertBalance(
                AccountBalanceEntity(
                    accountId = accountId,
                    balance = balanceAfter.toPlainString(),
                    currency = draft.currency,
                    recordedAt = draft.occurredAt,
                ),
            )
        }
        return transactionId
    }

    private suspend fun collectCandidates(draft: RuleDraft, incomingType: String): List<DedupeCandidate> {
        val window = if (incomingType == "EMAIL") 72 * 60 * 60_000L else 30 * 60_000L
        val byAmount = transactionDao.findCandidates(
            amount = draft.amount.stripTrailingZeros().toPlainString(),
            currency = draft.currency,
            direction = draft.direction,
            start = draft.occurredAt - window,
            end = draft.occurredAt + window,
        )
        val byReference = draft.reference?.let { transactionDao.findByReference(it) }.orEmpty()
        val all = (byAmount + byReference).distinctBy { it.id }
        return all.map { transaction ->
            DedupeCandidate(
                transactionId = transaction.id,
                amount = transaction.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                currency = transaction.currency,
                direction = transaction.direction,
                institution = transaction.institution,
                merchant = transaction.merchant,
                reference = transaction.reference,
                occurredAt = transaction.occurredAt,
                sourceTypes = sourceEventDao.getByTransaction(transaction.id).map { it.type }.toSet(),
            )
        }
    }

    private suspend fun mergeInto(decision: DedupeDecision, draft: RuleDraft, event: SourceEventEntity) {
        val target = decision.candidateId?.let { transactionDao.getById(it) } ?: return
        val merged = target.copy(
            merchant = richerText(target.merchant, draft.merchant),
            category = if (target.category == "Lainnya" || target.category.isBlank()) draft.category else target.category,
            institution = target.institution ?: draft.institution,
            narration = listOfNotNull(target.narration, draft.narration).maxByOrNull { it.length },
            reference = target.reference ?: draft.reference,
            occurredAt = minOf(target.occurredAt, draft.occurredAt),
            status = if (target.status == "NEEDS_REVIEW") "CONFIRMED" else target.status,
            updatedAt = System.currentTimeMillis(),
        )
        transactionDao.update(merged)
        sourceEventDao.linkTransaction(event.id, target.id)
    }

    private fun richerText(first: String?, second: String?): String? {
        val a = first?.trim().orEmpty()
        val b = second?.trim().orEmpty()
        return when {
            b.length > a.length -> b
            a.isNotBlank() -> a
            else -> null
        }
    }

    private suspend fun ensureAccount(institution: String?, last4: String?, currency: String): Long? {
        val name = institution?.takeIf { it.isNotBlank() } ?: return null
        accountDao.find(name, last4)?.let { return it.id }
        val id = accountDao.insert(
            AccountEntity(
                institution = name,
                displayName = if (last4 != null) name + " ••" + last4 else name,
                last4 = last4,
                currency = currency,
                kind = "BANK",
            ),
        )
        return id.takeIf { it > 0 } ?: accountDao.find(name, last4)?.id
    }

    suspend fun updateTransaction(transaction: TransactionEntity) {
        transactionDao.update(transaction.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteTransaction(id: Long) {
        transactionDao.deleteById(id)
    }

    suspend fun saveBudget(budget: BudgetEntity): Long =
        if (budget.id == 0L) budgetDao.insert(budget) else {
            budgetDao.update(budget)
            budget.id
        }

    suspend fun deleteBudget(id: Long) = budgetDao.deleteById(id)

    suspend fun saveRule(name: String, conditionsJson: String, actionsJson: String, enabled: Boolean = true): Long =
        ruleDao.insert(
            RuleEntity(
                name = name,
                enabled = enabled,
                conditionsJson = conditionsJson,
                actionsJson = actionsJson,
            ),
        )

    suspend fun updateRule(rule: RuleEntity) = ruleDao.update(rule.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteRule(id: Long) = ruleDao.deleteById(id)

    suspend fun setAllowlistEnabled(packageName: String, enabled: Boolean) =
        allowlistDao.setEnabled(packageName, enabled)

    suspend fun isNotificationPackageAllowed(packageName: String): Boolean =
        allowlistDao.isEnabled(packageName)

    suspend fun addAllowlist(packageName: String, label: String) =
        allowlistDao.upsert(
            AllowlistEntity(
                packageName = packageName,
                label = label,
                enabled = true,
                addedBy = "USER",
            ),
        )

    suspend fun setGmailSenderEnabled(pattern: String, enabled: Boolean) =
        gmailDao.setSenderEnabled(pattern, enabled)

    suspend fun getEnabledGmailSenders(): List<GmailSenderEntity> = gmailDao.getEnabledSenders()

    suspend fun getGmailSync(): GmailSyncEntity? = gmailDao.getSync()

    suspend fun deleteGmailDerivedData() {
        reviewDao.deleteForSourceType("EMAIL")
        val emailOnly = transactionDao.getEmailOnlyTransactions()
        sourceEventDao.deleteByType("EMAIL")
        emailOnly.forEach { transactionDao.deleteById(it.id) }
    }

    suspend fun addGmailSender(pattern: String, label: String) =
        gmailDao.upsertSender(
            GmailSenderEntity(
                pattern = pattern.trim().lowercase(),
                label = label.trim().ifBlank { pattern.trim() },
                enabled = true,
            ),
        )

    suspend fun saveGmailSync(state: GmailSyncEntity) = gmailDao.saveSync(state)

    suspend fun saveAssistantModel(model: AssistantModelEntity) = assistantModelDao.save(model)

    suspend fun resolveReview(itemId: Long, resolution: String) {
        val item = reviewDao.getById(itemId) ?: return
        if (resolution == "MERGE" &&
            item.transactionId != null &&
            item.candidateTransactionId != null &&
            item.transactionId != item.candidateTransactionId
        ) {
            val keep = transactionDao.getById(item.candidateTransactionId)
            val merge = transactionDao.getById(item.transactionId)
            if (keep != null && merge != null) {
                val sources = sourceEventDao.getByTransaction(merge.id)
                sources.forEach { sourceEventDao.linkTransaction(it.id, keep.id) }
                val merged = keep.copy(
                    merchant = richerText(keep.merchant, merge.merchant),
                    category = if (keep.category == "Lainnya") merge.category else keep.category,
                    narration = listOfNotNull(keep.narration, merge.narration).maxByOrNull { it.length },
                    reference = keep.reference ?: merge.reference,
                    occurredAt = minOf(keep.occurredAt, merge.occurredAt),
                    status = "CONFIRMED",
                    updatedAt = System.currentTimeMillis(),
                )
                transactionDao.update(merged)
                transactionDao.deleteById(merge.id)
            }
        }
        reviewDao.resolve(itemId, resolution)
    }

    suspend fun createTransactionFromUnparsed(
        sourceEventId: Long,
        amount: BigDecimal,
        direction: String,
        merchant: String?,
        category: String,
    ): Long {
        val source = sourceEventDao.getById(sourceEventId) ?: error("Sumber tidak ditemukan")
        val now = System.currentTimeMillis()
        val id = transactionDao.insert(
            TransactionEntity(
                amount = amount.stripTrailingZeros().toPlainString(),
                currency = "IDR",
                direction = direction,
                merchant = merchant,
                category = category,
                occurredAt = source.receivedAt,
                narration = source.rawText?.take(400),
                status = "CONFIRMED",
                createdAt = now,
                updatedAt = now,
            ),
        )
        sourceEventDao.markParsed(sourceEventId, "PARSED", "manual-resolution", id, null)
        return id
    }

    suspend fun dismissSource(sourceEventId: Long) {
        sourceEventDao.markIgnored(sourceEventId)
    }

    suspend fun purgeRawText(cutoff: Long) {
        sourceEventDao.clearRawTextOlderThan(cutoff)
    }

    suspend fun deleteAllData() {
        reviewDao.deleteAll()
        sourceEventDao.deleteAll()
        transactionDao.deleteAll()
        budgetDao.deleteAll()
        ruleDao.deleteAll()
        accountDao.deleteAllBalances()
        accountDao.deleteAll()
        allowlistDao.deleteAll()
        gmailDao.deleteAllSenders()
        discoveryDao.deleteAll()
        categoryDao.deleteAll()
        categoryDao.insertAll(DefaultSeed.categories)
        allowlistDao.insertAll(DefaultSeed.allowlist)
        DefaultSeed.gmailSenders.forEach { gmailDao.upsertSender(it) }
    }

    fun reviewCount(): Flow<Int> = reviewDao.observePendingCount()

    fun homeSummary(start: Long, end: Long): Flow<HomeSummary> =
        combine(
            transactionDao.observeBetween(start, end),
            budgetDao.observeAll(),
            reviewDao.observePendingCount(),
        ) { transactions, budgets, reviews ->
            HomeSummary(transactions = transactions, budgets = budgets, reviewCount = reviews)
        }
}

data class HomeSummary(
    val transactions: List<TransactionEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val reviewCount: Int = 0,
)
