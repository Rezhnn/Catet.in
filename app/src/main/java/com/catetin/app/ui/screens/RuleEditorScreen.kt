@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.catetin.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catetin.app.domain.rules.RuleAction
import com.catetin.app.domain.rules.RuleActionConfig
import com.catetin.app.domain.rules.RuleCondition
import com.catetin.app.domain.rules.RuleConfig
import com.catetin.app.domain.rules.RuleEngine
import com.catetin.app.ui.MainViewModel
import com.catetin.app.ui.components.CatetCard

private val fields = listOf(
    "MERCHANT", "AMOUNT", "CATEGORY", "NARRATION", "RAW_TEXT",
    "INSTITUTION", "SOURCE", "TYPE", "HOUR", "DAY_OF_WEEK", "DAY_OF_MONTH", "DATE",
)
private val operators = listOf(
    "CONTAINS", "NOT_CONTAINS", "EQUALS", "NOT_EQUALS", "STARTS_WITH", "ENDS_WITH",
    "GREATER_THAN", "LESS_THAN", "BEFORE", "AFTER",
)
private val actionTypes = listOf("SET_CATEGORY", "SET_MERCHANT", "SET_DIRECTION", "APPEND_NARRATION", "PREPEND_NARRATION", "CLEAR_MERCHANT", "BLOCK")

