@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.catetin.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catetin.app.data.db.SourceEventEntity
import com.catetin.app.ui.MainViewModel
import com.catetin.app.ui.components.CatetCard
import com.catetin.app.ui.components.CategoryAvatar
import com.catetin.app.ui.components.StatusPill
import com.catetin.app.ui.theme.Pine
import com.catetin.app.util.CurrencyFormatter
import com.catetin.app.util.Dates

@Composable
fun TransactionDetailScreen(
    mainViewModel: MainViewModel,
    transactionId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onRule: (String) -> Unit,
    viewModel: TransactionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(transactionId) {
        viewModel.clearError()
        viewModel.load(transactionId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detail transaksi") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "Ubah") }
                    IconButton(onClick = { confirmDelete = true }, enabled = !state.deleting) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Hapus")
                    }
                },
            )
        },
    ) { padding ->
        val transaction = state.transaction
        if (transaction == null) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(if (state.loading) "Memuat…" else "Transaksi tidak ditemukan")
            }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CategoryAvatar(transaction.category, transaction.direction, size = 64)
                Spacer(Modifier.height(12.dp))
                Text(
                    (if (transaction.direction == "EXPENSE") "−" else if (transaction.direction == "TRANSFER") "" else "+") +
                        CurrencyFormatter.format(
                            transaction.amount.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO,
                            transaction.currency,
                        ),
                    style = MaterialTheme.typography.displaySmall,
                    color = if (transaction.direction == "INCOME") Color(0xFF2E7D5B) else MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    transaction.merchant ?: transaction.category,
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(transaction.category)
                    if (transaction.status == "NEEDS_REVIEW") StatusPill("Perlu ditinjau", positive = false)
                }
            }

            CatetCard {
                DetailRow("Tanggal", Dates.formatDateTime(transaction.occurredAt))
                DetailRow("Arah", when (transaction.direction) {
                    "EXPENSE" -> "Pengeluaran"
                    "INCOME" -> "Pemasukan"
                    else -> "Transfer"
                })
                DetailRow("Institusi", transaction.institution ?: "—")
                DetailRow("Referensi", transaction.reference ?: "—")
                DetailRow("Sumber", if (state.sources.size > 1) state.sources.size.toString() + " sumber digabung" else "1 sumber")
            }

            if (state.error != null) {
                Text(
                    state.error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (state.sources.isNotEmpty()) {
                Text("Sumber data", style = MaterialTheme.typography.titleMedium)
                state.sources.forEach { source ->
                    SourceCard(source)
                }
            }

            val merchant = transaction.merchant
            if (!merchant.isNullOrBlank()) {
                OutlinedButton(
                    onClick = { onRule(merchant) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Buat aturan dari transaksi ini")
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus transaksi?") },
            text = { Text("Transaksi akan dihapus dari catatan. Sumber datanya tetap tersimpan untuk riwayat.") },
            confirmButton = {
                Button(onClick = {
                    confirmDelete = false
                    viewModel.delete(onBack)
                }, enabled = !state.deleting) { Text(if (state.deleting) "Menghapus…" else "Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Batal") }
            },
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SourceCard(source: SourceEventEntity) {
    val icon = when (source.type) {
        "EMAIL" -> Icons.Outlined.Mail
        "NOTIFICATION" -> Icons.Outlined.Notifications
        else -> Icons.Outlined.Smartphone
    }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(8.dp).size(18.dp), tint = Pine)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when (source.type) {
                        "NOTIFICATION" -> "Notifikasi"
                        "EMAIL" -> "Email"
                        "MANUAL" -> "Input manual"
                        else -> source.type
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    source.sourceApp + " · " + Dates.formatDateTime(source.receivedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(source.parseStatus, style = MaterialTheme.typography.labelSmall)
        }
    }
}
