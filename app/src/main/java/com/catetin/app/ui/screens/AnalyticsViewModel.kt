package com.catetin.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catetin.app.data.db.TransactionEntity
import com.catetin.app.data.repo.FinanceRepository
import com.catetin.app.util.Dates
import com.catetin.app.util.PeriodPreset
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TrendPoint(val label: String, val total: BigDecimal)
data class RecurringExpense(
    val merchant: String,
    val amount: BigDecimal,
    val currency: String,
    val occurrences: Int,
    val lastDate: LocalDate,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val repository: FinanceRepository,
) : ViewModel() {

    val period = MutableStateFlow(PeriodPreset.THIS_MONTH)
    val currency = MutableStateFlow("IDR")
    val transactions: StateFlow<List<TransactionEntity>> = period
        .flatMapLatest { repository.observeTransactions(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val trend = MutableStateFlow<List<TrendPoint>>(emptyList())
    val recurring = MutableStateFlow<List<RecurringExpense>>(emptyList())

    init {
        refreshTrend()
        detectRecurring()
    }

    fun setPeriod(value: PeriodPreset) {
        period.value = value
        refreshTrend()
    }

    fun setCurrency(value: String) {
        currency.value = value
        refreshTrend()
    }

    private fun refreshTrend() {
        viewModelScope.launch {
            val today = LocalDate.now(ZoneId.systemDefault())
            val formatter = DateTimeFormatter.ofPattern("MMM", Locale.forLanguageTag("id-ID"))
            val points = (5 downTo 0).map { offset ->
                val month = today.minusMonths(offset.toLong())
                val start = month.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val end = month.plusMonths(1).withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
                val total = repository.getTransactionsBetween(start, end)
                    .filter { it.direction == "EXPENSE" && it.currency == currency.value }
                    .fold(BigDecimal.ZERO) { acc, t -> acc + (t.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO) }
                TrendPoint(month.format(formatter), total)
            }
            trend.value = points
        }
    }

    private fun detectRecurring() {
        viewModelScope.launch {
            val end = System.currentTimeMillis()
            val start = end - 210L * 24L * 60L * 60L * 1_000L
            val grouped = repository.getTransactionsBetween(start, end)
                .filter { it.direction == "EXPENSE" && !it.merchant.isNullOrBlank() }
                .groupBy { it.merchant!!.trim().lowercase(Locale.ROOT) to it.currency }
            recurring.value = grouped.values.mapNotNull { items ->
                val ordered = items.sortedBy { it.occurredAt }
                if (ordered.size < 2) return@mapNotNull null
                val dates = ordered.map { Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate() }
                val intervals = dates.zipWithNext { first, second -> java.time.temporal.ChronoUnit.DAYS.between(first, second) }
                val regular = intervals.size >= 1 && intervals.dropLast(1).all { it in 25..35 } || intervals.all { it in 25..35 }
                val amounts = ordered.mapNotNull { it.amount.toBigDecimalOrNull() }
                if (!regular || amounts.size < 2) return@mapNotNull null
                val average = amounts.reduce { a, b -> a + b } / BigDecimal(amounts.size)
                val consistent = amounts.all { amount ->
                    average.compareTo(BigDecimal.ZERO) > 0 &&
                        amount.subtract(average).abs() <= average.multiply(BigDecimal("0.10"))
                }
                if (!consistent) return@mapNotNull null
                RecurringExpense(
                    merchant = ordered.last().merchant ?: return@mapNotNull null,
                    amount = average,
                    currency = ordered.first().currency,
                    occurrences = ordered.size,
                    lastDate = dates.last(),
                )
            }.sortedByDescending { it.lastDate }
        }
    }
}
