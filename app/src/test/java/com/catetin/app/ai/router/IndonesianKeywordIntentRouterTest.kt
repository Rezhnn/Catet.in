package com.catetin.app.ai.router

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class IndonesianKeywordIntentRouterTest {

    @Test
    fun `pengeluaran bulan ini is deterministic`() {
        val intent = IndonesianKeywordIntentRouter.match("Pengeluaran bulan ini")
        assertNotNull(intent)
        assertEquals(IntentMetric.SPENDING, intent!!.metric)
        assertEquals(IntentPeriod.THIS_MONTH, intent.period)
        assertEquals("EXPENSE", intent.direction)
    }

    @Test
    fun `belanja makan minggu lalu maps to food and last week`() {
        val intent = IndonesianKeywordIntentRouter.match("Belanja makan minggu lalu")
        assertNotNull(intent)
        assertEquals(IntentPeriod.LAST_WEEK, intent!!.period)
        assertEquals("Makanan & Minuman", intent.category)
    }

    @Test
    fun `total jajan hari ini maps to today`() {
        val intent = IndonesianKeywordIntentRouter.match("Total jajan hari ini")
        assertNotNull(intent)
        assertEquals(IntentPeriod.TODAY, intent!!.period)
        assertEquals("Makanan & Minuman", intent.category)
    }

    @Test
    fun `pemasukan bulan ini maps to income`() {
        val intent = IndonesianKeywordIntentRouter.match("Pemasukan bulan ini")
        assertNotNull(intent)
        assertEquals(IntentMetric.INCOME, intent!!.metric)
        assertEquals("INCOME", intent.direction)
    }

    @Test
    fun `transportasi minggu ini maps to transport`() {
        val intent = IndonesianKeywordIntentRouter.match("Pengeluaran transportasi minggu ini")
        assertNotNull(intent)
        assertEquals("Transportasi", intent!!.category)
        assertEquals(IntentPeriod.THIS_WEEK, intent.period)
    }

    @Test
    fun `transaksi terbesar bulan ini maps to largest`() {
        val intent = IndonesianKeywordIntentRouter.match("Transaksi terbesar bulan ini")
        assertNotNull(intent)
        assertEquals(IntentMetric.LARGEST, intent!!.metric)
        assertEquals(1, intent.limit)
    }

    @Test
    fun `saldo saat ini maps to balance`() {
        val intent = IndonesianKeywordIntentRouter.match("Saldo saat ini")
        assertNotNull(intent)
        assertEquals(IntentMetric.BALANCE, intent!!.metric)
    }

    @Test
    fun `greeting is not a deterministic intent`() {
        assertNull(IndonesianKeywordIntentRouter.match("Halo, apa kabar?"))
    }

    @Test
    fun `question without period falls through to the model`() {
        assertNull(IndonesianKeywordIntentRouter.match("Ceritakan pola pengeluaranku"))
    }
}
