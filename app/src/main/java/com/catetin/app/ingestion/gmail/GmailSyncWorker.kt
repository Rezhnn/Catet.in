package com.catetin.app.ingestion.gmail

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.catetin.app.data.db.GmailSenderEntity
import com.catetin.app.data.db.GmailSyncEntity
import com.catetin.app.data.prefs.SettingsRepository
import com.catetin.app.data.repo.FinanceRepository
import com.catetin.app.widget.CatetWidgetProvider
import com.catetin.parser.model.RawArtifact
import com.catetin.parser.model.SourceType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltWorker
class GmailSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: FinanceRepository,
    private val authManager: GmailAuthManager,
    private val api: GmailApiClient,
    private val settingsRepository: SettingsRepository,
    private val scheduler: GmailSyncScheduler,
) : CoroutineWorker(context, params) {

    private val store: GmailSyncStore = object : GmailSyncStore {
        override suspend fun saveState(state: GmailSyncEntity) = repository.saveGmailSync(state)

        override suspend fun enabledSenders(): List<GmailSenderEntity> = repository.getEnabledGmailSenders()

        override suspend fun ingest(artifact: RawArtifact) {
            repository.ingest(artifact)
        }
    }

    override suspend fun doWork(): Result = RUN_MUTEX.withLock { performSync() }

    private suspend fun performSync(): Result {
        val stored = repository.getGmailSync() ?: GmailSyncEntity()
        if (!authManager.isConnected()) {
            if (stored.status != "NOT_CONNECTED") {
                repository.saveGmailSync(stored.markReconnectRequired("Hubungkan ulang Gmail untuk melanjutkan"))
            }
            return Result.success()
        }

        reportProgress("Menghubungkan Gmail…", 0)
        return try {
            var latest = stored
            var token = authManager.freshAccessToken()
            latest = try {
                runEngine(token, latest)
            } catch (reconnect: GmailReconnectRequiredException) {
                // A long backfill can outlive its access token. Refresh once before asking the
                // user to reconnect, and resume from the last state persisted by the engine.
                latest = repository.getGmailSync() ?: latest
                token = authManager.freshAccessToken(forceRefresh = true)
                runEngine(token, latest)
            }
            runCatching { CatetWidgetProvider.refresh(applicationContext) }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (reconnect: GmailReconnectRequiredException) {
            val current = repository.getGmailSync() ?: stored
            repository.saveGmailSync(
                current.markReconnectRequired(reconnect.message ?: "Hubungkan ulang Gmail"),
            )
            Result.failure()
        } catch (t: Throwable) {
            // Preserve the latest page token, anchor and history id saved during pagination.
            val current = repository.getGmailSync() ?: stored
            repository.saveGmailSync(
                current.markSyncError(t.message ?: "Sinkronisasi Gmail gagal"),
            )
            when {
                t is GmailApiException && !t.retryable -> Result.failure()
                runAttemptCount >= 3 -> Result.failure()
                else -> Result.retry()
            }
        }
    }

    private suspend fun runEngine(token: String, state: GmailSyncEntity): GmailSyncEntity {
        val backfillDays = settingsRepository.settings.first().gmailBackfillDays.coerceIn(7, 365)
        return GmailSyncEngine(
            api = api,
            store = store,
            backfillDays = backfillDays,
            onProgress = { processed -> reportProgress("Memproses $processed email…", processed) },
            onContinue = { scheduler.continueBackfill() },
        ).run(token, state)
    }

    private suspend fun reportProgress(message: String, processed: Int) {
        try {
            setProgress(workDataOf("status" to message, "processed" to processed))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // Progress data is useful for diagnostics but must never fail a sync.
        }
    }

    private companion object {
        /** Periodic and one-time workers must never advance the same cursor concurrently. */
        val RUN_MUTEX = Mutex()
    }
}

internal interface GmailSyncStore {
    suspend fun saveState(state: GmailSyncEntity)

    suspend fun enabledSenders(): List<GmailSenderEntity>

    suspend fun ingest(artifact: RawArtifact)
}

/**
 * Resumable Gmail sync state machine. It depends only on [GmailSyncApi] and [GmailSyncStore], so
 * pagination, history expiry and message-race behaviour are covered by JVM unit tests.
 */
internal class GmailSyncEngine(
    private val api: GmailSyncApi,
    private val store: GmailSyncStore,
    private val backfillDays: Int,
    private val now: () -> Long = System::currentTimeMillis,
    private val maxMessagesPerRun: Int = 100,
    private val maxPagesPerRun: Int = 50,
    private val onProgress: suspend (Int) -> Unit = {},
    private val onContinue: () -> Unit = {},
) {
    suspend fun run(accessToken: String, initialState: GmailSyncEntity): GmailSyncEntity {
        val senders = store.enabledSenders()
        if (senders.isEmpty()) {
            return fail(initialState, "Aktifkan minimal satu pengirim Gmail")
        }
        val allowList = GmailSenderAllowList.from(senders)
        if (allowList.isEmpty) {
            return fail(initialState, "Tidak ada pengirim Gmail yang valid")
        }

        val state = initialState.copy(
            status = "SYNCING",
            message = "Menyinkronkan email transaksi…",
        )
        store.saveState(state)
        return when (modeOf(state)) {
            SyncMode.BACKFILL -> syncBackfill(accessToken, state, allowList)
            SyncMode.HISTORY -> syncHistory(accessToken, state, allowList)
        }
    }

    private suspend fun syncBackfill(
        accessToken: String,
        initialState: GmailSyncEntity,
        allowList: GmailSenderAllowList,
    ): GmailSyncEntity {
        var state = initialState
        if (state.backfillAnchorAt == null || state.lastHistoryId.isNullOrBlank()) {
            val profile = api.profile(accessToken)
            val historyId = state.lastHistoryId?.takeIf { it.isNotBlank() }
                ?: profile.historyId.takeIf { it.isNotBlank() }
                ?: return fail(state, "Gmail tidak memberikan historyId untuk melanjutkan")
            val anchor = state.backfillAnchorAt ?: (now() - backfillWindowMillis())
            state = state.copy(
                accountHint = profile.emailAddress.takeIf { it.isNotBlank() } ?: state.accountHint,
                // Capture the watermark before the first page. History from this id replays mail
                // that arrives while a long backfill is still paging, so no gap is possible.
                lastHistoryId = historyId,
                backfillAnchorAt = anchor,
                nextPageToken = if (state.backfillAnchorAt == null) null else state.nextPageToken,
                syncMode = MODE_BACKFILL,
                status = "SYNCING",
                message = "Menyiapkan daftar email…",
            )
            store.saveState(state)
        }

        val anchor = state.backfillAnchorAt ?: return fail(state, "Titik awal backfill tidak tersedia")
        val query = GmailQueryBuilder.backfill(anchor, allowList)
        var pageToken = state.nextPageToken
        var processed = 0
        var pages = 0
        var restartedPageToken = false
        val seenPageTokens = mutableSetOf<String>().apply { pageToken?.let(::add) }

        while (true) {
            if (pages >= maxPagesPerRun) {
                onContinue()
                return state
            }
            pages++
            val page = try {
                api.listMessages(accessToken, query, pageToken, maxResults = 25)
            } catch (apiError: GmailApiException) {
                if (!restartedPageToken && pageToken != null && apiError.statusCode in PAGE_TOKEN_ERRORS) {
                    restartedPageToken = true
                    pageToken = null
                    seenPageTokens.clear()
                    state = state.copy(
                        nextPageToken = null,
                        syncMode = MODE_BACKFILL,
                        status = "SYNCING",
                        message = "Memulai ulang daftar email…",
                    )
                    store.saveState(state)
                    pages--
                    continue
                }
                throw apiError
            }

            val nextPageToken = page.nextPageToken?.takeIf { it.isNotBlank() }
            if (nextPageToken != null && (page.messages.isEmpty() || nextPageToken in seenPageTokens)) {
                return paginationFailure(state, "Gmail mengembalikan halaman berulang")
            }
            nextPageToken?.let(seenPageTokens::add)
            page.messages.forEach { ref -> processMessage(accessToken, ref.id, allowList) }
            processed += page.messages.size
            pageToken = nextPageToken
            state = state.copy(
                nextPageToken = pageToken,
                syncMode = MODE_BACKFILL,
                status = "SYNCING",
                message = "Memproses $processed email…",
            )
            store.saveState(state)
            onProgress(processed)

            if (pageToken == null) break
            if (processed >= maxMessagesPerRun) {
                onContinue()
                return state
            }
        }

        val complete = state.copy(
            status = "OK",
            message = "Sinkronisasi selesai",
            lastSyncAt = now(),
            nextPageToken = null,
            syncMode = null,
            backfillAnchorAt = null,
        )
        store.saveState(complete)
        return complete
    }

    private suspend fun syncHistory(
        accessToken: String,
        initialState: GmailSyncEntity,
        allowList: GmailSenderAllowList,
    ): GmailSyncEntity {
        val startHistoryId = initialState.lastHistoryId?.takeIf { it.isNotBlank() }
            ?: return syncBackfill(
                accessToken,
                initialState.copy(lastHistoryId = null, syncMode = MODE_BACKFILL, nextPageToken = null),
                allowList,
            )
        var state = initialState.copy(
            syncMode = MODE_HISTORY,
            status = "SYNCING",
            message = "Memeriksa email baru…",
        )
        store.saveState(state)
        var pageToken = state.nextPageToken
        var newestHistoryId = startHistoryId
        var processed = 0
        var pages = 0
        val seenMessageIds = mutableSetOf<String>()
        val seenPageTokens = mutableSetOf<String>().apply { pageToken?.let(::add) }

        try {
            while (true) {
                if (pages >= maxPagesPerRun) {
                    onContinue()
                    return state
                }
                pages++
                val page = api.history(accessToken, startHistoryId, pageToken, maxResults = 100)
                newestHistoryId = maxHistoryId(newestHistoryId, page.historyId)
                page.history.forEach { record ->
                    newestHistoryId = maxHistoryId(newestHistoryId, record.id)
                    record.messagesAdded.forEach { added ->
                        val id = added.message.id
                        if (id.isNotBlank() && seenMessageIds.add(id)) {
                            processMessage(accessToken, id, allowList)
                            processed++
                        }
                    }
                }

                val nextPageToken = page.nextPageToken?.takeIf { it.isNotBlank() }
                if (nextPageToken != null && !seenPageTokens.add(nextPageToken)) {
                    return paginationFailure(state, "Gmail mengembalikan halaman riwayat berulang")
                }
                pageToken = nextPageToken
                state = state.copy(
                    nextPageToken = pageToken,
                    syncMode = MODE_HISTORY,
                    status = "SYNCING",
                    message = "Memproses $processed email…",
                )
                store.saveState(state)
                onProgress(processed)

                if (pageToken == null) break
                if (processed >= maxMessagesPerRun) {
                    onContinue()
                    return state
                }
            }
        } catch (notFound: GmailApiException) {
            if (notFound.statusCode !in HISTORY_EXPIRED_ERRORS) throw notFound
            return fallbackFromExpiredHistory(accessToken, state, allowList)
        }

        val complete = state.copy(
            lastHistoryId = newestHistoryId,
            lastSyncAt = now(),
            nextPageToken = null,
            syncMode = null,
            backfillAnchorAt = null,
            status = "OK",
            message = "Sinkronisasi selesai",
        )
        store.saveState(complete)
        return complete
    }

    private suspend fun fallbackFromExpiredHistory(
        accessToken: String,
        state: GmailSyncEntity,
        allowList: GmailSenderAllowList,
    ): GmailSyncEntity {
        val profile = api.profile(accessToken)
        val accountChanged = !state.accountHint.isNullOrBlank() &&
            profile.emailAddress.isNotBlank() &&
            !state.accountHint.equals(profile.emailAddress, ignoreCase = true)
        val anchor = if (accountChanged) {
            now() - backfillWindowMillis()
        } else {
            (state.lastSyncAt ?: now()) - 2L * 24L * 60L * 60L * 1_000L
        }
        val historyId = profile.historyId.takeIf { it.isNotBlank() }
            ?: return fail(state, "Gmail tidak memberikan historyId baru")
        val fallback = state.copy(
            accountHint = profile.emailAddress.takeIf { it.isNotBlank() } ?: state.accountHint,
            // This watermark is captured before the replacement listing, so history will replay
            // messages that arrive while that listing runs.
            lastHistoryId = historyId,
            backfillAnchorAt = anchor,
            nextPageToken = null,
            syncMode = MODE_BACKFILL,
            status = "SYNCING",
            message = "Riwayat Gmail kedaluwarsa; memuat ulang email terbaru…",
        )
        store.saveState(fallback)
        return syncBackfill(accessToken, fallback, allowList)
    }

    private suspend fun processMessage(
        accessToken: String,
        messageId: String,
        allowList: GmailSenderAllowList,
    ): Boolean {
        val raw = try {
            api.message(accessToken, messageId)
        } catch (gone: GmailApiException) {
            // The message was deleted or moved between messages.list/history.list and get.
            if (gone.statusCode in GONE_MESSAGE_ERRORS) return false
            throw gone
        }
        val parsed = GmailMimeParser.toParsed(raw) ?: return false
        if (!allowList.allows(parsed.senderAddress)) return false
        store.ingest(
            RawArtifact(
                sourceType = SourceType.EMAIL,
                sourceApp = parsed.senderAddress.ifBlank { parsed.sender },
                text = parsed.text,
                timestampMillis = parsed.receivedAt,
                title = parsed.subject.ifBlank { parsed.sender },
                externalId = "gmail:${parsed.messageId}",
                subject = parsed.subject,
            ),
        )
        return true
    }

    private suspend fun fail(state: GmailSyncEntity, message: String): GmailSyncEntity {
        val failed = state.copy(status = "ERROR", message = message, nextPageToken = null)
        store.saveState(failed)
        return failed
    }

    private suspend fun paginationFailure(state: GmailSyncEntity, message: String): Nothing {
        store.saveState(state.copy(status = "ERROR", message = message, nextPageToken = null))
        throw GmailApiException(message, 409, retryable = false)
    }

    private fun modeOf(state: GmailSyncEntity): SyncMode = when {
        state.backfillAnchorAt != null -> SyncMode.BACKFILL
        state.syncMode == MODE_BACKFILL -> SyncMode.BACKFILL
        !state.lastHistoryId.isNullOrBlank() -> SyncMode.HISTORY
        else -> SyncMode.BACKFILL
    }

    private fun backfillWindowMillis(): Long =
        backfillDays.coerceIn(7, 365).toLong() * 24L * 60L * 60L * 1_000L

    private enum class SyncMode { BACKFILL, HISTORY }

    private companion object {
        const val MODE_BACKFILL = "BACKFILL"
        const val MODE_HISTORY = "HISTORY"
        val GONE_MESSAGE_ERRORS = setOf(404, 410)
        val PAGE_TOKEN_ERRORS = setOf(400, 404, 410)
        val HISTORY_EXPIRED_ERRORS = setOf(400, 404, 410)
    }
}

internal object GmailQueryBuilder {
    fun backfill(anchorAt: Long, allowList: GmailSenderAllowList): String {
        val clauses = allowList.queryTerms().map { "from:$it" }
        require(clauses.isNotEmpty()) { "Tidak ada pengirim Gmail yang valid" }
        val senderQuery = if (clauses.size == 1) clauses.first() else "(" + clauses.joinToString(" OR ") + ")"
        val formatter = SimpleDateFormat("yyyy/MM/dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return "after:${formatter.format(Date(anchorAt))} $senderQuery -in:spam -in:trash"
    }
}

private fun maxHistoryId(current: String, candidate: String?): String {
    if (candidate.isNullOrBlank()) return current
    val currentNumber = current.toBigIntegerOrNull()
    val candidateNumber = candidate.toBigIntegerOrNull()
    if (candidateNumber == null) return current
    if (currentNumber == null) return candidate
    return if (candidateNumber > currentNumber) candidate else current
}

private fun String.toBigIntegerOrNull(): BigInteger? =
    runCatching { BigInteger(this) }.getOrNull()

private fun workDataOf(vararg pairs: Pair<String, Any>): Data =
    Data.Builder().apply { pairs.forEach { putString(it.first, it.second.toString()) } }.build()
