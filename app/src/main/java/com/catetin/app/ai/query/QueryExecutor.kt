package com.catetin.app.ai.query

import com.catetin.app.ai.router.IntentMetric
import com.catetin.app.ai.router.IntentPeriod
import com.catetin.app.ai.router.QueryIntent
import com.catetin.app.data.db.TransactionEntity
import com.catetin.app.data.repo.FinanceRepository
import com.catetin.app.util.CurrencyFormatter
import com.catetin.app.util.Dates
import com.catetin.app.util.PeriodPreset
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

data class AssistantFigure(
    val label: String,
    val amount: BigDecimal,
    val currency: String,
)

data class AssistantAnswer(
    val text: String,
    val figures: List<AssistantFigure> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val intent: QueryIntent? = null,
)

@Singleton
class QueryExecutor @Inject constructor(
    private val repository: FinanceRepository,
) {

    suspend fun execute(intent: QueryIntent): AssistantAnswer {
        if (intent.metric == IntentMetric.BALANCE) return balanceAnswer(intent)

        val range = Dates.range(PeriodPreset.valueOf(intent.period.name))
        val all = repository.getTransactionsBetween(range.start, range.end)
        val filtered = all.filter { transaction ->
            (intent.direction == null || transaction.direction == intent.direction) &&
                (intent.category == null || transaction.category == intent.category)
        }
        val periodLabel = Dates.periodLabel(PeriodPreset.valueOf(intent.period.name)).lowercase()
        val categoryPart = intent.category?.let { " " + it } ?: ""

        return when (intent.metric) {
            IntentMetric.LARGEST -> largestAnswer(filtered, intent, periodLabel, categoryPart)
            IntentMetric.COUNT -> countAnswer(filtered, categoryPart, periodLabel)
            IntentMetric.BREAKDOWN -> breakdownAnswer(filtered, periodLabel)
            IntentMetric.INCOME -> totalAnswer(
                filtered,
                label = "pemasukan" + categoryPart,
                periodLabel = periodLabel,
                emptyText = "Belum ada pemasukan" + categoryPart + " " + periodLabel + ".",
                intent = intent,
            )
            else -> totalAnswer(
                filtered,
                label = "pengeluaran" + categoryPart,
                periodLabel = periodLabel,
                emptyText = "Belum ada pengeluaran" + categoryPart + " " + periodLabel + ".",
                intent = intent,
            )
        }
    }

    private fun totalAnswer(
        transactions: List<TransactionEntity>,
        label: String,
        periodLabel: String,
        emptyText: String,
        intent: QueryIntent,
    ): AssistantAnswer {
        if (transactions.isEmpty()) return AssistantAnswer(emptyText, intent = intent)
        val totals = totalsByCurrency(transactions)
        val figures = totals.map { (currency, total) ->
            AssistantFigure("Total " + currency, total, currency)
        }
        val amountText = totals.entries.joinToString(", ") { (currency, total) ->
            CurrencyFormatter.format(total, currency)
        }
        val text = "Total " + label + " " + periodLabel + " adalah " + amountText +
            " dari " + transactions.size + " transaksi."
        return AssistantAnswer(text, figures, transactions.take(5), intent)
    }

    private fun largestAnswer(
        transactions: List<TransactionEntity>,
        intent: QueryIntent,
        periodLabel: String,
        categoryPart: String,
    ): AssistantAnswer {
        if (transactions.isEmpty()) {
            return AssistantAnswer("Belum ada transaksi" + categoryPart + " " + periodLabel + ".", intent = intent)
        }
        val largest = transactions.maxByOrNull { it.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO }!!
        val amount = largest.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val merchant = largest.merchant?.takeIf { it.isNotBlank() } ?: "tanpa nama merchant"
        val text = "Transaksi terbesar" + categoryPart + " " + periodLabel + " adalah " +
            CurrencyFormatter.format(amount, largest.currency) + " di " + merchant +
            " pada " + Dates.formatDate(largest.occurredAt) + "."
        return AssistantAnswer(
            text,
            listOf(AssistantFigure(merchant, amount, largest.currency)),
            listOf(largest),
            intent,
        )
    }

    private fun countAnswer(
        transactions: List<TransactionEntity>,
        categoryPart: String,
        periodLabel: String,
    ): AssistantAnswer {
        val text = if (transactions.isEmpty()) {
            "Tidak ada transaksi" + categoryPart + " " + periodLabel + "."
        } else {
            "Ada " + transactions.size + " transaksi" + categoryPart + " " + periodLabel + "."
        }
        return AssistantAnswer(text, transactions = transactions.take(5))
    }

    private fun breakdownAnswer(
        transactions: List<TransactionEntity>,
        periodLabel: String,
    ): AssistantAnswer {
        if (transactions.isEmpty()) return AssistantAnswer("Belum ada pengeluaran " + periodLabel + ".")
        val grouped = transactions.groupBy { it.category }
            .mapValues { entry -> totalsByCurrency(entry.value) }
            .entries
            .sortedByDescending { entry -> entry.value.values.fold(BigDecimal.ZERO) { acc, v -> acc + v } }
            .take(5)
        val figures = grouped.flatMap { (category, totals) ->
            totals.map { (currency, total) -> AssistantFigure(category, total, currency) }
        }
        val text = "Rincian pengeluaran " + periodLabel + ": " +
            grouped.joinToString("; ") { (category, totals) ->
                category + " " + totals.entries.joinToString(", ") { (currency, total) ->
                    CurrencyFormatter.format(total, currency)
                }
            } + "."
        return AssistantAnswer(text, figures, transactions.take(5))
    }

    private suspend fun balanceAnswer(intent: QueryIntent): AssistantAnswer {
        val balances = repository.getLatestBalances()
        if (balances.isEmpty()) {
            return AssistantAnswer(
                "Belum ada saldo tersimpan. Saldo akan muncul dari notifikasi atau email yang mencantumkan sisa saldo.",
                intent = intent,
            )
        }
        val latestPerAccount = balances.groupBy { it.accountId }.mapValues { it.value.first() }
        val perCurrency = latestPerAccount.values
            .groupBy { it.currency }
            .mapValues { entry -> entry.value.fold(BigDecimal.ZERO) { acc, b -> acc + (b.balance.toBigDecimalOrNull() ?: BigDecimal.ZERO) } }
        val figures = perCurrency.map { (currency, total) -> AssistantFigure("Saldo " + currency, total, currency) }
        val text = "Saldo saat ini: " + perCurrency.entries.joinToString(", ") { (currency, total) ->
            CurrencyFormatter.format(total, currency)
        } + "."
        return AssistantAnswer(text, figures, intent = intent)
    }

    private fun totalsByCurrency(transactions: List<TransactionEntity>): Map<String, BigDecimal> =
        transactions.groupBy { it.currency }.mapValues { entry ->
            entry.value.fold(BigDecimal.ZERO) { acc, t ->
                acc + (t.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO)
            }
        }
}
