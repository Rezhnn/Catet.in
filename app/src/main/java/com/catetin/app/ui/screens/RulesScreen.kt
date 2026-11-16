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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catetin.app.domain.rules.RuleEngine
import com.catetin.app.ui.MainViewModel
import com.catetin.app.ui.components.CatetCard
import com.catetin.app.ui.components.EmptyState
import com.catetin.app.ui.theme.Pine

@Composable
fun RulesScreen(
    mainViewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenRule: (Long?, String?) -> Unit,
    viewModel: RulesViewModel = hiltViewModel(),
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aturan otomatis") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onOpenRule(null, null) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Aturan") },
                containerColor = Pine,
                contentColor = Color.White,
            )
        },
    ) { padding ->
        if (rules.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    title = "Belum ada aturan",
                    body = "Contoh: semua merchant yang mengandung “Kopi” menjadi kategori Makanan & Minuman.",
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(rules, key = { it.id }) { rule ->
                val config = RuleEngine.decodeConditions(rule.conditionsJson)
                val actions = RuleEngine.decodeActions(rule.actionsJson)
                CatetCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(rule.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                config.conditions.joinToString(" " + config.logic + " ") {
                                    it.field.lowercase().replaceFirstChar { c -> c.uppercase() } + " " +
                                        it.operator.lowercase().replace('_', ' ') + " " + it.value
                                }.ifBlank { "Tidak ada kondisi" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                actions.actions.joinToString(", ") { it.actionType.lowercase().replace('_', ' ') + " " + it.value }
                                    .ifBlank { "Tidak ada aksi" },
                                style = MaterialTheme.typography.bodySmall,
                                color = Pine,
                            )
                        }
                        Switch(checked = rule.enabled, onCheckedChange = { viewModel.toggle(rule, it) })
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onOpenRule(rule.id, null) }) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.width(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Ubah")
                        }
                        TextButton(onClick = { viewModel.delete(rule) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.width(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Hapus")
                        }
                    }
                }
            }
        }
    }
}
