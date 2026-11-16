package com.catetin.app.domain.budget

import com.catetin.app.data.db.BudgetEntity
import com.catetin.app.data.db.TransactionEntity
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetMathTest {
    private fun transaction(
        id: Long,
        amount: String,
        direction: String,
        category: String = "Lainnya",
        currency: String = "IDR",
    ) = TransactionEntity(
        id = id,
        amount = amount,
        currency = currency,
        direction = direction,
        category = category,
        occurredAt = 1L,
    )

    private fun budget(
        id: Long,
        amount: String,
        type: String,
        category: String? = null,
        currency: String = "IDR",
    ) = BudgetEntity(
        id = id,
        name = "Rencana $id",
        type = type,
        amount = amount,
        currency = currency,
        category = category,
    )

    @Test
    fun `limit and estimate totals are calculated separately`() {
        val budgets = listOf(
            budget(1, "2000000", BudgetMath.LIMIT),
            budget(2, "750000", BudgetMath.EXPECTED),
            budget(3, "500000", BudgetMath.TARGET),
        )

        assertEquals(BigDecimal("2000000"), BudgetMath.total(budgets, BudgetMath.LIMIT))
        assertEquals(BigDecimal("750000"), BudgetMath.total(budgets, BudgetMath.EXPECTED))
        assertEquals(BigDecimal("500000"), BudgetMath.total(budgets, BudgetMath.TARGET))
    }

    @Test
    fun `category budget only counts matching expenses in its currency`() {
        val transactions = listOf(
            transaction(1, "50000", "EXPENSE", category = "Makanan & Minuman"),
            transaction(2, "75000", "EXPENSE", category = "Transportasi"),
            transaction(3, "40000", "EXPENSE", category = "Makanan & Minuman", currency = "USD"),
        )
        val makanan = budget(1, "500000", BudgetMath.LIMIT, category = "Makanan & Minuman")

        assertEquals(BigDecimal("50000"), BudgetMath.budgetSpent(makanan, transactions))
    }

    @Test
    fun `target progress uses positive income minus expenses`() {
        val transactions = listOf(
            transaction(1, "1000000", "INCOME"),
            transaction(2, "350000", "EXPENSE"),
        )
        val target = budget(1, "500000", BudgetMath.TARGET)

        assertEquals(BigDecimal("650000"), BudgetMath.targetAchieved(target, transactions))
        assertEquals(1.3f, BudgetMath.fraction(BudgetMath.targetAchieved(target, transactions), BudgetMath.amount(target)), 0.0001f)
    }

    @Test
    fun `target never receives negative progress when expenses exceed income`() {
        val transactions = listOf(
            transaction(1, "100000", "INCOME"),
            transaction(2, "250000", "EXPENSE"),
        )
        val target = budget(1, "500000", BudgetMath.TARGET)

        assertEquals(BigDecimal.ZERO, BudgetMath.targetAchieved(target, transactions))
    }
}
