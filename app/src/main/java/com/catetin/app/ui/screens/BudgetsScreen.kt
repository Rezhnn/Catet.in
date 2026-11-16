package com.catetin.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catetin.app.data.db.BudgetEntity
import com.catetin.app.ui.MainViewModel
import com.catetin.app.ui.components.CatetCard
import com.catetin.app.ui.components.RingProgress
import com.catetin.app.ui.theme.Pine
import com.catetin.app.util.CurrencyFormatter
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Composable
fun BudgetsScreen(
    mainViewModel: MainViewModel,
    onAdd: () -> Unit,
    viewModel: BudgetsViewModel = hiltViewModel(),
) {
    val budgets by mainViewModel.budgets.collectAsStateWithLifecycle()
    val transactions by mainViewModel.monthTransactions.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    val totalLimit = budgets.fold(BigDecimal.ZERO) { acc, b -> acc + (b.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO) }
    val totalSpent = transactions.filter { it.direction == "EXPENSE" }.fold(BigDecimal.ZERO) { acc, t ->
        acc + (t.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO)
    }
    val fraction = if (totalLimit > BigDecimal.ZERO) (totalSpent.toFloat() / totalLimit.toFloat()) else 0f
    val daysLeft = (LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()).dayOfMonth - LocalDate.now().dayOfMonth + 1).coerceAtLeast(1)
    val daily = if (totalLimit > totalSpent) (totalLimit - totalSpent) / BigDecimal(daysLeft) else BigDecimal.ZERO

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("Anggaran", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Batas, target, dan estimasi bulan ini",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                CatetCard(Modifier.padding(top = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RingProgress(fraction = fraction, label = (fraction * 100).toInt().coerceAtLeast(0).toString() + "%")
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Total anggaran bulan ini", style = MaterialTheme.typography.labelLarge)
                            Text(
                                CurrencyFormatter.format(totalLimit),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                "Terpakai " + CurrencyFormatter.format(totalSpent) + " · sisa " +
                                    CurrencyFormatter.format((totalLimit - totalSpent).max(BigDecimal.ZERO)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Boleh belanja ± " + CurrencyFormatter.format(daily) + "/hari",
                                style = MaterialTheme.typography.bodySmall,
                                color = Pine,
                            )
                        }
                    }
                }
            }

            if (budgets.isEmpty()) {
                item {
                    CatetCard {
                        Text("Belum ada anggaran", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Buat batas pengeluaran bulanan supaya kamu tahu sisa uang yang aman dipakai.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            items(budgets, key = { it.id }) { budget ->
                BudgetRow(budget, transactions, onDelete = { viewModel.deleteBudget(budget) })
            }
        }

        androidx.compose.material3.ExtendedFloatingActionButton(
            onClick = { showDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Anggaran") },
            containerColor = Pine,
            contentColor = androidx.compose.ui.graphics.Color.White,
        )
    }

    if (showDialog) {
        AddBudgetDialog(
            onDismiss = { showDialog = false },
            onSave = { name, amount, category, type ->
                viewModel.addBudget(name, amount, category, type) { showDialog = false }
            },
        )
    }
}

@Composable
private fun BudgetRow(
    budget: BudgetEntity,
    transactions: List<com.catetin.app.data.db.TransactionEntity>,
    onDelete: () -> Unit,
) {
    val spent = transactions.filter {
        it.direction == "EXPENSE" && (budget.category == null || it.category == budget.category)
    }.fold(BigDecimal.ZERO) { acc, t -> acc + (t.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO) }
    val limit = budget.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val fraction = if (limit > BigDecimal.ZERO) (spent.toFloat() / limit.toFloat()) else 0f
    CatetCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RingProgress(
                fraction = fraction,
                size = 64,
                stroke = 7,
                color = if (fraction > 1f) MaterialTheme.colorScheme.error else Pine,
                label = (fraction * 100).toInt().coerceAtLeast(0).toString() + "%",
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(budget.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    (budget.category ?: "Semua kategori") + " · " + when (budget.type) {
                        "TARGET" -> "Target"
                        "EXPECTED" -> "Estimasi"
                        else -> "Batas"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    CurrencyFormatter.format(spent) + " / " + CurrencyFormatter.format(limit),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Hapus anggaran")
            }
        }
    }
}

@Composable
private fun AddBudgetDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String?, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var type by remember { mutableStateOf("LIMIT") }
    var typeMenu by remember { mutableStateOf(false) }
    val categories = com.catetin.parser.parse.CategoryGuesser.defaults.map { it.first }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buat anggaran") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama anggaran") }, singleLine = true)
                OutlinedTextField(value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, label = { Text("Nominal") }, prefix = { Text("Rp ") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = type == "LIMIT", onClick = { type = "LIMIT" }, label = { Text("Batas") })
                    FilterChip(selected = type == "TARGET", onClick = { type = "TARGET" }, label = { Text("Target") })
                    FilterChip(selected = type == "EXPECTED", onClick = { type = "EXPECTED" }, label = { Text("Estimasi") })
                }
                Box {
                    OutlinedTextField(
                        value = category ?: "Semua kategori",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Kategori") },
                        trailingIcon = { TextButton(onClick = { typeMenu = true }) { Text("Pilih") } },
                    )
                    DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                        DropdownMenuItem(text = { Text("Semua kategori") }, onClick = { category = null; typeMenu = false })
                        categories.forEach { item ->
                            DropdownMenuItem(text = { Text(item) }, onClick = { category = item; typeMenu = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, amount, category, type) }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
