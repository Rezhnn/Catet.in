@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.catetin.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catetin.app.data.db.TransactionEntity
import com.catetin.app.ui.MainViewModel
import com.catetin.app.ui.components.CatetCard
import com.catetin.app.ui.components.EmptyState
import com.catetin.app.ui.components.StatusPill
import com.catetin.app.util.CurrencyFormatter
import com.catetin.app.util.Dates
import java.math.BigDecimal

@Composable
fun ReviewScreen(
    mainViewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val items by viewModel.state.collectAsStateWithLifecycle()
    var unparsedItem by remember { mutableStateOf<ReviewUiItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tinjauan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    title = "Tidak ada yang perlu ditinjau",
                    body = "Transaksi yang yakin akan langsung disimpan. Kandidat duplikat muncul di sini agar tidak ada penggabungan diam-diam.",
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.review.id }) { item ->
                when (item.review.kind) {
                    "UNPARSED" -> UnparsedCard(
                        item,
                        onCreate = { unparsedItem = item },
                        onDismiss = { viewModel.dismiss(item.review.id) },
                    )
                    else -> DuplicateCard(
                        item,
                        onMerge = { viewModel.merge(item.review.id) },
                        onKeepBoth = { viewModel.keepBoth(item.review.id) },
                        onDismiss = { viewModel.dismiss(item.review.id) },
                        onOpen = { onOpenTransaction(it) },
                    )
                }
            }
        }
    }

    unparsedItem?.let { item ->
        CreateFromSourceDialog(
            item = item,
            onDismiss = { unparsedItem = null },
            onCreate = { amount, direction, merchant, category ->
                viewModel.createFromUnparsed(item, amount, direction, merchant, category) {
                    unparsedItem = null
                }
            },
        )
    }
}

@Composable
private fun DuplicateCard(
    item: ReviewUiItem,
    onMerge: () -> Unit,
    onKeepBoth: () -> Unit,
    onDismiss: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    CatetCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (item.review.kind) {
                    "LOW_CONFIDENCE" -> "Keyakinan rendah"
                    else -> "Kemungkinan duplikat"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            StatusPill("Perlu ditinjau", positive = false)
        }
        Spacer(Modifier.height(6.dp))
        Text(item.review.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        item.transaction?.let { transaction ->
            TransactionMini(transaction, "Transaksi baru", onClick = { onOpen(transaction.id) })
        }
        item.candidate?.let { candidate ->
            TransactionMini(candidate, "Kandidat lama", onClick = { onOpen(candidate.id) })
        }
        Spacer(Modifier.height(10.dp))
        if (item.review.kind == "DUPLICATE_CANDIDATE") {
            Button(onClick = onMerge, shape = RoundedCornerShape(14.dp)) { Text("Transaksi sama — gabungkan") }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = onKeepBoth, shape = RoundedCornerShape(14.dp)) { Text("Berbeda — simpan keduanya") }
            TextButton(onClick = onDismiss) { Text("Abaikan") }
        } else {
            OutlinedButton(onClick = onKeepBoth, shape = RoundedCornerShape(14.dp)) { Text("Tandai selesai") }
        }
    }
}

@Composable
private fun TransactionMini(transaction: TransactionEntity, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                transaction.merchant ?: transaction.category,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                Dates.formatDateTime(transaction.occurredAt) + " · " + transaction.institution.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            CurrencyFormatter.format(transaction.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO, transaction.currency),
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
private fun UnparsedCard(item: ReviewUiItem, onCreate: () -> Unit, onDismiss: () -> Unit) {
    CatetCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Belum terbaca", style = MaterialTheme.typography.titleMedium)
            StatusPill(item.source?.sourceApp ?: "Sumber", positive = false)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.source?.rawText?.take(600) ?: item.review.reason,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCreate, shape = RoundedCornerShape(14.dp)) { Text("Buat transaksi") }
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(14.dp)) { Text("Abaikan") }
        }
    }
}

@Composable
private fun CreateFromSourceDialog(
    item: ReviewUiItem,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf("EXPENSE") }
    var category by remember { mutableStateOf("Lainnya") }
    val categories = com.catetin.parser.parse.CategoryGuesser.defaults.map { it.first }
    var categoryMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buat transaksi dari sumber") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("Nominal") },
                    prefix = { Text("Rp ") },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = direction == "EXPENSE", onClick = { direction = "EXPENSE" }, label = { Text("Keluar") })
                    FilterChip(selected = direction == "INCOME", onClick = { direction = "INCOME" }, label = { Text("Masuk") })
                }
                OutlinedTextField(value = merchant, onValueChange = { merchant = it }, label = { Text("Merchant") }, singleLine = true)
                Column {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Kategori") },
                        trailingIcon = { TextButton(onClick = { categoryMenu = true }) { Text("Pilih") } },
                    )
                    androidx.compose.material3.DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        categories.forEach { item ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(item) },
                                onClick = { category = item; categoryMenu = false },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(amount, direction, merchant, category) }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
