package com.catetin.app.ingestion.gmail

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Locale
import java.util.TimeZone
import kotlinx.serialization.json.Json

object GmailMimeParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val encodedWord = Regex("=\\?([^?\\s]+)\\?([BbQq])\\?([^?]*)\\?=")
    private val adjacentEncodedWords = Regex("(\\?=)\\s+(=\\?)")
    private val foldedHeader = Regex("\\r?\\n[ \\t]+")
    private val emailAddress = Regex("(?i)[a-z0-9._%+\\-]+@[a-z0-9.\\-]+\\.[a-z]{2,}")
    private val charsetParameter = Regex("(?i)charset\\s*\\*?\\s*=\\s*[\"']?([^;\"'\\s]+)")

    fun decodeMessage(jsonText: String): GmailMessage =
        json.decodeFromString(GmailMessage.serializer(), jsonText)

    fun toParsed(message: GmailMessage): ParsedGmailMessage? {
        val payload = message.payload ?: return null
        val headers = payload.headers.associateBy { it.name.lowercase(Locale.ROOT) }
        val rawSender = decodeMimeHeader(headers["from"]?.value.orEmpty())
        val subject = decodeMimeHeader(headers["subject"]?.value.orEmpty()).trim()
        val senderAddress = extractAddress(rawSender)
        val senderLabel = extractLabel(rawSender, senderAddress)

        val bodyText = extractPreferredText(payload).trim()
        val fallback = cleanPlainText(decodeHtmlEntities(message.snippet)).trim()
        val content = bodyText.ifBlank { fallback }
        if (content.isBlank()) return null

        val timestamp = message.internalDate?.trim()?.toLongOrNull()
            ?: parseHeaderDate(headers["date"]?.value)
            ?: System.currentTimeMillis()
        val fullText = buildString {
            if (senderLabel.isNotBlank()) append("Dari: ").append(senderLabel).append('\n')
            if (senderAddress.isNotBlank()) append("Email: ").append(senderAddress).append('\n')
            if (subject.isNotBlank()) append("Subjek: ").append(subject).append('\n')
            append(content.take(11_500))
        }
        return ParsedGmailMessage(
            messageId = message.id,
            threadId = message.threadId,
            sender = senderLabel.ifBlank { senderAddress },
            senderAddress = senderAddress,
            subject = subject,
            receivedAt = timestamp,
            text = fullText,
        )
    }

    internal fun extractPreferredText(part: GmailPart): String {
        val plain = collectText(part, preferredMime = "text/plain", inheritedCharset = null)
        if (plain.isNotBlank()) return cleanPlainText(plain)
        val html = collectText(part, preferredMime = "text/html", inheritedCharset = null)
        return cleanPlainText(stripHtml(html))
    }

    private fun collectText(
        part: GmailPart,
        preferredMime: String,
        inheritedCharset: Charset?,
    ): String {
        if (part.filename?.isNotBlank() == true) return ""
        val charset = contentTypeCharset(part) ?: inheritedCharset ?: Charsets.UTF_8
        val mime = part.mimeType.substringBefore(';').trim().lowercase(Locale.ROOT)
        if (mime == preferredMime) {
            val data = part.body?.data
            if (!data.isNullOrBlank()) {
                val decoded = decodeBody(data, charset)
                if (decoded.isNotBlank()) return decoded
            }
        }
        if (mime.startsWith("multipart/") || part.parts.isNotEmpty()) {
            val children = part.parts.mapNotNull { child ->
                collectText(child, preferredMime, charset).takeIf { it.isNotBlank() }
            }
            if (children.isNotEmpty()) return children.joinToString("\n")
        }
        return ""
    }

    private fun contentTypeCharset(part: GmailPart): Charset? {
        val contentType = part.headers.firstOrNull { it.name.equals("Content-Type", true) }?.value
            ?: part.mimeType
        val name = charsetParameter.find(contentType)?.groupValues?.getOrNull(1) ?: return null
        return runCatching { Charset.forName(name) }.getOrNull()
    }

    /**
     * Gmail's format=full payload is already transfer-decoded. Only the Gmail base64url
     * envelope is removed here; quoted-printable content must not be decoded a second time.
     */
    internal fun decodeBody(data: String, charset: Charset = Charsets.UTF_8): String {
        val compact = data.filterNot { it.isWhitespace() }
        if (compact.isEmpty()) return ""
        val normalized = compact.replace('-', '+').replace('_', '/')
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        val bytes = runCatching { Base64.getDecoder().decode(padded) }
            .recoverCatching { Base64.getMimeDecoder().decode(compact) }
            .getOrElse { return "" }
        return String(bytes, charset)
    }

    internal fun decodeMimeHeader(value: String): String {
        val unfolded = value.replace(foldedHeader, " ").trim()
        if (!unfolded.contains("=?")) return unfolded
        val joined = unfolded.replace(adjacentEncodedWords, "$1$2")
        return encodedWord.replace(joined) { match ->
            val charsetName = match.groupValues[1].substringBefore('*')
            val charset = runCatching { Charset.forName(charsetName) }.getOrDefault(Charsets.UTF_8)
            when (match.groupValues[2].uppercase(Locale.ROOT)) {
                "B" -> runCatching {
                    val encoded = match.groupValues[3]
                    val padded = encoded + "=".repeat((4 - encoded.length % 4) % 4)
                    String(Base64.getMimeDecoder().decode(padded), charset)
                }.getOrDefault(match.value)
                else -> runCatching {
                    String(decodeQuotedPrintable(match.groupValues[3].replace('_', ' ')), charset)
                }.getOrDefault(match.value)
            }
        }
    }

    private fun decodeQuotedPrintable(value: String): ByteArray {
        val out = ByteArrayOutputStream()
        var index = 0
        while (index < value.length) {
            val ch = value[index]
            when {
                ch == '=' && index + 2 < value.length && value[index + 1] == '\r' && value[index + 2] == '\n' -> index += 3
                ch == '=' && index + 1 < value.length && value[index + 1] == '\n' -> index += 2
                ch == '=' && index + 2 < value.length -> {
                    val byte = value.substring(index + 1, index + 3).toIntOrNull(16)
                    if (byte != null) {
                        out.write(byte)
                        index += 3
                    } else {
                        out.write(ch.code)
                        index++
                    }
                }
                else -> {
                    out.write(ch.code)
                    index++
                }
            }
        }
        return out.toByteArray()
    }

    internal fun extractAddress(rawSender: String): String {
        val angleAddress = Regex("<([^<>]+)>").find(rawSender)?.groupValues?.getOrNull(1)
        val candidate = angleAddress ?: emailAddress.find(rawSender)?.value
        return candidate.orEmpty()
            .trim()
            .removePrefix("mailto:")
            .trim()
            .trim('<', '>', ' ')
            .lowercase(Locale.ROOT)
    }

    private fun extractLabel(rawSender: String, address: String): String {
        val beforeAddress = if ('<' in rawSender) {
            rawSender.substringBefore('<')
        } else if (address.isNotBlank()) {
            rawSender.replace(address, "", ignoreCase = true)
        } else {
            rawSender
        }
        return beforeAddress
            .trim()
            .trim('"', '\'', ' ', ',', ';')
            .ifBlank { address }
    }

    internal fun stripHtml(raw: String): String = decodeHtmlEntities(
        raw
            .replace(Regex("(?is)<!--.*?-->"), " ")
            .replace(Regex("(?is)<(script|style|head|title)\\b[^>]*>.*?</\\1\\s*>"), " ")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(
                Regex("(?i)</?(?:p|div|li|tr|h[1-6]|blockquote|table|section|article|header|footer|main|aside|hr)\\b[^>]*>"),
                "\n",
            )
            .replace(Regex("(?i)</?(?:td|th)\\b[^>]*>"), " ")
            .replace(Regex("(?is)<[^>]+>"), ""),
    )

    internal fun decodeHtmlEntities(raw: String): String {
        var value = raw
        value = Regex("&#x([0-9a-fA-F]+);").replace(value) { match ->
            match.groupValues[1].toIntOrNull(16)
                ?.takeIf(Character::isValidCodePoint)
                ?.let { String(Character.toChars(it)) }
                ?: match.value
        }
        value = Regex("&#([0-9]+);").replace(value) { match ->
            match.groupValues[1].toIntOrNull()
                ?.takeIf(Character::isValidCodePoint)
                ?.let { String(Character.toChars(it)) }
                ?: match.value
        }
        val named = linkedMapOf(
            "&nbsp;" to " ",
            "&quot;" to "\"",
            "&apos;" to "'",
            "&lt;" to "<",
            "&gt;" to ">",
            "&ndash;" to "\u2013",
            "&mdash;" to "\u2014",
            "&hellip;" to "\u2026",
            "&lsquo;" to "\u2018",
            "&rsquo;" to "\u2019",
            "&ldquo;" to "\u201C",
            "&rdquo;" to "\u201D",
            "&middot;" to "\u00B7",
            "&bull;" to "\u2022",
            "&copy;" to "\u00A9",
            "&reg;" to "\u00AE",
            "&trade;" to "\u2122",
            "&euro;" to "\u20AC",
            "&pound;" to "\u00A3",
            "&yen;" to "\u00A5",
        )
        named.forEach { (entity, replacement) -> value = value.replace(entity, replacement) }
        return value.replace("&amp;", "&")
    }

    private fun cleanPlainText(raw: String): String = raw
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace("\uFEFF", "")
        .replace("\u200B", "")
        .replace(Regex("[\\t\\u00A0]+"), " ")
        .replace(Regex("[ ]{2,}"), " ")
        .replace(Regex(" *\\n *"), "\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    private fun parseHeaderDate(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val cleaned = value.replace(Regex("\\([^)]*\\)"), "").trim()
        val patterns = listOf(
            "EEE, d MMM yyyy HH:mm:ss Z",
            "d MMM yyyy HH:mm:ss Z",
            "EEE, d MMM yyyy HH:mm:ss z",
        )
        return patterns.firstNotNullOfOrNull { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(cleaned)?.time
            }.getOrNull()
        }
    }
}
