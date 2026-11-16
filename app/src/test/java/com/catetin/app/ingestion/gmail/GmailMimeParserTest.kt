package com.catetin.app.ingestion.gmail

import java.nio.charset.Charset
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GmailMimeParserTest {

    @Test
    fun `decodes RFC 2047 subject`() {
        val decoded = GmailMimeParser.decodeMimeHeader("=?UTF-8?B?VHJhbnNha3NpIEJlcnRoYQ==?=")
        assertEquals("Transaksi Bertha", decoded)
    }

    @Test
    fun `prefers plain text over html alternative`() {
        val plain = body("Nominal: Rp25.000\nMerchant: Kopi", "text/plain")
        val html = body("<p>Nominal: Rp25.000</p>", "text/html")
        val part = GmailPart(
            mimeType = "multipart/alternative",
            parts = listOf(html, plain),
        )
        val result = GmailMimeParser.extractPreferredText(part)
        assertTrue(result.contains("Merchant: Kopi"))
        assertFalse(result.contains("<p>"))
    }

    @Test
    fun `strips html when only html body exists`() {
        val parsed = GmailMimeParser.toParsed(
            GmailMessage(
                id = "msg-1",
                internalDate = "1779000000000",
                payload = GmailPart(
                    mimeType = "text/html",
                    headers = listOf(
                        GmailHeader("From", "BCA <no-reply@bca.co.id>"),
                        GmailHeader("Subject", "Transaksi BCA"),
                    ),
                    body = GmailBody(data = base64Url("<div>Pembayaran <b>Rp50.000</b> berhasil</div>")),
                ),
            ),
        )
        requireNotNull(parsed)
        assertTrue(parsed.text.contains("Pembayaran Rp50.000 berhasil"))
        assertFalse(parsed.text.contains("<div>"))
        assertEquals("no-reply@bca.co.id", parsed.senderAddress)
    }

    @Test
    fun `honours non utf8 charset`() {
        val latin = "Kafe sudah dibayar".toByteArray(Charset.forName("ISO-8859-1"))
        val data = Base64.getUrlEncoder().withoutPadding().encodeToString(latin)
        val part = GmailPart(
            mimeType = "text/plain; charset=ISO-8859-1",
            headers = listOf(GmailHeader("Content-Type", "text/plain; charset=ISO-8859-1")),
            body = GmailBody(data = data),
        )
        val text = GmailMimeParser.extractPreferredText(part)
        assertTrue(text.contains("Kafe"))
    }

    @Test
    fun `decodes quoted printable encoded words and underscores`() {
        val decoded = GmailMimeParser.decodeMimeHeader(
            "=?UTF-8?Q?Transaksi=20Berhasil=20=3DRp50.000?=",
        )
        assertEquals("Transaksi Berhasil =Rp50.000", decoded)
    }

    @Test
    fun `joins folded adjacent encoded words`() {
        val decoded = GmailMimeParser.decodeMimeHeader(
            "=?UTF-8?Q?Transaksi=20?=\r\n =?UTF-8?Q?Berhasil?=",
        )
        assertEquals("Transaksi Berhasil", decoded)
    }

    @Test
    fun `decodes base64url body containing line breaks`() {
        val encoded = base64Url("Nominal: Rp25.000\nMerchant: Kopi")
            .chunked(8)
            .joinToString("\n")
        val part = GmailPart(
            mimeType = "text/plain",
            headers = listOf(GmailHeader("Content-Type", "text/plain; charset=UTF-8")),
            body = GmailBody(data = encoded),
        )
        val text = GmailMimeParser.extractPreferredText(part)
        assertTrue(text.contains("Merchant: Kopi"))
    }

    @Test
    fun `inherits charset from multipart parent`() {
        val latin = "Kafe sudah dibayar".toByteArray(Charset.forName("ISO-8859-1"))
        val data = Base64.getUrlEncoder().withoutPadding().encodeToString(latin)
        val part = GmailPart(
            mimeType = "multipart/alternative",
            headers = listOf(GmailHeader("Content-Type", "multipart/alternative; charset=ISO-8859-1")),
            parts = listOf(
                GmailPart(
                    mimeType = "text/plain",
                    body = GmailBody(data = data),
                ),
            ),
        )
        assertTrue(GmailMimeParser.extractPreferredText(part).contains("Kafe"))
    }

    @Test
    fun `inline html tags do not split an amount`() {
        val part = GmailPart(
            mimeType = "text/html",
            headers = listOf(GmailHeader("Content-Type", "text/html; charset=UTF-8")),
            body = GmailBody(data = base64Url("<p>Nominal: Rp<b>50</b>.000</p>")),
        )
        val text = GmailMimeParser.extractPreferredText(part)
        assertTrue(text.contains("Rp50.000"))
        assertFalse(text.contains("<") )
    }

    @Test
    fun `decodes numeric and named html entities`() {
        val part = GmailPart(
            mimeType = "text/html",
            headers = listOf(GmailHeader("Content-Type", "text/html; charset=UTF-8")),
            body = GmailBody(
                data = base64Url(
                    "<p>Total&nbsp;Rp50.000 &amp; biaya &#39;admin&#39; &mdash; lunas &#x2705;</p>",
                ),
            ),
        )
        val text = GmailMimeParser.extractPreferredText(part)
        assertTrue(text.contains("Total Rp50.000 & biaya 'admin' \u2014 lunas \u2705"))
    }

    @Test
    fun `extracts the address from an encoded display name`() {
        val parsed = GmailMimeParser.toParsed(
            GmailMessage(
                id = "msg-2",
                internalDate = "1779000000000",
                payload = GmailPart(
                    mimeType = "text/plain",
                    headers = listOf(
                        GmailHeader("From", "=?UTF-8?Q?BCA?= <No-Reply@BCA.co.id>"),
                        GmailHeader("Subject", "Transaksi"),
                    ),
                    body = GmailBody(data = base64Url("Pembayaran Rp50.000")),
                ),
            ),
        )
        requireNotNull(parsed)
        assertEquals("BCA", parsed.sender)
        assertEquals("no-reply@bca.co.id", parsed.senderAddress)
    }

    @Test
    fun `does not double decode a quoted printable body from gmail`() {
        // Gmail format=full has already removed the MIME transfer encoding.
        val parsed = GmailMimeParser.toParsed(
            GmailMessage(
                id = "msg-3",
                internalDate = "1779000000000",
                payload = GmailPart(
                    mimeType = "text/plain",
                    headers = listOf(
                        GmailHeader("From", "BCA <no-reply@bca.co.id>"),
                        GmailHeader("Content-Type", "text/plain; charset=UTF-8"),
                        GmailHeader("Content-Transfer-Encoding", "quoted-printable"),
                    ),
                    body = GmailBody(data = base64Url("Harga = 25.000")),
                ),
            ),
        )
        requireNotNull(parsed)
        assertTrue(parsed.text.contains("Harga = 25.000"))
    }

    private fun body(text: String, mime: String) = GmailPart(
        mimeType = mime,
        headers = listOf(GmailHeader("Content-Type", "$mime; charset=UTF-8")),
        body = GmailBody(data = base64Url(text)),
    )

    private fun base64Url(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
}