@Composable
fun RuleEditorScreen(
    mainViewModel: MainViewModel,
    ruleId: Long?,
    seedMerchant: String?,
    onBack: () -> Unit,
    viewModel: RulesViewModel = hiltViewModel(),
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var logic by remember { mutableStateOf("AND") }
    val conditions = remember { mutableStateListOf(RuleCondition("MERCHANT", "CONTAINS", seedMerchant.orEmpty())) }
    val actions = remember { mutableStateListOf(RuleAction("SET_CATEGORY", "Makanan & Minuman")) }
    var previewCount by remember { mutableStateOf<Int?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(ruleId, rules) {
        if (ruleId != null && !loaded) {
            val rule = rules.firstOrNull { it.id == ruleId } ?: return@LaunchedEffect
            val config = RuleEngine.decodeConditions(rule.conditionsJson)
            val actionConfig = RuleEngine.decodeActions(rule.actionsJson)
            name = rule.name
            logic = config.logic
            conditions.clear()
            conditions.addAll(config.conditions)
            actions.clear()
            actions.addAll(actionConfig.actions)
            loaded = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (ruleId == null) "Aturan baru" else "Ubah aturan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        viewModel.save(
                            ruleId = ruleId,
                            name = name,
                            config = RuleConfig(logic, conditions.toList()),
                            actions = RuleActionConfig(actions.toList()),
                        ) { onBack() }
                    }) { Text("Simpan") }
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nama aturan") },
                placeholder = { Text("Misal: Kopi masuk Makanan") },
                singleLine = true,
            )
            Text("Jika kondisi berikut", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = logic == "AND", onClick = { logic = "AND" }, label = { Text("Semua (AND)") })
                FilterChip(selected = logic == "OR", onClick = { logic = "OR" }, label = { Text("Salah satu (OR)") })
            }
            conditions.forEachIndexed { index, condition ->
                ConditionCard(
                    condition = condition,
                    onChange = { conditions[index] = it },
                    onDelete = { if (conditions.size > 1) conditions.removeAt(index) },
                )
            }
            OutlinedButton(
                onClick = { conditions.add(RuleCondition()) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Tambah kondisi")
            }

            Text("Maka", style = MaterialTheme.typography.titleSmall)
            actions.forEachIndexed { index, action ->
                ActionCard(
                    action = action,
                    onChange = { actions[index] = it },
                    onDelete = { if (actions.size > 1) actions.removeAt(index) },
                )
            }
            OutlinedButton(
                onClick = { actions.add(RuleAction()) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Tambah aksi")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        viewModel.preview(RuleConfig(logic, conditions.toList())) { previewCount = it }
                    },
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Pratinjau") }
                Button(
                    onClick = {
                        viewModel.save(
                            ruleId = ruleId,
                            name = name,
                            config = RuleConfig(logic, conditions.toList()),
                            actions = RuleActionConfig(actions.toList()),
                        ) { onBack() }
                    },
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Simpan aturan") }
            }
            if (previewCount != null) {
                Text(
                    previewCount.toString() + " transaksi lama cocok dengan kondisi ini.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ConditionCard(
    condition: RuleCondition,
    onChange: (RuleCondition) -> Unit,
    onDelete: () -> Unit,
) {
    var fieldMenu by remember { mutableStateOf(false) }
    var opMenu by remember { mutableStateOf(false) }
    CatetCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Kondisi", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Hapus kondisi") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                OutlinedTextField(
                    value = condition.field,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Field") },
                    trailingIcon = { TextButton(onClick = { fieldMenu = true }) { Text("▾") } },
                )
                DropdownMenu(expanded = fieldMenu, onDismissRequest = { fieldMenu = false }) {
                    fields.forEach { field ->
                        DropdownMenuItem(text = { Text(field) }, onClick = {
                            onChange(condition.copy(field = field)); fieldMenu = false
                        })
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                OutlinedTextField(
                    value = condition.operator,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Operator") },
                    trailingIcon = { TextButton(onClick = { opMenu = true }) { Text("▾") } },
                )
                DropdownMenu(expanded = opMenu, onDismissRequest = { opMenu = false }) {
                    operators.forEach { op ->
                        DropdownMenuItem(text = { Text(op) }, onClick = {
                            onChange(condition.copy(operator = op)); opMenu = false
                        })
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = condition.value,
            onValueChange = { onChange(condition.copy(value = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nilai") },
            placeholder = { Text("Kopi") },
            singleLine = true,
        )
    }
}

@Composable
private fun ActionCard(
    action: RuleAction,
    onChange: (RuleAction) -> Unit,
    onDelete: () -> Unit,
) {
    var typeMenu by remember { mutableStateOf(false) }
    var valueMenu by remember { mutableStateOf(false) }
    val categories = com.catetin.parser.parse.CategoryGuesser.defaults.map { it.first }
    CatetCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Aksi", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Hapus aksi") }
        }
        Box {
            OutlinedTextField(
                value = action.actionType,
                onValueChange = {},
                readOnly = true,
                label = { Text("Jenis aksi") },
                trailingIcon = { TextButton(onClick = { typeMenu = true }) { Text("▾") } },
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                actionTypes.forEach { type ->
                    DropdownMenuItem(text = { Text(type) }, onClick = {
                        onChange(action.copy(actionType = type)); typeMenu = false
                    })
                }
            }
        }
        if (action.actionType != "BLOCK" && action.actionType != "CLEAR_MERCHANT") {
            Spacer(Modifier.height(8.dp))
            Box {
                OutlinedTextField(
                    value = action.value,
                    onValueChange = { onChange(action.copy(value = it)) },
                    readOnly = action.actionType == "SET_CATEGORY" || action.actionType == "SET_DIRECTION",
                    label = { Text("Nilai") },
                    trailingIcon = {
                        if (action.actionType == "SET_CATEGORY" || action.actionType == "SET_DIRECTION") {
                            TextButton(onClick = { valueMenu = true }) { Text("Pilih") }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownMenu(expanded = valueMenu, onDismissRequest = { valueMenu = false }) {
                    val options = if (action.actionType == "SET_DIRECTION") listOf("EXPENSE", "INCOME", "TRANSFER") else categories
                    options.forEach { item ->
                        DropdownMenuItem(text = { Text(item) }, onClick = {
                            onChange(action.copy(value = item)); valueMenu = false
                        })
                    }
                }
            }
        }
    }
}
