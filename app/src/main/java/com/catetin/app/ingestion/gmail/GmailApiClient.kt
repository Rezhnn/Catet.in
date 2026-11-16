package com.catetin.app.ingestion.gmail

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@Singleton
class GmailApiClient @Inject constructor() : GmailSyncApi {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    override suspend fun profile(accessToken: String): GmailProfile = decode(
        get("/gmail/v1/users/me/profile", accessToken),
        GmailProfile.serializer(),
        "respons profil Gmail",
    )

    override suspend fun listMessages(
        accessToken: String,
        query: String?,
        pageToken: String?,
        maxResults: Int,
    ): GmailMessageList {
        val params = linkedMapOf("maxResults" to maxResults.coerceIn(1, 100).toString())
        if (!query.isNullOrBlank()) params["q"] = query
        if (!pageToken.isNullOrBlank()) params["pageToken"] = pageToken
        return decode(
            get("/gmail/v1/users/me/messages", accessToken, params),
            GmailMessageList.serializer(),
            "respons daftar email",
        )
    }

    override suspend fun message(accessToken: String, messageId: String): GmailMessage = decode(
        get("/gmail/v1/users/me/messages/${encodePath(messageId)}", accessToken, mapOf("format" to "full")),
        GmailMessage.serializer(),
        "respons email",
    )

    override suspend fun history(
        accessToken: String,
        startHistoryId: String,
        pageToken: String?,
        maxResults: Int,
    ): GmailHistoryList {
        val params = linkedMapOf(
            "startHistoryId" to startHistoryId,
            "maxResults" to maxResults.coerceIn(1, 500).toString(),
            "historyTypes" to "messageAdded",
        )
        if (!pageToken.isNullOrBlank()) params["pageToken"] = pageToken
        return decode(
            get("/gmail/v1/users/me/history", accessToken, params),
            GmailHistoryList.serializer(),
            "respons riwayat Gmail",
        )
    }

    private suspend fun get(
        path: String,
        accessToken: String,
        query: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        require(accessToken.isNotBlank()) { "Token akses Gmail kosong" }
        withGmailRetry(
            delayFor = { attempt ->
                delay((1_000L shl attempt) + Random.nextLong(0, 500))
            },
        ) {
            var connection: HttpURLConnection? = null
            try {
                val queryString = if (query.isEmpty()) "" else "?" + query.entries.joinToString("&") {
                    URLEncoder.encode(it.key, StandardCharsets.UTF_8.name()) + "=" +
                        URLEncoder.encode(it.value, StandardCharsets.UTF_8.name())
                }
                connection = (URL("https://gmail.googleapis.com$path$queryString").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 20_000
                    readTimeout = 45_000
                    instanceFollowRedirects = false
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "Catet.in-Android/1")
                }
                val code = connection.responseCode
                if (code in 200..299) {
                    return@withGmailRetry readLimited(connection.inputStream, 12 * 1024 * 1024)
                }

                val detail = readLimited(connection.errorStream, 32 * 1024)
                    .replace(Regex("(?i)ya29\\.[A-Za-z0-9._-]+"), "[redacted]")
                when {
                    code == 401 -> throw GmailReconnectRequiredException("Akses Gmail kedaluwarsa atau dicabut")
                    code == 404 -> throw GmailApiException("Sumber Gmail tidak ditemukan", code)
                    code == 403 && isGmailRateLimit(detail) -> {
                        throw GmailApiException("Gmail sementara membatasi permintaan", code, retryable = true)
                    }
                    else -> throw GmailApiException(
                        "Gmail API error HTTP $code: ${detail.take(240)}",
                        code,
                        retryable = code == 429 || code in 500..599,
                    )
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun readLimited(input: InputStream?, maxBytes: Int): String {
        if (input == null) return ""
        input.use {
            val out = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
            val buffer = ByteArray(32 * 1024)
            var total = 0
            while (true) {
                val read = it.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) throw GmailApiException("Respons Gmail terlalu besar", 0)
                out.write(buffer, 0, read)
            }
            return out.toString(StandardCharsets.UTF_8.name())
        }
    }

    private fun encodePath(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun isGmailRateLimit(detail: String): Boolean = listOf(
        "rateLimitExceeded",
        "userRateLimitExceeded",
        "backendError",
        "quotaExceeded",
    ).any { detail.contains(it, ignoreCase = true) }

    private fun <T> decode(text: String, serializer: kotlinx.serialization.KSerializer<T>, label: String): T =
        runCatching { json.decodeFromString(serializer, text) }
            .getOrElse { throw GmailApiException("$label tidak valid", 0, it) }
}

/**
 * Retries only transient Gmail failures. Cancellation and reconnect decisions are never retried.
 */
internal suspend fun <T> withGmailRetry(
    maxAttempts: Int = 4,
    delayFor: suspend (attempt: Int) -> Unit,
    block: suspend () -> T,
): T {
    require(maxAttempts > 0) { "maxAttempts harus positif" }
    var lastFailure: Throwable? = null
    for (attempt in 0 until maxAttempts) {
        try {
            return block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (reconnect: GmailReconnectRequiredException) {
            throw reconnect
        } catch (api: GmailApiException) {
            if (!api.retryable) throw api
            lastFailure = api
        } catch (t: Exception) {
            lastFailure = t
        }
        if (attempt < maxAttempts - 1) delayFor(attempt)
    }
    val status = (lastFailure as? GmailApiException)?.statusCode ?: 0
    throw GmailApiException(
        "Gagal menghubungi Gmail setelah beberapa percobaan",
        status,
        lastFailure,
        retryable = true,
    )
}
