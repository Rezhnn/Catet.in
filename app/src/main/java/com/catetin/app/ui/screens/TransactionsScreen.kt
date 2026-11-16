package com.catetin.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catetin.app.data.db.TransactionEntity
import com.catetin.app.ui.MainViewModel
import com.catetin.app.ui.ListKeys
import com.catetin.app.ui.components.TransactionRow
import com.catetin.app.ui.theme.Pine
import com.catetin.app.util.CurrencyFormatter
import com.catetin.app.util.Dates
import java.math.BigDecimal

@Composable
fun TransactionsScreen(
    mainViewModel: MainViewModel,
    onAdd: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val selectedType by viewModel.type.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.category.collectAsStateWithLifecycle()
    val categories by mainViewModel.categories.collectAsStateWithLifecycle()
    var categoryMenu by remember { mutableStateOf(false) }
    val groups = transactions.groupBy { Dates.dayHeader(it.occurredAt) }
    val totals = transactions.filter { it.direction == "EXPENSE" }.groupBy { it.currency }
        .mapValues { entry -> entry.value.fold(BigDecimal.ZERO) { acc, t -> acc + (t.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO) } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text("Transaksi", style = MaterialTheme.typography.headlineMedium)
                Text(
                    totals.entries.joinToString(" · ") { (currency, total) -> "Keluar " + CurrencyFormatter.format(total, currency) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Cari merchant, kategori, catatan…") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(Icons.Outlined.Clear, contentDescription = "Hapus")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = selectedType == null, onClick = { viewModel.setType(null) }, label = { Text("Semua") })
                    FilterChip(selected = selectedType == "EXPENSE", onClick = { viewModel.setType("EXPENSE") }, label = { Text("Keluar") })
                    FilterChip(selected = selectedType == "INCOME", onClick = { viewModel.setType("INCOME") }, label = { Text("Masuk") })
                    Box {
                        FilterChip(
                            selected = selectedCategory != null,
                            onClick = { categoryMenu = true },
                            label = { Text(selectedCategory ?: "Kategori") },
                        )
                        DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                            DropdownMenuItem(text = { Text("Semua kategori") }, onClick = {
                                viewModel.setCategory(null)
                                categoryMenu = false
                            })
                            categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    onClick = {
                                        viewModel.setCategory(category.name)
                                        categoryMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (transactions.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Belum ada transaksi", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Transaksi dari notifikasi, email, atau input manual akan muncul di sini.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    groups.forEach { (day, dayTransactions) ->
                        item(key = ListKeys.dayHeader(day)) {
                            Text(
                                day,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                            )
                        }
                        items(dayTransactions, key = { ListKeys.transaction(it.id) }) { transaction ->
                            TransactionRow(transaction, onClick = { onOpenTransaction(transaction.id) })
                        }
                    }
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
