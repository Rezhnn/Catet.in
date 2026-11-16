package com.catetin.app.data.db

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

data class SourceStat(
    val sourceApp: String,
    val receivedAt: Long,
    val count: Int,
)

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY occurred_at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE occurred_at BETWEEN :start AND :end ORDER BY occurred_at DESC")
    fun observeBetween(start: Long, end: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE occurred_at BETWEEN :start AND :end ORDER BY occurred_at DESC")
    suspend fun getBetween(start: Long, end: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeById(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query(
        """
        SELECT * FROM transactions
        WHERE occurred_at BETWEEN :start AND :end
          AND (:type IS NULL OR direction = :type)
          AND (:category IS NULL OR category = :category)
          AND (:query IS NULL OR :query = '' OR merchant LIKE '%' || :query || '%' OR narration LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%')
        ORDER BY occurred_at DESC
        LIMIT :limit
        """,
    )
    fun search(
        query: String?,
        type: String?,
        category: String?,
        start: Long,
        end: Long,
        limit: Int = 300,
    ): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE amount = :amount AND currency = :currency AND direction = :direction
          AND occurred_at BETWEEN :start AND :end
        ORDER BY occurred_at ASC
        """,
    )
    suspend fun findCandidates(
        amount: String,
        currency: String,
        direction: String,
        start: Long,
        end: Long,
    ): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE reference = :reference AND reference IS NOT NULL LIMIT 5")
    suspend fun findByReference(reference: String): List<TransactionEntity>

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("SELECT * FROM transactions ORDER BY occurred_at ASC")
    suspend fun getAll(): List<TransactionEntity>

    @Query(
        """
        SELECT DISTINCT t.* FROM transactions t
        INNER JOIN source_events e ON e.transaction_id = t.id
        WHERE e.type = 'EMAIL'
          AND NOT EXISTS (
              SELECT 1 FROM source_events other
              WHERE other.transaction_id = t.id AND other.type <> 'EMAIL'
          )
        """,
    )
    suspend fun getEmailOnlyTransactions(): List<TransactionEntity>
}

@Dao
interface ManualEntryDao {
    @Insert
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSourceEvent(event: SourceEventEntity): Long

    /** The ledger row and its audit source are committed together or not at all. */
    @Transaction
    suspend fun insertManual(transaction: TransactionEntity, sourceEvent: SourceEventEntity): Long {
        val transactionId = insertTransaction(transaction)
        insertSourceEvent(sourceEvent.copy(transactionId = transactionId))
        return transactionId
    }
}

@Dao
interface SourceEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(event: SourceEventEntity): Long

    @Query("SELECT * FROM source_events WHERE id = :id")
    suspend fun getById(id: Long): SourceEventEntity?

    @Query("SELECT * FROM source_events WHERE type = :type AND external_id = :externalId LIMIT 1")
    suspend fun getByExternal(type: String, externalId: String): SourceEventEntity?

    @Query("SELECT * FROM source_events WHERE transaction_id = :transactionId ORDER BY received_at ASC")
    fun observeByTransaction(transactionId: Long): Flow<List<SourceEventEntity>>

    @Query("SELECT * FROM source_events WHERE transaction_id = :transactionId ORDER BY received_at ASC")
    suspend fun getByTransaction(transactionId: Long): List<SourceEventEntity>

    @Query("SELECT * FROM source_events WHERE parse_status = 'UNPARSED' ORDER BY received_at DESC LIMIT 100")
    fun observeUnparsed(): Flow<List<SourceEventEntity>>

    @Query("SELECT COUNT(*) FROM source_events WHERE parse_status = :status")
    fun observeCount(status: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM source_events WHERE parse_status = :status")
    suspend fun count(status: String): Int

    @Query("SELECT * FROM source_events ORDER BY received_at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 30): Flow<List<SourceEventEntity>>

    @Query("SELECT * FROM source_events ORDER BY received_at ASC")
    suspend fun getAll(): List<SourceEventEntity>

    @Query(
        """
        SELECT source_app AS sourceApp, MAX(received_at) AS receivedAt, COUNT(*) AS count
        FROM source_events
        WHERE type = 'NOTIFICATION'
        GROUP BY source_app
        """,
    )
    fun observeNotificationStats(): Flow<List<SourceStat>>

    @Query(
        """
        UPDATE source_events
        SET parse_status = :status, parser_id = :parserId, transaction_id = :transactionId, institution = :institution
        WHERE id = :id
        """,
    )
    suspend fun markParsed(
        id: Long,
        status: String,
        parserId: String?,
        transactionId: Long?,
        institution: String?,
    )

    @Query("UPDATE source_events SET transaction_id = :transactionId WHERE id = :id")
    suspend fun linkTransaction(id: Long, transactionId: Long?)

    @Query("UPDATE source_events SET parse_status = 'IGNORED' WHERE id = :id")
    suspend fun markIgnored(id: Long)

    @Query("DELETE FROM source_events WHERE received_at < :cutoff AND parse_status IN ('PARSED','IGNORED')")
    suspend fun purgeOld(cutoff: Long)

    @Query("UPDATE source_events SET raw_text = NULL WHERE received_at < :cutoff AND raw_text IS NOT NULL")
    suspend fun clearRawTextOlderThan(cutoff: Long)

    @Query("DELETE FROM source_events WHERE type = :type")
    suspend fun deleteByType(type: String)

    @Query("DELETE FROM source_events")
    suspend fun deleteAll()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sort_order ASC, name ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sort_order ASC, name ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY display_name ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY display_name ASC")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE institution = :institution AND (:last4 IS NULL OR last4 = :last4) LIMIT 1")
    suspend fun find(institution: String, last4: String?): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Insert
    suspend fun insertBalance(balance: AccountBalanceEntity): Long

    @Query("SELECT * FROM account_balances WHERE account_id = :accountId ORDER BY recorded_at DESC LIMIT 1")
    fun observeLatestBalance(accountId: Long): Flow<AccountBalanceEntity?>

    @Query("SELECT * FROM account_balances ORDER BY recorded_at DESC")
    suspend fun getAllBalances(): List<AccountBalanceEntity>

    @Query("DELETE FROM account_balances")
    suspend fun deleteAllBalances()

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY created_at ASC")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets ORDER BY created_at ASC")
    suspend fun getAll(): List<BudgetEntity>

    @Insert
    suspend fun insert(budget: BudgetEntity): Long

    @Update
    suspend fun update(budget: BudgetEntity)

    @Delete
    suspend fun delete(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules ORDER BY created_at DESC")
    fun observeAll(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules WHERE enabled = 1 ORDER BY created_at ASC")
    suspend fun getEnabled(): List<RuleEntity>

    @Query("SELECT * FROM rules ORDER BY created_at ASC")
    suspend fun getAll(): List<RuleEntity>

    @Insert
    suspend fun insert(rule: RuleEntity): Long

    @Update
    suspend fun update(rule: RuleEntity)

    @Delete
    suspend fun delete(rule: RuleEntity)

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM rules")
    suspend fun deleteAll()
}

@Dao
interface AllowlistDao {
    @Query("SELECT * FROM notification_allowlist ORDER BY label ASC")
    fun observeAll(): Flow<List<AllowlistEntity>>

    @Query("SELECT * FROM notification_allowlist WHERE enabled = 1")
    suspend fun getEnabled(): List<AllowlistEntity>

    @Query("SELECT * FROM notification_allowlist ORDER BY label ASC")
    suspend fun getAll(): List<AllowlistEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM notification_allowlist WHERE package_name = :packageName AND enabled = 1)")
    suspend fun isEnabled(packageName: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<AllowlistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: AllowlistEntity)

    @Query("UPDATE notification_allowlist SET enabled = :enabled WHERE package_name = :packageName")
    suspend fun setEnabled(packageName: String, enabled: Boolean)

    @Query("SELECT COUNT(*) FROM notification_allowlist")
    suspend fun count(): Int

    @Query("DELETE FROM notification_allowlist")
    suspend fun deleteAll()
}

@Dao
interface GmailDao {
    @Query("SELECT * FROM gmail_senders ORDER BY label ASC")
    fun observeSenders(): Flow<List<GmailSenderEntity>>

    @Query("SELECT * FROM gmail_senders WHERE enabled = 1")
    suspend fun getEnabledSenders(): List<GmailSenderEntity>

    @Query("SELECT * FROM gmail_senders ORDER BY label ASC")
    suspend fun getAllSenders(): List<GmailSenderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSender(sender: GmailSenderEntity)

    @Query("UPDATE gmail_senders SET enabled = :enabled WHERE pattern = :pattern")
    suspend fun setSenderEnabled(pattern: String, enabled: Boolean)

    @Query("SELECT * FROM gmail_sync_state WHERE id = 1")
    fun observeSync(): Flow<GmailSyncEntity?>

    @Query("SELECT * FROM gmail_sync_state WHERE id = 1")
    suspend fun getSync(): GmailSyncEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSync(state: GmailSyncEntity)

    @Query("DELETE FROM gmail_senders")
    suspend fun deleteAllSenders()
}

@Dao
interface ReviewDao {
    @Query("SELECT * FROM review_items WHERE resolved_at IS NULL ORDER BY created_at DESC")
    fun observePending(): Flow<List<ReviewItemEntity>>

    @Query("SELECT COUNT(*) FROM review_items WHERE resolved_at IS NULL")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM review_items WHERE id = :id")
    suspend fun getById(id: Long): ReviewItemEntity?

    @Insert
    suspend fun insert(item: ReviewItemEntity): Long

    @Query("UPDATE review_items SET resolved_at = :resolvedAt, resolution = :resolution WHERE id = :id")
    suspend fun resolve(id: Long, resolution: String, resolvedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM review_items")
    suspend fun deleteAll()

    @Query("SELECT * FROM review_items ORDER BY created_at ASC")
    suspend fun getAll(): List<ReviewItemEntity>

    @Query(
        """
        DELETE FROM review_items
        WHERE source_event_id IN (SELECT id FROM source_events WHERE type = :sourceType)
        """,
    )
    suspend fun deleteForSourceType(sourceType: String)
}

@Dao
interface AssistantModelDao {
    @Query("SELECT * FROM assistant_model WHERE id = 1")
    fun observe(): Flow<AssistantModelEntity?>

    @Query("SELECT * FROM assistant_model WHERE id = 1")
    suspend fun get(): AssistantModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(model: AssistantModelEntity)
}

@Dao
interface DiscoveryDao {
    @Query("SELECT * FROM notification_discovery ORDER BY last_seen DESC")
    fun observeAll(): Flow<List<DiscoveryEntity>>

    @Query("SELECT * FROM notification_discovery WHERE package_name = :packageName")
    suspend fun get(packageName: String): DiscoveryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: DiscoveryEntity)

    @Query("DELETE FROM notification_discovery")
    suspend fun deleteAll()
}
