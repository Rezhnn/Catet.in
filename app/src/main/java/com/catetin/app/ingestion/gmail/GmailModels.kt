package com.catetin.app.ingestion.gmail

import com.catetin.app.data.db.GmailSenderEntity
import com.catetin.app.data.db.GmailSyncEntity
import java.util.Locale
import kotlinx.serialization.Serializable

/** The only OAuth scope Catet.in may request for Gmail. */
const val GMAIL_READONLY_SCOPE: String = "https://www.googleapis.com/auth/gmail.readonly"

@Serializable
data class GmailProfile(
    val emailAddress: String = "",
    val messagesTotal: Int = 0,
    val threadsTotal: Int = 0,
    val historyId: String = "",
)

@Serializable
data class GmailMessageRef(
    val id: String,
    val threadId: String? = null,
)

@Serializable
data class GmailMessageList(
    val messages: List<GmailMessageRef> = emptyList(),
    val nextPageToken: String? = null,
    val resultSizeEstimate: Int = 0,
)

@Serializable
data class GmailHeader(
    val name: String = "",
    val value: String = "",
)

@Serializable
data class GmailBody(
    val attachmentId: String? = null,
    val size: Int = 0,
    val data: String? = null,
)

@Serializable
data class GmailPart(
    val partId: String = "",
    val mimeType: String = "",
    val filename: String? = null,
    val headers: List<GmailHeader> = emptyList(),
    val body: GmailBody? = null,
    val parts: List<GmailPart> = emptyList(),
)

@Serializable
data class GmailMessage(
    val id: String,
    val threadId: String? = null,
    val historyId: String? = null,
    val internalDate: String? = null,
    val snippet: String = "",
    val payload: GmailPart? = null,
)

@Serializable
data class GmailMessageAdded(
    val message: GmailMessageRef,
)

@Serializable
data class GmailHistoryRecord(
    val id: String = "",
    val messagesAdded: List<GmailMessageAdded> = emptyList(),
)

@Serializable
data class GmailHistoryList(
    val history: List<GmailHistoryRecord> = emptyList(),
    val nextPageToken: String? = null,
    val historyId: String? = null,
)

data class ParsedGmailMessage(
    val messageId: String,
    val threadId: String?,
    val sender: String,
    val senderAddress: String,
    val subject: String,
    val receivedAt: Long,
    val text: String,
)

/** Narrow API surface used by the sync state machine so its behaviour can be unit tested. */
interface GmailSyncApi {
    suspend fun profile(accessToken: String): GmailProfile

    suspend fun listMessages(
        accessToken: String,
        query: String?,
        pageToken: String?,
        maxResults: Int = 25,
    ): GmailMessageList

    suspend fun message(accessToken: String, messageId: String): GmailMessage

    suspend fun history(
        accessToken: String,
        startHistoryId: String,
        pageToken: String?,
        maxResults: Int = 100,
    ): GmailHistoryList
}

class GmailReconnectRequiredException(message: String, cause: Throwable? = null) : Exception(message, cause)

class GmailApiException(
    message: String,
    val statusCode: Int,
    cause: Throwable? = null,
    val retryable: Boolean = statusCode == 429 || statusCode in 500..599,
) : Exception(message, cause)

/** A token-refresh failure that is safe to retry without asking the user to reconnect. */
class GmailTransientException(message: String, cause: Throwable? = null) : Exception(message, cause)

internal fun GmailSyncEntity.markSyncError(message: String): GmailSyncEntity = copy(
    status = "ERROR",
    message = message.take(240),
)

internal fun GmailSyncEntity.markReconnectRequired(message: String): GmailSyncEntity = copy(
    status = "RECONNECT_NEEDED",
    message = message.take(240),
    // A page token can be tied to the old authorization flow. The history watermark and
    // backfill anchor remain so a resumed sync can restart safely without losing progress.
    nextPageToken = null,
)

/**
 * Client-side enforcement of the sender allow-list. Gmail's `from:` query narrows the server
 * result, but every downloaded message is checked again against the actual From address.
 */
internal class GmailSenderAllowList private constructor(
    private val rules: List<Rule>,
) {
    val isEmpty: Boolean get() = rules.isEmpty()

    fun allows(address: String): Boolean {
        val normalized = normalizeAddress(address) ?: return false
        val at = normalized.lastIndexOf('@')
        if (at <= 0 || at == normalized.lastIndex) return false
        val local = normalized.substring(0, at)
        val domain = normalized.substring(at + 1)
        return rules.any { it.matches(normalized, local, domain) }
    }

    fun queryTerms(): List<String> = rules.map { it.queryTerm }.distinct()

    private data class Rule(
        val queryTerm: String,
        val exactAddress: String? = null,
        val token: String = "",
        val matchDomain: Boolean = false,
        val matchLocal: Boolean = false,
    ) {
        fun matches(address: String, local: String, domain: String): Boolean {
            if (exactAddress != null) return address == exactAddress
            if (matchLocal && local == token) return true
            return matchDomain && (domain == token || domain.endsWith(".$token"))
        }
    }

    companion object {
        private val EMAIL = Regex("[a-z0-9][a-z0-9._%+\\-]*@[a-z0-9](?:[a-z0-9.\\-]*[a-z0-9])?\\.[a-z]{2,}")
        private val TOKEN = Regex("[a-z0-9][a-z0-9._\\-]{2,159}")

        fun from(entries: List<GmailSenderEntity>): GmailSenderAllowList {
            val rules = entries.mapNotNull { parse(it.pattern) }.distinctBy { it.queryTerm }
            return GmailSenderAllowList(rules)
        }

        private fun parse(raw: String): Rule? {
            val normalized = raw.trim()
                .lowercase(Locale.ROOT)
                .removePrefix("from:")
                .trim()
                .removePrefix("@")
                .trim()
            if (normalized.isEmpty()) return null
            if ('@' in normalized) {
                return normalized.takeIf { EMAIL.matches(it) }?.let { address ->
                    Rule(queryTerm = address, exactAddress = address)
                }
            }
            if (!TOKEN.matches(normalized)) return null
            return Rule(
                queryTerm = normalized,
                token = normalized,
                matchDomain = '.' in normalized,
                matchLocal = true,
            )
        }

        internal fun normalizeAddress(raw: String): String? {
            val candidate = raw.trim()
                .removePrefix("mailto:")
                .trim()
                .trim('<', '>', ' ')
                .lowercase(Locale.ROOT)
            return EMAIL.find(candidate)?.value
        }
    }
}
