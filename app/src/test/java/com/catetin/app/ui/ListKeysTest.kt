package com.catetin.app.ui

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListKeysTest {
    @Test
    fun `same row id in different tables produces different compose keys`() {
        val budgetKey = ListKeys.budget(1L)
        val transactionKey = ListKeys.transaction(1L)

        assertNotEquals(budgetKey, transactionKey)
        assertTrue(budgetKey.startsWith("budget:"))
        assertTrue(transactionKey.startsWith("transaction:"))
    }

    @Test
    fun `merchant and recurring analytics keys cannot collide`() {
        assertNotEquals(
            ListKeys.merchant("Kopi-IDR"),
            ListKeys.recurring("Kopi", "IDR"),
        )
    }
}
