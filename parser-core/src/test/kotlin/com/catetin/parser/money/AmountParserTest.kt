package com.catetin.parser.money

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AmountParserTest {

    @Test
    fun `reads Indonesian grouped rupiah`() {
        assertEquals(BigDecimal("1250000"), AmountParser.parseNumber("1.250.000", "IDR"))
    }

    @Test
    fun `reads rupiah with decimal comma`() {
        assertEquals(BigDecimal("1250000.50"), AmountParser.parseNumber("1.250.000,50", "IDR"))
    }

    @Test
    fun `reads English grouped dollars`() {
        assertEquals(BigDecimal("1250000.00"), AmountParser.parseNumber("1,250,000.00", "USD"))
    }

    @Test
    fun `reads shorthand thousands and millions`() {
        assertEquals(0, AmountParser.parseNumber("50", "IDR")!!.multiply(BigDecimal(1000)).compareTo(BigDecimal("50000")))
        assertEquals(0, AmountParser.parseNumber("1,5", "IDR")!!.multiply(BigDecimal(1000000)).compareTo(BigDecimal("1500000")))
    }

    @Test
    fun `rejects a malformed separator pattern`() {
        assertNull(AmountParser.parseNumber("12.34.567", "IDR"))
    }

    @Test
    fun `finds the payment amount before the balance`() {
        val text = "Pembayaran berhasil Rp25.000 di Kopi Kenangan. Saldo Rp1.250.000"
        val match = AmountParser.findPrimary(text)
        assertNotNull(match)
        assertEquals(BigDecimal("25000"), match!!.amount)
        assertEquals("IDR", match.currency)
    }

    @Test
    fun `reads the balance separately`() {
        val text = "Transaksi debit Rp50.000. Saldo Anda Rp2.400.000"
        assertEquals(BigDecimal("2400000"), AmountParser.findBalance(text)?.amount)
    }

    @Test
    fun `detects foreign currency`() {
        val match = AmountParser.findPrimary("Payment successful USD 15.99 at Spotify")
        assertNotNull(match)
        assertEquals("USD", match!!.currency)
        assertEquals(BigDecimal("15.99"), match.amount)
    }

    @Test
    fun `reads rupiah shorthand in notification`() {
        val match = AmountParser.findPrimary("Bayar kopi Rp25rb berhasil")
        assertNotNull(match)
        assertEquals(0, match!!.amount.compareTo(BigDecimal("25000")))
    }
}
