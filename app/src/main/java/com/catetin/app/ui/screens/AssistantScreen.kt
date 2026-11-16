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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catetin.app.ai.query.AssistantFigure
import com.catetin.app.ai.engine.AiEngineState
import com.catetin.app.ai.engine.AiEngineStatus
import com.catetin.app.ai.model.ModelCatalog
import com.catetin.app.ai.model.ModelOperation
import com.catetin.app.ai.model.ModelOperationState
import com.catetin.app.ai.router.IndonesianKeywordIntentRouter
import com.catetin.app.data.db.TransactionEntity
import com.catetin.app.ui.MainViewModel
import com.catetin.app.ui.components.CatetCard
import com.catetin.app.ui.components.StatusPill
import com.catetin.app.ui.components.TransactionRow
import com.catetin.app.ui.theme.Amber
import com.catetin.app.ui.theme.Pine
import com.catetin.app.util.CurrencyFormatter
import java.io.File

@Composable
fun AssistantScreen(
    mainViewModel: MainViewModel,
    onOpenModel: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    viewModel: AssistantViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val model by mainViewModel.assistantModel.collectAsStateWithLifecycle()
    val aiStatus by viewModel.aiStatus.collectAsStateWithLifecycle()
    val modelOperation by viewModel.modelOperation.collectAsStateWithLifecycle()
    val aiModelConsent by viewModel.aiModelConsent.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var showModelConsent by remember { mutableStateOf(false) }
    val modelSpec = ModelCatalog.qwenQ4
    val modelInstalled = model?.filePath?.let { File(it).isFile } == true

    DisposableEffect(Unit) {
        viewModel.onVisible()
        onDispose { viewModel.onHidden() }
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(Pine),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Asisten", style = MaterialTheme.typography.titleLarge)
                Text(
                    when (aiStatus.state) {
                        AiEngineState.LOADING -> "Memuat Qwen3.5-0.8B…"
                        AiEngineState.READY -> "Mini-Qwen siap · data lokal"
                        AiEngineState.ERROR -> "Mode cepat · AI lokal sedang istirahat"
                        else -> "Mode cepat · data lokal"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = viewModel::clear) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = "Bersihkan percakapan")
            }
        }

        AssistantModelCard(
            modifier = Modifier.padding(horizontal = 20.dp),
            installed = modelInstalled,
            operation = modelOperation,
            aiStatus = aiStatus,
            downloadAvailable = modelSpec.downloadUrl.startsWith("https://"),
            onDownload = { showModelConsent = true },
            onRetryDownload = {
                if (aiModelConsent) viewModel.retryModelDownload() else showModelConsent = true
            },
            onRetryLoad = viewModel::retryModel,
            onOpenModel = onOpenModel,
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.messages.isEmpty()) {
                item {
                    CatetCard {
                        Text("Tanya apa saja tentang uangmu", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Pertanyaan umum dijawab langsung oleh router Bahasa Indonesia — tanpa menunggu model AI.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(14.dp))
                        IndonesianKeywordIntentRouter.examples().forEach { example ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable {
                                    viewModel.setInput(example)
                                    viewModel.send()
                                },
                            ) {
                                Text(example, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            items(state.messages, key = { it.id }) { message ->
                MessageBubble(message, onOpenTransaction)
            }
            if (state.busy) {
                item { Text("Menghitung…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = state.input,
                onValueChange = viewModel::setInput,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Tanya pengeluaran…") },
                shape = RoundedCornerShape(22.dp),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { viewModel.send() }),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = viewModel::send,
                enabled = state.input.isNotBlank() && !state.busy,
                modifier = Modifier.size(52.dp).clip(CircleShape).background(Pine),
            ) {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Kirim", tint = Color.White)
            }
        }
    }

    if (showModelConsent) {
        AlertDialog(
            onDismissRequest = { showModelConsent = false },
            title = { Text("Unduh model AI lokal?") },
            text = {
                Text(
                    "Catet.in akan mengunduh ${modelSpec.displayName} ${modelSpec.variant} sebesar " +
                        "${ModelCatalog.formatBytes(modelSpec.sizeBytes)} ke perangkat ini. Sebaiknya gunakan Wi-Fi. " +
                        "Unduhan dapat dilanjutkan bila terputus, dan model tidak mengirim data transaksi ke luar perangkat. " +
                        "Dengan memilih “Setuju & unduh”, kamu memberi izin untuk mengunduh model ini.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showModelConsent = false
                        viewModel.acceptModelConsentAndDownload()
                    },
                ) { Text("Setuju & unduh") }
            },
            dismissButton = {
                TextButton(onClick = { showModelConsent = false }) { Text("Batal") }
            },
        )
    }
}

@Composable
private fun AssistantModelCard(
    modifier: Modifier = Modifier,
    installed: Boolean,
    operation: ModelOperationState,
    aiStatus: AiEngineStatus,
    downloadAvailable: Boolean,
    onDownload: () -> Unit,
    onRetryDownload: () -> Unit,
    onRetryLoad: () -> Unit,
    onOpenModel: () -> Unit,
) {
    val spec = ModelCatalog.qwenQ4
    val hasError = operation.operation == ModelOperation.ERROR && !installed
    val containerColor = when {
        hasError -> MaterialTheme.colorScheme.errorContainer
        installed -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
    }
    val title = when {
        operation.busy -> assistantModelOperationTitle(operation.operation)
        hasError -> "Model AI gagal disiapkan"
        installed -> "Model AI lokal terpasang"
        else -> "Model AI lokal"
    }
    val status = when {
        !installed -> "Opsional · pertanyaan umum tetap memakai mode cepat."
        aiStatus.state == AiEngineState.READY -> "Aktif dan siap menjawab pertanyaan bebas."
        aiStatus.state == AiEngineState.LOADING -> "Sedang dimuat di perangkat."
        aiStatus.state == AiEngineState.ERROR -> "Terpasang, tetapi belum dapat dimuat."
        else -> "Terpasang · dimuat otomatis saat diperlukan."
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(if (installed) Pine else Amber),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (installed) Icons.Outlined.CheckCircle else Icons.Outlined.CloudDownload,
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        status,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasError) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                if (installed) {
                    StatusPill("Terpasang")
                } else if (hasError) {
                    StatusPill("Gagal", positive = false)
                }
            }

            when {
                operation.busy -> {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { operation.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (operation.bytesTotal > 0) {
                            "${ModelCatalog.formatBytes(operation.bytesDone)} dari " +
                                "${ModelCatalog.formatBytes(operation.bytesTotal)} · " +
                                "${(operation.progress * 100).toInt().coerceIn(0, 100)}%"
                        } else {
                            "Menyiapkan unduhan…"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Mode cepat tetap dapat digunakan selama proses ini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                hasError -> {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Unduhan belum selesai. Periksa koneksi internet dan ruang penyimpanan, lalu coba lagi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onRetryDownload, modifier = Modifier.fillMaxWidth()) {
                        Text("Coba lagi")
                    }
                    TextButton(onClick = onOpenModel, modifier = Modifier.fillMaxWidth()) {
                        Text("Kelola model")
                    }
                }

                installed -> {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "${spec.displayName} ${spec.variant} · ${ModelCatalog.formatBytes(spec.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Tersimpan di ruang privat aplikasi dan hanya diproses di perangkat ini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (aiStatus.state == AiEngineState.ERROR) {
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = onRetryLoad, modifier = Modifier.fillMaxWidth()) {
                            Text("Coba muat ulang model")
                        }
                    }
                    TextButton(onClick = onOpenModel) { Text("Kelola model") }
                }

                else -> {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Untuk pertanyaan bebas di luar mode cepat. Berjalan lokal tanpa mengirim data transaksi ke luar perangkat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onDownload,
                        enabled = downloadAvailable,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Outlined.CloudDownload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (downloadAvailable) {
                                "Unduh model AI · ${ModelCatalog.formatBytes(spec.sizeBytes)}"
                            } else {
                                "Unduhan model belum tersedia"
                            },
                        )
                    }
                    if (!downloadAvailable) {
                        Text(
                            "Build ini belum menyiapkan alamat unduhan model.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onOpenModel) { Text("Kelola model dan impor GGUF") }
                }
            }
        }
    }
}

