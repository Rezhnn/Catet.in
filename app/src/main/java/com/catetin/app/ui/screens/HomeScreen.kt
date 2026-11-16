package com.catetin.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catetin.app.data.db.TransactionEntity
import com.catetin.app.ui.MainViewModel
import com.catetin.app.ui.components.CatetCard
import com.catetin.app.ui.components.HeroCard
import com.catetin.app.ui.components.LinearProgress
import com.catetin.app.ui.components.SectionTitle
import com.catetin.app.ui.components.TransactionRow
import com.catetin.app.ui.theme.Amber
import com.catetin.app.ui.theme.Pine
import com.catetin.app.ui.util.rememberNotificationAccessGranted
import com.catetin.app.util.CurrencyFormatter
import com.catetin.app.util.Dates
import java.math.BigDecimal

@Composable
fun HomeScreen(
    mainViewModel: MainViewModel,
    onOpenSettings: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenReview: () -> Unit,
    onAdd: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onSeeAll: () -> Unit,
) {
    val transactions by mainViewModel.monthTransactions.collectAsStateWithLifecycle()
    val recent by mainViewModel.recentTransactions.collectAsStateWithLifecycle()
    val budgets by mainViewModel.budgets.collectAsStateWithLifecycle()
    val reviewCount by mainViewModel.pendingReviewCount.collectAsStateWithLifecycle()
    val granted = rememberNotificationAccessGranted()

    val expenses = remember(transactions) { transactions.filter { it.direction == "EXPENSE" } }
    val incomes = remember(transactions) { transactions.filter { it.direction == "INCOME" } }
    val expenseTotals = remember(expenses) { totals(expenses) }
    val incomeTotals = remember(incomes) { totals(incomes) }
    val primaryCurrency = expenseTotals.keys.firstOrNull() ?: incomeTotals.keys.firstOrNull() ?: "IDR"
    val primaryExpense = expenseTotals[primaryCurrency] ?: BigDecimal.ZERO
    val primaryIncome = incomeTotals[primaryCurrency] ?: BigDecimal.ZERO

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 12.dp,
                bottom = 100.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Catet.in", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        Text(
                            Dates.monthLabel(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Pengaturan")
                    }
                }
            }

            if (!granted) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenSources),
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.NotificationsOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Akses notifikasi mati", style = MaterialTheme.typography.titleSmall)
                                Text("Aktifkan agar transaksi tercatat otomatis.", style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                        }
                    }
                }
            }

            item {
                HeroCard(
                    title = "Pengeluaran bulan ini",
                    amount = CurrencyFormatter.format(primaryExpense, primaryCurrency),
                    caption = if (expenseTotals.size > 1) {
                        "Ada " + expenseTotals.size + " mata uang; total ditampilkan terpisah."
                    } else {
                        expenses.size.toString() + " transaksi · pemasukan " + CurrencyFormatter.format(primaryIncome, primaryCurrency)
                    },
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MiniStat("Pemasukan", CurrencyFormatter.format(primaryIncome, primaryCurrency), Color(0xFF2E7D5B), Modifier.weight(1f))
                    MiniStat("Transaksi", transactions.size.toString(), Pine, Modifier.weight(1f))
                }
            }

            if (reviewCount > 0) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Amber.copy(alpha = 0.14f),
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clickable(onClick = onOpenReview),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.RateReview, contentDescription = null, tint = Amber)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Perlu ditinjau (" + reviewCount + ")",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                        }
                    }
                }
            }

            if (budgets.isNotEmpty()) {
                item { SectionTitle("Anggaran", "Kelola", onOpenSettings) }
                items(budgets.take(2), key = { it.id }) { budget ->
                    val spent = transactions.filter {
                        it.currency == budget.currency &&
                            it.direction == "EXPENSE" &&
                            (budget.category == null || it.category == budget.category)
                    }.fold(BigDecimal.ZERO) { acc, t -> acc + (t.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO) }
                    val limit = budget.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    val fraction = if (limit > BigDecimal.ZERO) (spent.toFloat() / limit.toFloat()) else 0f
                    CatetCard(Modifier.padding(vertical = 4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(budget.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    CurrencyFormatter.format(spent, budget.currency) + " dari " + CurrencyFormatter.format(limit, budget.currency),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                (fraction * 100).toInt().coerceAtLeast(0).toString() + "%",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (fraction > 1f) MaterialTheme.colorScheme.error else Pine,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        LinearProgress(
                            value = fraction,
                            color = if (fraction > 1f) MaterialTheme.colorScheme.error else Pine,
                        )
                    }
                }
            }

            item {
                SectionTitle("Transaksi terbaru", "Lihat semua", onSeeAll)
            }
            if (recent.isEmpty()) {
                item {
                    CatetCard {
                        Text("Menunggu transaksi pertamamu", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Coba lakukan pembayaran kecil dengan e-wallet atau tambah transaksi manual.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(recent.take(5), key = { it.id }) { transaction ->
                    TransactionRow(transaction, onClick = { onOpenTransaction(transaction.id) })
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onAdd,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Catat") },
            containerColor = Pine,
            contentColor = Color.White,
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = color)
        }
    }
}

private fun totals(transactions: List<TransactionEntity>): Map<String, BigDecimal> =
    transactions.groupBy { it.currency }.mapValues { entry ->
        entry.value.fold(BigDecimal.ZERO) { acc, t -> acc + (t.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO) }
    }
