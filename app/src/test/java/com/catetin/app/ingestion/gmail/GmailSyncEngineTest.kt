package com.catetin.app.ingestion.gmail

import com.catetin.app.data.db.GmailSenderEntity
import com.catetin.app.data.db.GmailSyncEntity
import com.catetin.parser.model.RawArtifact
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GmailSyncEngineTest {

    @Test
    fun `first backfill captures history watermark before paging and keeps it on completion`() = runTest {
        val api = FakeApi(GmailProfile(emailAddress = "me@example.com", historyId = "500"))
        api.messages["m1"] = message("m1")
        api.listPages += page("m1")
        val store = FakeStore()

        val result = engine(api, store).run("token", GmailSyncEntity(status = "SYNCING"))

        assertEquals("500", result.lastHistoryId)
        assertNull(result.backfillAnchorAt)
        assertEquals("OK", result.status)
        assertEquals(1, api.profileCalls)
        assertTrue(
            store.saved.any {
                it.lastHistoryId == "500" && it.backfillAnchorAt != null && it.syncMode == "BACKFILL"
            },
        )
        assertEquals("gmail:m1", store.artifacts.single().externalId)
    }

    @Test
    fun `a cleared sync mode still resumes backfill when the anchor remains`() = runTest {
        val api = FakeApi()
        api.messages["m1"] = message("m1")
        api.listPages += page("m1")
        val state = backfillState().copy(syncMode = null, nextPageToken = "p2")

        val result = engine(api, FakeStore()).run("token", state)

        assertEquals(0, api.profileCalls)
        assertEquals(0, api.historyCalls)
        assertEquals(listOf("p2"), api.listPageTokens)
        assertEquals("500", result.lastHistoryId)
        assertNull(result.backfillAnchorAt)
    }

    @Test
    fun `expired history falls back to a bounded relist with a fresh watermark`() = runTest {
        val api = FakeApi(GmailProfile(emailAddress = "me@example.com", historyId = "900"))
        api.historyFailures += GmailApiException("history terlalu lama", 404)
        api.messages["m1"] = message("m1")
        api.listPages += page("m1")
        val state = GmailSyncEntity(
            lastHistoryId = "200",
            lastSyncAt = NOW - 5 * DAY,
            accountHint = "me@example.com",
            status = "OK",
        )

        val result = engine(api, FakeStore()).run("token", state)

        assertEquals(1, api.historyCalls)
        assertEquals(1, api.profileCalls)
        assertEquals("900", result.lastHistoryId)
        assertNull(result.backfillAnchorAt)
        assertEquals("OK", result.status)
        val query = requireNotNull(api.listQueries.single())
        assertTrue(query.contains("after:${queryDate(NOW - 7 * DAY)}"))
        assertTrue(query.contains("from:no-reply@bca.co.id"))
    }

    @Test
    fun `message deleted between listing and fetch is skipped`() = runTest {
        val api = FakeApi()
        api.missing += "gone"
        api.messages["good"] = message("good")
        api.listPages += page("gone", "good")
        val store = FakeStore()

        val result = engine(api, store).run("token", backfillState())

        assertEquals("OK", result.status)
        assertEquals(1, store.artifacts.size)
        assertEquals("gmail:good", store.artifacts.single().externalId)
    }

    @Test
    fun `backfill stops at the message budget and persists the next page token`() = runTest {
        val api = FakeApi()
        val ids = (1..25).map { "m$it" }
        ids.forEach { api.messages[it] = message(it) }
        api.listPages += page(*ids.toTypedArray(), next = "p2")
        var continuations = 0
        val store = FakeStore()

        val result = engine(api, store, maxMessages = 25, onContinue = { continuations++ })
            .run("token", backfillState())

        assertEquals(1, continuations)
        assertEquals("p2", result.nextPageToken)
        assertEquals("SYNCING", result.status)
        assertEquals("BACKFILL", result.syncMode)
        assertNotNull(result.backfillAnchorAt)
        assertEquals(1, api.listCalls)
        assertEquals(25, store.artifacts.size)
    }

    @Test
    fun `history pagination keeps the old watermark until the last page`() = runTest {
        val api = FakeApi()
        val ids = listOf("m1", "m2", "m3")
        ids.forEach { api.messages[it] = message(it) }
        api.historyPages += historyPage(ids, next = "h2", historyId = "150")
        var continuations = 0

        val result = engine(api, FakeStore(), maxMessages = 2, onContinue = { continuations++ })
            .run(
                "token",
                GmailSyncEntity(lastHistoryId = "100", lastSyncAt = NOW - DAY, status = "OK"),
            )

        assertEquals(1, continuations)
        assertEquals("h2", result.nextPageToken)
        assertEquals("100", result.lastHistoryId)
        assertEquals("HISTORY", result.syncMode)
        assertEquals("SYNCING", result.status)
        assertEquals(listOf("100"), api.historyStarts)
    }

    @Test
    fun `completed history advances to the newest history id`() = runTest {
        val api = FakeApi()
        api.messages["m1"] = message("m1")
        api.historyPages += GmailHistoryList(
            history = listOf(
                GmailHistoryRecord(
                    id = "240",
                    messagesAdded = listOf(GmailMessageAdded(GmailMessageRef("m1"))),
                ),
            ),
            historyId = "250",
        )

        val result = engine(api, FakeStore()).run(
            "token",
            GmailSyncEntity(lastHistoryId = "100", lastSyncAt = NOW - DAY, status = "OK"),
        )

        assertEquals("250", result.lastHistoryId)
        assertEquals(NOW, result.lastSyncAt)
        assertEquals("OK", result.status)
        assertNull(result.syncMode)
    }

    @Test
    fun `repeated page token resets the cursor and fails visibly`() = runTest {
        val api = FakeApi()
        api.messages["m1"] = message("m1")
        api.messages["m2"] = message("m2")
        api.listPages += page("m1", next = "p2")
        api.listPages += page("m2", next = "p2")
        val store = FakeStore()

        val failure = runCatching { engine(api, store).run("token", backfillState()) }.exceptionOrNull()

        assertTrue(failure is GmailApiException)
        assertEquals(409, (failure as GmailApiException).statusCode)
        assertNull(store.latest?.nextPageToken)
        assertEquals("ERROR", store.latest?.status)
        assertEquals(1, store.artifacts.size)
    }

    @Test
    fun `stale page token restarts the listing once`() = runTest {
        val api = FakeApi()
        api.listFailures += GmailApiException("page token tidak valid", 400)
        api.messages["m1"] = message("m1")
        api.listPages += page("m1")

        val result = engine(api, FakeStore()).run(
            "token",
            backfillState().copy(nextPageToken = "stale"),
        )

        assertEquals("OK", result.status)
        assertEquals(listOf("stale", null), api.listPageTokens)
        assertEquals(2, api.listCalls)
    }

    @Test
    fun `sender allow list uses the address and never the display label`() {
        val allowList = GmailSenderAllowList.from(
            listOf(
                GmailSenderEntity("no-reply@bca.co.id", "BCA"),
                GmailSenderEntity("bankmandiri.co.id", "Mandiri"),
                GmailSenderEntity("receipts", "Receipts"),
            ),
        )

        assertTrue(allowList.allows("No-Reply@BCA.co.id"))
        assertTrue(allowList.allows("notif@mail.bankmandiri.co.id"))
        assertTrue(allowList.allows("receipts@shopee.co.id"))
        assertFalse(allowList.allows("no-reply@evilbca.co.id"))
        assertFalse(allowList.allows("attacker@example.com"))
        assertFalse(allowList.allows("notreceipts@shopee.co.id"))
    }

    @Test
    fun `query includes every enabled sender instead of truncating at twenty`() {
        val senders = (1..25).map { GmailSenderEntity("bank$it.example.com", "Bank $it") }
        val query = GmailQueryBuilder.backfill(NOW, GmailSenderAllowList.from(senders))

        (1..25).forEach { assertTrue(query.contains("from:bank$it.example.com")) }
        assertTrue(query.contains("after:${queryDate(NOW)}"))
        assertTrue(query.contains("-in:spam -in:trash"))
    }

    @Test
    fun `state helpers preserve progress and only clear an unusable page token`() {
        val state = GmailSyncEntity(
            lastHistoryId = "10",
            backfillAnchorAt = 42,
            nextPageToken = "page-2",
            syncMode = "BACKFILL",
        )

        val error = state.markSyncError("gagal")
        assertEquals("ERROR", error.status)
        assertEquals("page-2", error.nextPageToken)
        assertEquals("10", error.lastHistoryId)

        val reconnect = state.markReconnectRequired("hubungkan ulang")
        assertEquals("RECONNECT_NEEDED", reconnect.status)
        assertNull(reconnect.nextPageToken)
        assertEquals("10", reconnect.lastHistoryId)
        assertEquals(42L, reconnect.backfillAnchorAt)
        assertEquals("BACKFILL", reconnect.syncMode)
    }

    @Test
    fun `empty sender list stops before any network call`() = runTest {
        val api = FakeApi()
        val store = FakeStore(senders = emptyList())

        val result = engine(api, store).run("token", GmailSyncEntity())

        assertEquals("ERROR", result.status)
        assertEquals(0, api.profileCalls)
        assertEquals(0, api.listCalls)
    }

    private fun engine(
        api: FakeApi,
        store: FakeStore,
        maxMessages: Int = 100,
        onContinue: () -> Unit = {},
    ) = GmailSyncEngine(
        api = api,
        store = store,
        backfillDays = 30,
        now = { NOW },
        maxMessagesPerRun = maxMessages,
        onContinue = onContinue,
    )

    private fun backfillState() = GmailSyncEntity(
        lastHistoryId = "500",
        backfillAnchorAt = NOW - 30 * DAY,
        syncMode = "BACKFILL",
        status = "SYNCING",
    )

    private fun page(vararg ids: String, next: String? = null) = GmailMessageList(
        messages = ids.map { GmailMessageRef(it) },
        nextPageToken = next,
    )

    private fun historyPage(
        ids: List<String>,
        next: String? = null,
        historyId: String? = null,
    ) = GmailHistoryList(
        history = listOf(
            GmailHistoryRecord(
                id = historyId.orEmpty(),
                messagesAdded = ids.map { GmailMessageAdded(GmailMessageRef(it)) },
            ),
        ),
        nextPageToken = next,
        historyId = historyId,
    )

    private fun message(
        id: String,
        from: String = "BCA <no-reply@bca.co.id>",
        body: String = "Transaksi Rp25.000",
    ) = GmailMessage(
        id = id,
        internalDate = NOW.toString(),
        payload = GmailPart(
            mimeType = "text/plain",
            headers = listOf(
                GmailHeader("From", from),
                GmailHeader("Subject", "Transaksi"),
            ),
            body = GmailBody(
                data = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(body.toByteArray(Charsets.UTF_8)),
            ),
        ),
    )

    private fun queryDate(millis: Long): String = SimpleDateFormat("yyyy/MM/dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(millis))

    private class FakeStore(
        var senders: List<GmailSenderEntity> = listOf(
            GmailSenderEntity("no-reply@bca.co.id", "BCA"),
        ),
    ) : GmailSyncStore {
        val saved = mutableListOf<GmailSyncEntity>()
        val artifacts = mutableListOf<RawArtifact>()
        var latest: GmailSyncEntity? = null

        override suspend fun saveState(state: GmailSyncEntity) {
            latest = state
            saved += state
        }

        override suspend fun enabledSenders(): List<GmailSenderEntity> = senders

        override suspend fun ingest(artifact: RawArtifact) {
            artifacts += artifact
        }
    }

    private class FakeApi(
        var profileResponse: GmailProfile = GmailProfile(
            emailAddress = "me@example.com",
            historyId = "100",
        ),
    ) : GmailSyncApi {
        var profileCalls = 0
        var listCalls = 0
        var historyCalls = 0
        val listPages = mutableListOf<GmailMessageList>()
        val listFailures = mutableListOf<GmailApiException>()
        val historyPages = mutableListOf<GmailHistoryList>()
        val historyFailures = mutableListOf<GmailApiException>()
        val listQueries = mutableListOf<String?>()
        val listPageTokens = mutableListOf<String?>()
        val historyStarts = mutableListOf<String>()
        val messages = mutableMapOf<String, GmailMessage>()
        val missing = mutableSetOf<String>()

        override suspend fun profile(accessToken: String): GmailProfile {
            profileCalls++
            return profileResponse
        }

        override suspend fun listMessages(
            accessToken: String,
            query: String?,
            pageToken: String?,
            maxResults: Int,
        ): GmailMessageList {
            listCalls++
            listQueries += query
            listPageTokens += pageToken
            listFailures.removeFirstOrNull()?.let { throw it }
            return listPages.removeFirstOrNull() ?: error("Tidak ada halaman daftar email")
        }

        override suspend fun message(accessToken: String, messageId: String): GmailMessage {
            if (messageId in missing) throw GmailApiException("email dihapus", 404)
            return messages[messageId] ?: throw GmailApiException("email tidak ditemukan", 404)
        }

        override suspend fun history(
            accessToken: String,
            startHistoryId: String,
            pageToken: String?,
            maxResults: Int,
        ): GmailHistoryList {
            historyCalls++
            historyStarts += startHistoryId
            historyFailures.removeFirstOrNull()?.let { throw it }
            return historyPages.removeFirstOrNull() ?: error("Tidak ada halaman riwayat")
        }
    }

    private companion object {
        const val DAY = 24L * 60L * 60L * 1_000L
        const val NOW = 1_700_000_000_000L
    }
}