private fun assistantModelOperationTitle(operation: ModelOperation): String = when (operation) {
    ModelOperation.DOWNLOADING -> "Mengunduh model AI"
    ModelOperation.IMPORTING -> "Menyiapkan model AI"
    ModelOperation.VERIFYING -> "Memeriksa model AI"
    ModelOperation.DELETING -> "Menghapus model AI"
    else -> "Memproses model AI"
}

@Composable
private fun MessageBubble(message: ChatMessage, onOpenTransaction: (Long) -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.fromUser) Alignment.End else Alignment.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (message.fromUser) 20.dp else 6.dp,
                bottomEnd = if (message.fromUser) 6.dp else 20.dp,
            ),
            color = if (message.fromUser) Pine else MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (message.fromUser) Color.White else MaterialTheme.colorScheme.onSurface,
                )
                if (message.figures.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    message.figures.forEach { figure -> FigureRow(figure) }
                }
                if (message.transactions.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    message.transactions.forEach { transaction ->
                        TransactionRow(transaction, onClick = { onOpenTransaction(transaction.id) })
                    }
                }
                if (!message.fromUser) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (message.fastPath) "Mode cepat · 0 ms inferensi" else "Model lokal",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun FigureRow(figure: AssistantFigure) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(figure.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(CurrencyFormatter.format(figure.amount, figure.currency), style = MaterialTheme.typography.titleSmall)
    }
}
