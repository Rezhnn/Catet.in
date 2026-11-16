package com.catetin.parser

import com.catetin.parser.model.ParseResult
import com.catetin.parser.model.RawArtifact
import com.catetin.parser.model.SourceType
import com.catetin.parser.model.TransactionDirection
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseEngineTest {

    private val ts = 1_779_000_000_000L

    private fun notification(pkg: String, text: String, title: String? = null) =
        RawArtifact(
            sourceType = SourceType.NOTIFICATION,
            sourceApp = pkg,
            title = title,
            text = text,
            timestampMillis = ts,
        )

    @Test
    fun `BCA debit becomes an expense with merchant`() {
        val result = ParseEngine.parse(
            notification(
                "com.bca",
                "Transaksi Debit BCA Rp50.000 di KOPI KENANGAN tgl 16/11/2026 12:34. Saldo Rp1.250.000",
                "Transaksi Debit",
            ),
        )
        val parsed = result as ParseResult.Parsed
        assertEquals(BigDecimal("50000"), parsed.transaction.amount)
        assertEquals("IDR", parsed.transaction.currency)
        assertEquals(TransactionDirection.EXPENSE, parsed.transaction.direction)
        assertEquals("BCA", parsed.transaction.institution)
        assertEquals("KOPI KENANGAN", parsed.transaction.merchant)
        assertEquals("Makanan & Minuman", parsed.transaction.category)
    }

    @Test
    fun `GoPay payment reads rupiah and merchant`() {
        val result = ParseEngine.parse(
            notification("com.gojek.app", "Pembayaran berhasil Rp25.000 ke Kopi Kenangan. Saldo GoPay Rp100.000"),
        )
        val parsed = result as ParseResult.Parsed
        assertEquals(BigDecimal("25000"), parsed.transaction.amount)
        assertEquals("GoPay", parsed.transaction.institution)
        assertEquals(TransactionDirection.EXPENSE, parsed.transaction.direction)
    }

    @Test
    fun `incoming DANA is income`() {
        val result = ParseEngine.parse(
            notification("id.dana", "Dana masuk Rp150.000 dari BUDI SANTOSO. Saldo DANA Rp300.000"),
        )
        val parsed = result as ParseResult.Parsed
        assertEquals(TransactionDirection.INCOME, parsed.transaction.direction)
        assertEquals(BigDecimal("150000"), parsed.transaction.amount)
    }

    @Test
    fun `OTP is ignored before any transaction is created`() {
        val result = ParseEngine.parse(
            notification("com.bca", "Kode OTP Anda 123456. Jangan berikan kode ini kepada siapa pun."),
        )
        assertTrue(result is ParseResult.Ignored)
    }

    @Test
    fun `promotion is ignored`() {
        val result = ParseEngine.parse(
            notification("id.dana", "Promo cashback Rp50.000 untuk transaksi pertamamu. Klaim sekarang!"),
        )
        assertTrue(result is ParseResult.Ignored)
    }

    @Test
    fun `failed payment is ignored`() {
        val result = ParseEngine.parse(
            notification("com.gojek.app", "Pembayaran Rp25.000 gagal. Silakan coba lagi."),
        )
        assertTrue(result is ParseResult.Ignored)
    }

    @Test
    fun `foreign currency is not silently converted to rupiah`() {
        val result = ParseEngine.parse(
            notification("com.bca", "Card transaction USD 15.99 at SPOTIFY. Thank you."),
        )
        val parsed = result as ParseResult.Parsed
        assertEquals("USD", parsed.transaction.currency)
        assertEquals(BigDecimal("15.99"), parsed.transaction.amount)
    }

    @Test
    fun `email uses merchant field and reference`() {
        val email = RawArtifact(
            sourceType = SourceType.EMAIL,
            sourceApp = "receipts@shopee.co.id",
            subject = "Bukti pembayaran Shopee",
            text = """
                Halo, pembayaran kamu berhasil.
                Merchant: Toko Sinar Jaya
                Total: Rp125.000
                No. Referensi: SPX-20261116-001
                Terima kasih.
            """.trimIndent(),
            timestampMillis = ts,
        )
        val parsed = ParseEngine.parse(email) as ParseResult.Parsed
        assertEquals(BigDecimal("125000"), parsed.transaction.amount)
        assertEquals("Toko Sinar Jaya", parsed.transaction.merchant)
        assertEquals("SPX-20261116-001", parsed.transaction.reference)
    }

    @Test
    fun `balance-only message does not create a transaction`() {
        val result = ParseEngine.parse(
            notification("com.bca", "Saldo rekening Anda Rp1.250.000 per 16/11/2026."),
        )
        assertTrue(result is ParseResult.Ignored)
    }

    @Test
    fun `completed payment is kept even when the receipt mentions a promo`() {
        val result = ParseEngine.parse(
            notification("com.bca", "Pembayaran berhasil Rp25.000 di KOPI KENANGAN. Promo hemat diterapkan."),
        )
        val parsed = result as ParseResult.Parsed
        assertEquals(BigDecimal("25000"), parsed.transaction.amount)
        assertEquals(TransactionDirection.EXPENSE, parsed.transaction.direction)
    }

    @Test
    fun `wallet top up is a transfer instead of income`() {
        val result = ParseEngine.parse(
            notification("com.gojek.app", "Top up berhasil Rp100.000 ke saldo GoPay."),
        )
        val parsed = result as ParseResult.Parsed
        assertEquals(TransactionDirection.TRANSFER, parsed.transaction.direction)
    }
}
