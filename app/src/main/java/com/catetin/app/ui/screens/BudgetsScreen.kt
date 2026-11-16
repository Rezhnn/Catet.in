package com.catetin.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catetin.app.data.db.BudgetEntity
import com.catetin.app.domain.budget.BudgetMath
import com.catetin.app.ui.ListKeys
import com.catetin.app.ui.MainViewModel
import com.catetin.app.ui.components.CatetCard
import com.catetin.app.ui.components.RingProgress
import com.catetin.app.ui.theme.Amber
import com.catetin.app.ui.theme.Pine
import com.catetin.app.ui.theme.Sky
import com.catetin.app.util.CurrencyFormatter
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

private const val TAB_BUDGETS = 0
private const val TAB_TARGETS = 1

@Composable
fun BudgetsScreen(
    mainViewModel: MainViewModel,
    onAdd: () -> Unit,
    viewModel: BudgetsViewModel = hiltViewModel(),
) {
    val allBudgets by mainViewModel.budgets.collectAsStateWithLifecycle()
    val transactions by mainViewModel.monthTransactions.collectAsStateWithLifecycle()
    val categoryEntities by mainViewModel.categories.collectAsStateWithLifecycle()
    val editor by viewModel.editorState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_BUDGETS) }
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<BudgetEntity?>(null) }
    var editorType by remember { mutableStateOf(BudgetMath.LIMIT) }
    var deleting by remember { mutableStateOf<BudgetEntity?>(null) }

    val budgets = remember(allBudgets) { allBudgets.filter { it.type != BudgetMath.TARGET } }
    val targets = remember(allBudgets) { allBudgets.filter { it.type == BudgetMath.TARGET } }
    val categoryNames = remember(categoryEntities) { categoryEntities.filter { !it.isIncome }.map { it.name } }
    val monthlyExpense = remember(transactions) { BudgetMath.spending(transactions, currency = "IDR") }
    val monthlySavings = remember(transactions) { BudgetMath.savings(transactions, currency = "IDR") }
    val totalLimit = remember(budgets) { BudgetMath.total(budgets, BudgetMath.LIMIT) }
    val totalExpected = remember(budgets) { BudgetMath.total(budgets, BudgetMath.EXPECTED) }
    val totalTarget = remember(targets) { BudgetMath.total(targets, BudgetMath.TARGET) }
    val daysLeft = remember {
        (LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()).dayOfMonth - LocalDate.now().dayOfMonth + 1)
            .coerceAtLeast(1)
    }
    val remaining = (totalLimit - monthlyExpense).max(BigDecimal.ZERO)
    val daily = if (totalLimit > monthlyExpense) remaining / BigDecimal(daysLeft) else BigDecimal.ZERO

    LaunchedEffect(selectedTab) { viewModel.clearError() }

    fun openNew() {
        editing = null
        editorType = if (selectedTab == TAB_TARGETS) BudgetMath.TARGET else BudgetMath.LIMIT
        viewModel.clearError()
        showEditor = true
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text(
                    if (selectedTab == TAB_TARGETS) "Target Capaian" else "Anggaran",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    if (selectedTab == TAB_TARGETS) {
                        "Tetapkan hasil yang ingin dicapai bulan ini"
                    } else {
                        "Atur pagu belanja dan perkiraan pengeluaran"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Tab(
                        selected = selectedTab == TAB_BUDGETS,
                        onClick = { selectedTab = TAB_BUDGETS },
                        text = { Text("Batas & Estimasi") },
                    )
                    Tab(
                        selected = selectedTab == TAB_TARGETS,
                        onClick = { selectedTab = TAB_TARGETS },
                        text = { Text("Target Capaian") },
                    )
                }
            }

            if (selectedTab == TAB_BUDGETS) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { BudgetExplanationCard() }
                    item {
                        BudgetSummaryCard(
                            totalLimit = totalLimit,
                            totalExpected = totalExpected,
                            spent = monthlyExpense,
                            daily = daily,
                        )
                    }
                    if (budgets.isEmpty()) {
                        item {
                            CatetCard {
                                Text("Belum ada batas atau estimasi", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Buat batas agar Catet.in bisa mengingatkan saat pengeluaran mendekati pagu. Tambahkan estimasi bila ingin membandingkan rencana dengan realisasi.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        items(budgets, key = { ListKeys.budget(it.id) }) { budget ->
                            BudgetProgressCard(
                                budget = budget,
                                spent = BudgetMath.budgetSpent(budget, transactions),
                                onEdit = {
                                    editing = budget
                                    editorType = budget.type
                                    viewModel.clearError()
                                    showEditor = true
                                },
                                onDelete = { deleting = budget },
                            )
                        }
                    }
                    item {
                        OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                            Text("Catat transaksi pengeluaran")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { TargetExplanationCard() }
                    item { TargetSummaryCard(saved = monthlySavings, totalTarget = totalTarget) }
                    if (targets.isEmpty()) {
                        item {
                            CatetCard {
                                Icon(Icons.Outlined.Flag, contentDescription = null, tint = Amber)
                                Spacer(Modifier.height(10.dp))
                                Text("Belum ada target capaian", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Contoh: target dana darurat Rp1.000.000. Kemajuan dihitung dari pemasukan dikurangi pengeluaran bulan berjalan.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        items(targets, key = { ListKeys.budget(it.id) }) { target ->
                            TargetProgressCard(
                                target = target,
                                achieved = BudgetMath.targetAchieved(target, transactions),
                                onEdit = {
                                    editing = target
                                    editorType = BudgetMath.TARGET
                                    viewModel.clearError()
                                    showEditor = true
                                },
                                onDelete = { deleting = target },
                            )
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = ::openNew,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(if (selectedTab == TAB_TARGETS) "Target" else "Rencana") },
            containerColor = Pine,
            contentColor = Color.White,
        )
    }

    if (showEditor) {
        BudgetEditorDialog(
            existing = editing,
            initialType = editorType,
            saving = editor.saving,
            error = editor.error,
            categories = categoryNames,
            onValueChanged = viewModel::clearError,
            onDismiss = { if (!editor.saving) showEditor = false },
            onSave = { name, amount, category, type ->
                viewModel.saveBudget(editing, name, amount, category, type) { showEditor = false }
            },
        )
    }

    deleting?.let { budget ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Hapus ${BudgetMath.typeLabel(budget.type).lowercase()}?") },
            text = { Text("“${budget.name}” akan dihapus. Transaksi yang sudah tercatat tidak ikut terhapus.") },
            confirmButton = {
                Button(onClick = {
                    val item = budget
                    deleting = null
                    viewModel.deleteBudget(item)
                }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Batal") } },
        )
    }
}

@Composable
private fun BudgetExplanationCard() {
    CatetCard(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Batas dan estimasi, singkatnya", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                ExplanationLine(
                    title = "Batas",
                    body = "Pagu maksimum belanja. Aplikasi menandai rencana yang terlampaui.",
                    color = Pine,
                )
                Spacer(Modifier.height(8.dp))
                ExplanationLine(
                    title = "Estimasi",
                    body = "Perkiraan belanja untuk perencanaan; bukan batas keras.",
                    color = Sky,
                )
            }
        }
    }
}

@Composable
private fun TargetExplanationCard() {
    CatetCard(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Flag, contentDescription = null, tint = Amber)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Cara membaca target", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Target capaian mengukur sisa uang bulan ini: pemasukan dikurangi pengeluaran. Angka ini adalah acuan kemajuan, bukan saldo rekening.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ExplanationLine(title: String, body: String, color: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.padding(top = 6.dp).width(7.dp).height(7.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(title, style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.width(78.dp))
        Text(body, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun BudgetSummaryCard(
    totalLimit: BigDecimal,
    totalExpected: BigDecimal,
    spent: BigDecimal,
    daily: BigDecimal,
) {
    val fraction = BudgetMath.fraction(spent, totalLimit)
    CatetCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RingProgress(
                fraction = fraction,
                label = (fraction * 100).toInt().coerceAtLeast(0).toString() + "%",
                color = if (fraction > 1f) MaterialTheme.colorScheme.error else Pine,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Total batas bulan ini", style = MaterialTheme.typography.labelLarge)
                Text(CurrencyFormatter.format(totalLimit), style = MaterialTheme.typography.titleLarge)
                Text(
                    "Terpakai " + CurrencyFormatter.format(spent) + " · sisa " +
                        CurrencyFormatter.format((totalLimit - spent).max(BigDecimal.ZERO)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (totalLimit > BigDecimal.ZERO) {
                        "Aman dipakai ± " + CurrencyFormatter.format(daily) + "/hari"
                    } else {
                        "Tambahkan batas untuk melihat saran harian."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Pine,
                )
            }
        }
        if (totalExpected > BigDecimal.ZERO) {
            Spacer(Modifier.height(14.dp))
            Text(
                "Estimasi belanja " + CurrencyFormatter.format(totalExpected) +
                    " · realisasi " + (fractionForExpected(spent, totalExpected) * 100).toInt() + "%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { fractionForExpected(spent, totalExpected).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = Sky,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
private fun TargetSummaryCard(saved: BigDecimal, totalTarget: BigDecimal) {
    val fraction = BudgetMath.fraction(saved, totalTarget)
    CatetCard(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RingProgress(
                fraction = fraction,
                color = Pine,
                label = (fraction * 100).toInt().coerceAtLeast(0).toString() + "%",
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Sisa uang bulan ini", style = MaterialTheme.typography.labelLarge)
                Text(CurrencyFormatter.format(saved), style = MaterialTheme.typography.titleLarge)
                Text(
                    if (totalTarget > BigDecimal.ZERO) {
                        "dari total target " + CurrencyFormatter.format(totalTarget)
                    } else {
                        "Belum ada total target"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (totalTarget > saved) {
                    Text(
                        "Kurang " + CurrencyFormatter.format(totalTarget - saved),
                        style = MaterialTheme.typography.bodySmall,
                        color = Pine,
                    )
                } else if (totalTarget > BigDecimal.ZERO) {
                    Text("Target bulan ini tercapai", style = MaterialTheme.typography.bodySmall, color = Pine)
                }
            }
        }
    }
}

@Composable
private fun BudgetProgressCard(
    budget: BudgetEntity,
    spent: BigDecimal,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val limit = BudgetMath.amount(budget)
    val fraction = BudgetMath.fraction(spent, limit)
    val over = spent > limit && limit > BigDecimal.ZERO
    val isEstimate = budget.type == BudgetMath.EXPECTED
    val accent = when {
        over -> MaterialTheme.colorScheme.error
        isEstimate -> Sky
        else -> Pine
    }
    CatetCard(Modifier.clickable(onClick = onEdit)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RingProgress(
                fraction = fraction,
                size = 64,
                stroke = 7,
                color = accent,
                label = (fraction * 100).toInt().coerceAtLeast(0).toString() + "%",
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(budget.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    BudgetMath.typeLabel(budget.type) + " · " + (budget.category ?: "Semua kategori"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    CurrencyFormatter.format(spent) + " / " + CurrencyFormatter.format(limit),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    if (over) {
                        if (isEstimate) "Realisasi di atas estimasi" else "Batas terlampaui"
                    } else {
                        if (isEstimate) "Masih dalam perkiraan" else "Sisa " + CurrencyFormatter.format(limit - spent)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = "Ubah ${budget.name}")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Hapus ${budget.name}")
            }
        }
    }
}

@Composable
private fun TargetProgressCard(
    target: BudgetEntity,
    achieved: BigDecimal,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val goal = BudgetMath.amount(target)
    val fraction = BudgetMath.fraction(achieved, goal)
    val complete = goal > BigDecimal.ZERO && achieved >= goal
    CatetCard(Modifier.clickable(onClick = onEdit)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RingProgress(
                fraction = fraction,
                size = 64,
                stroke = 7,
                color = if (complete) Pine else Amber,
                label = (fraction * 100).toInt().coerceIn(0, 100).toString() + "%",
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(target.name, style = MaterialTheme.typography.titleMedium)
                Text("Target capaian bulan ini", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(
                    CurrencyFormatter.format(achieved) + " / " + CurrencyFormatter.format(goal),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    if (complete) "Tercapai" else "Kurang " + CurrencyFormatter.format((goal - achieved).max(BigDecimal.ZERO)),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (complete) Pine else Amber,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = "Ubah ${target.name}")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Hapus ${target.name}")
            }
        }
    }
}

@Composable
private fun BudgetEditorDialog(
    existing: BudgetEntity?,
    initialType: String,
    saving: Boolean,
    error: String?,
    categories: List<String>,
    onValueChanged: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String, String?, String) -> Unit,
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var amount by remember(existing?.id) { mutableStateOf(existing?.amount.orEmpty()) }
    var category by remember(existing?.id) { mutableStateOf(existing?.category) }
    var type by remember(existing?.id, initialType) {
        mutableStateOf(existing?.type ?: initialType)
    }
    var categoryMenu by remember { mutableStateOf(false) }
    val targetMode = type == BudgetMath.TARGET

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    existing != null -> "Ubah ${BudgetMath.typeLabel(type).lowercase()}"
                    targetMode -> "Buat target capaian"
                    else -> "Buat rencana belanja"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!targetMode) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = type == BudgetMath.LIMIT,
                            onClick = { type = BudgetMath.LIMIT; onValueChanged() },
                            label = { Text("Batas") },
                        )
                        FilterChip(
                            selected = type == BudgetMath.EXPECTED,
                            onClick = { type = BudgetMath.EXPECTED; onValueChanged() },
                            label = { Text("Estimasi") },
                        )
                    }
                    Text(
                        if (type == BudgetMath.LIMIT) {
                            "Batas menjadi pagu maksimum pengeluaran."
                        } else {
                            "Estimasi adalah perkiraan belanja, bukan batas keras."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "Target dihitung dari pemasukan dikurangi pengeluaran bulan berjalan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; onValueChanged() },
                    label = { Text(if (targetMode) "Nama target" else "Nama rencana") },
                    placeholder = { Text(if (targetMode) "Contoh: Dana darurat" else "Contoh: Belanja bulanan") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { value ->
                        amount = value.filter { it.isDigit() || it == '.' || it == ',' }
                        onValueChanged()
                    },
                    label = { Text(if (targetMode) "Nominal target" else "Nominal") },
                    prefix = { Text("Rp ") },
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!targetMode) {
                    Box {
                        OutlinedTextField(
                            value = category ?: "Semua kategori",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Kategori") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                TextButton(onClick = { categoryMenu = true }) { Text("Pilih") }
                            },
                        )
                        DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Semua kategori") },
                                onClick = { category = null; categoryMenu = false },
                            )
                            categories.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item) },
                                    onClick = { category = item; categoryMenu = false },
                                )
                            }
                        }
                    }
                }
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, amount, if (targetMode) null else category, type) },
                enabled = !saving,
            ) { Text(if (saving) "Menyimpan…" else "Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Batal") } },
    )
}

private fun fractionForExpected(spent: BigDecimal, expected: BigDecimal): Float =
    BudgetMath.fraction(spent, expected)
