package com.catetin.app.domain.budget

import com.catetin.app.data.db.BudgetEntity
import com.catetin.app.data.db.TransactionEntity
import java.math.BigDecimal

/** Pure budget calculations kept outside Compose so their edge cases can be tested. */
object BudgetMath {
    const val LIMIT = "LIMIT"
    const val EXPECTED = "EXPECTED"
    const val TARGET = "TARGET"

    fun typeLabel(type: String): String = when (type) {
        TARGET -> "Target capaian"
        EXPECTED -> "Estimasi"
        else -> "Batas"
    }

    fun amount(budget: BudgetEntity): BigDecimal =
        budget.amount.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ZERO

    fun total(budgets: Iterable<BudgetEntity>, type: String): BigDecimal =
        budgets.filter { it.type == type }.fold(BigDecimal.ZERO) { sum, budget -> sum + amount(budget) }

    fun spending(
        transactions: Iterable<TransactionEntity>,
        category: String? = null,
        currency: String? = null,
    ): BigDecimal = transactions.fold(BigDecimal.ZERO) { sum, transaction ->
        val matches = transaction.direction == "EXPENSE" &&
            (category == null || transaction.category == category) &&
            (currency == null || transaction.currency == currency)
        if (matches) sum + transactionAmount(transaction) else sum
    }

    fun savings(transactions: Iterable<TransactionEntity>, currency: String? = null): BigDecimal {
        val relevant = transactions.filter { currency == null || it.currency == currency }
        val income = relevant.filter { it.direction == "INCOME" }
            .fold(BigDecimal.ZERO) { sum, transaction -> sum + transactionAmount(transaction) }
        val expense = spending(relevant, currency = currency)
        return (income - expense).max(BigDecimal.ZERO)
    }

    fun budgetSpent(budget: BudgetEntity, transactions: Iterable<TransactionEntity>): BigDecimal =
        spending(transactions, category = budget.category, currency = budget.currency)

    /** Targets are funded by the positive monthly cash flow (income minus expenses). */
    fun targetAchieved(target: BudgetEntity, transactions: Iterable<TransactionEntity>): BigDecimal =
        savings(transactions, currency = target.currency)

    fun fraction(used: BigDecimal, goal: BigDecimal): Float {
        if (goal <= BigDecimal.ZERO) return 0f
        return (used.toFloat() / goal.toFloat()).coerceAtLeast(0f)
    }

    private fun transactionAmount(transaction: TransactionEntity): BigDecimal =
        transaction.amount.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ZERO
}
