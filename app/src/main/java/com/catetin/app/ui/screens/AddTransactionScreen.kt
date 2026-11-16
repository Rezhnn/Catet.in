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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catetin.app.ui.MainViewModel
import com.catetin.app.util.Dates
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    mainViewModel: MainViewModel,
    transactionId: Long?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddTransactionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by mainViewModel.categories.collectAsStateWithLifecycle()
    var categoryMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(transactionId) {
        if (transactionId != null) viewModel.load(transactionId) else viewModel.reset()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (transactionId == null) "Catat transaksi" else "Ubah transaksi") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.direction == "EXPENSE", onClick = { viewModel.setDirection("EXPENSE") }, label = { Text("Pengeluaran") })
                FilterChip(selected = state.direction == "INCOME", onClick = { viewModel.setDirection("INCOME") }, label = { Text("Pemasukan") })
                FilterChip(selected = state.direction == "TRANSFER", onClick = { viewModel.setDirection("TRANSFER") }, label = { Text("Transfer") })
            }

            OutlinedTextField(
                value = state.amount,
                onValueChange = viewModel::setAmount,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nominal") },
                prefix = { Text("Rp ") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                textStyle = MaterialTheme.typography.headlineMedium,
            )

            OutlinedTextField(
                value = state.merchant,
                onValueChange = viewModel::setMerchant,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Merchant / keterangan") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )

            Column {
                OutlinedTextField(
                    value = state.category,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Kategori") },
                    trailingIcon = {
                        TextButton(onClick = { categoryMenu = true }) { Text("Pilih") }
                    },
                    shape = RoundedCornerShape(16.dp),
                )
                DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
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

            OutlinedTextField(
                value = Dates.formatDate(state.occurredAt),
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Tanggal") },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = "Pilih tanggal")
                    }
                },
                shape = RoundedCornerShape(16.dp),
            )

            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::setNote,
                modifier = Modifier.fillMaxWidth().height(110.dp),
                label = { Text("Catatan (opsional)") },
                shape = RoundedCornerShape(16.dp),
            )

            if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = { viewModel.save(onSaved) },
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(if (state.saving) "Menyimpan…" else "Simpan transaksi")
            }
        }
    }

    if (showDatePicker) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = state.occurredAt,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { millis ->
                        viewModel.setDate(
                            Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate(),
                        )
                    }
                    showDatePicker = false
                }) { Text("Pilih") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Batal") }
            },
        ) {
            DatePicker(state = picker)
        }
    }
}
