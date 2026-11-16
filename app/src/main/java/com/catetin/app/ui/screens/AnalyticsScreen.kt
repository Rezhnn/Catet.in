package com.catetin.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catetin.app.ui.MainViewModel
import com.catetin.app.ui.ListKeys
import com.catetin.app.ui.components.CatetCard
import com.catetin.app.ui.components.CategoryBar
import com.catetin.app.ui.components.MiniBarChart
import com.catetin.app.ui.components.SectionTitle
import com.catetin.app.util.CurrencyFormatter
import com.catetin.app.util.PeriodPreset
import java.math.BigDecimal
import java.util.Locale

@Composable
fun AnalyticsScreen(
    mainViewModel: MainViewModel,
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()
    val trend by viewModel.trend.collectAsStateWithLifecycle()
    val selectedCurrency by viewModel.currency.collectAsStateWithLifecycle()
    val recurring by viewModel.recurring.collectAsStateWithLifecycle()
    val currencies = transactions.map { it.currency }.distinct().sorted()
    LaunchedEffect(currencies) {
        if (currencies.isNotEmpty() && selectedCurrency !in currencies) {
            viewModel.setCurrency(currencies.first())
        }
    }
    val expenses = transactions.filter { it.direction == "EXPENSE" && it.currency == selectedCurrency }
    val total = expenses.fold(BigDecimal.ZERO) { acc, t -> acc + (t.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO) }
    val byCategory = expenses.groupBy { it.category }.mapValues { entry ->
        entry.value.fold(BigDecimal.ZERO) { acc, t -> acc + (t.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO) }
    }.toList().sortedByDescending { it.second }.take(6)
    val byMerchant = expenses.filter { !it.merchant.isNullOrBlank() }.groupBy { it.merchant!! }.mapValues { entry ->
        entry.value.fold(BigDecimal.ZERO) { acc, t -> acc + (t.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO) }
    }.toList().sortedByDescending { it.second }.take(5)
    val colors = listOf(Color(0xFFD95F4B), Color(0xFF3C7DA6), Color(0xFFE0A23A), Color(0xFF4E8B57), Color(0xFF7D64A8), Color(0xFFC46A8A))

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Analitik", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Pola pengeluaran, tanpa mencampur mata uang",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                listOf(PeriodPreset.THIS_MONTH, PeriodPreset.LAST_MONTH, PeriodPreset.LAST_7_DAYS, PeriodPreset.THIS_YEAR).forEach { preset ->
                    FilterChip(
                        selected = period == preset,
                        onClick = { viewModel.setPeriod(preset) },
                        label = {
                            Text(
                                when (preset) {
                                    PeriodPreset.THIS_MONTH -> "Bulan ini"
                                    PeriodPreset.LAST_MONTH -> "Bulan lalu"
                                    PeriodPreset.LAST_7_DAYS -> "7 hari"
                                    else -> "Tahun ini"
                                },
                            )
                        },
                    )
                }
                }
            }
        }
        if (currencies.size > 1) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(currencies) { currency ->
                        FilterChip(
                            selected = selectedCurrency == currency,
                            onClick = { viewModel.setCurrency(currency) },
                            label = { Text(currency) },
                        )
                    }
                }
            }
        }
        item {
            CatetCard {
                Text("Total pengeluaran ($selectedCurrency)", style = MaterialTheme.typography.labelLarge)
                Text(CurrencyFormatter.format(total), style = MaterialTheme.typography.displaySmall)
                Text(
                    expenses.size.toString() + " transaksi",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            CatetCard {
                Text("Tren 6 bulan · $selectedCurrency", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(14.dp))
                MiniBarChart(
                    values = trend.map { it.total.toFloat() },
                    labels = trend.map { it.label },
                )
            }
        }
        item { SectionTitle("Per kategori") }
        item {
            CatetCard {
                if (byCategory.isEmpty()) {
                    Text("Belum ada pengeluaran pada periode ini.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    val max = byCategory.first().second.toFloat().coerceAtLeast(0.01f)
                    byCategory.forEachIndexed { index, (category, amount) ->
                        CategoryBar(
                            label = category,
                            amount = CurrencyFormatter.format(amount),
                            fraction = amount.toFloat() / max,
                            color = colors[index % colors.size],
                        )
                    }
                }
            }
        }
        item { SectionTitle("Merchant teratas") }
        if (byMerchant.isEmpty()) {
            item {
                CatetCard {
                    Text("Belum ada data merchant.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            items(byMerchant, key = { ListKeys.merchant(it.first) }) { (merchant, amount) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(merchant, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(CurrencyFormatter.format(amount), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        if (recurring.isNotEmpty()) {
            item { SectionTitle("Langganan / tagihan berulang") }
            items(recurring, key = { ListKeys.recurring(it.merchant, it.currency) }) { item ->
                CatetCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(item.merchant, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${item.occurrences} kali · terakhir ${item.lastDate.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("id-ID")))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(CurrencyFormatter.format(item.amount, item.currency), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}
