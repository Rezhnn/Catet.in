@file:OptIn(ExperimentalMaterial3Api::class)

package com.catetin.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catetin.app.ai.model.ModelCatalog
import com.catetin.app.ai.model.ModelOperation
import com.catetin.app.ui.components.CatetCard
import com.catetin.app.ui.components.StatusPill

@Composable
fun ModelManagerScreen(
    onBack: () -> Unit,
    viewModel: ModelManagerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val model by viewModel.model.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val operation by viewModel.operation.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showConsent by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val spec = ModelCatalog.qwenQ4
    val installed = model?.filePath != null

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) viewModel.importModel(uri)
    }

    LaunchedEffect(operation.message) {
        operation.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearOperationMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kelola model AI") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                CatetCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(spec.displayName + " · " + spec.variant, style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Model opsional untuk pertanyaan bebas. Pertanyaan umum tetap dijawab mode cepat tanpa model.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        StatusPill(if (installed) "Terpasang" else "Belum ada", installed)
                    }
                    Spacer(Modifier.height(14.dp))
                    ModelFact("Ukuran unduhan", ModelCatalog.formatBytes(spec.sizeBytes))
                    ModelFact("Kuantisasi", "Q4_0 · disarankan untuk RAM 4 GB")
                    ModelFact("Lisensi", spec.license)
                    ModelFact("Runtime", "llama.cpp · proses :ai terpisah")
                }
            }

            if (operation.busy) {
                item {
                    CatetCard {
                        Text(operationLabel(operation.operation), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { operation.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (operation.bytesTotal > 0) {
                                ModelCatalog.formatBytes(operation.bytesDone) + " dari " + ModelCatalog.formatBytes(operation.bytesTotal)
                            } else {
                                "Menyiapkan berkas…"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (installed) {
                item {
                    CatetCard(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Model siap", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Model hanya dimuat ketika tab Asisten dibuka dan dilepas setelah 15 detik keluar.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            model?.sha256 ?: "—",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/octet-stream", "application/x-gguf", "*/*")) },
                            enabled = !operation.busy,
                        ) { Text("Ganti dengan GGUF hasil fine-tune") }
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { showDelete = true },
                        enabled = !operation.busy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text("Hapus model dari perangkat", color = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                item {
                    Button(
                        onClick = { showConsent = true },
                        enabled = !operation.busy && spec.downloadUrl.startsWith("https://"),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Outlined.CloudDownload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Unduh model · " + ModelCatalog.formatBytes(spec.sizeBytes))
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/octet-stream", "application/x-gguf", "*/*")) },
                        enabled = !operation.busy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Outlined.FileOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Impor model GGUF dari penyimpanan")
                    }
                }
            }

            item {
                CatetCard(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Privasi dan integritas", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Unduhan resmi diverifikasi dengan SHA-256 dan hanya disimpan di ruang privat aplikasi. Model impor diperiksa sebagai GGUF dan hash-nya dicatat. Tidak ada data transaksi yang dikirim ke model atau keluar dari perangkat.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                CatetCard {
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(spec.sourceUrl))) }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Sumber model resmi", style = MaterialTheme.typography.titleSmall)
                            Text(spec.sourceUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (!settings?.aiModelConsent.orFalse() && !installed) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Model bersifat opsional dan tidak pernah diunduh tanpa persetujuanmu.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showConsent) {
        AlertDialog(
            onDismissRequest = { showConsent = false },
            title = { Text("Unduh model AI lokal?") },
            text = {
                Text(
                    "Catet.in akan mengunduh ${spec.displayName} ${spec.variant} sebesar ${ModelCatalog.formatBytes(spec.sizeBytes)} dari Hugging Face. Sebaiknya gunakan Wi-Fi. Unduhan dapat dilanjutkan. Model hanya digunakan di perangkat ini.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    showConsent = false
                    viewModel.acceptConsentAndDownload()
                }) { Text("Setuju & unduh") }
            },
            dismissButton = { TextButton(onClick = { showConsent = false }) { Text("Batal") } },
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Hapus model AI?") },
            text = { Text("Asisten tetap dapat menjawab pertanyaan umum dengan mode cepat, tetapi pertanyaan bebas memerlukan unduhan ulang model.") },
            confirmButton = {
                Button(onClick = {
                    showDelete = false
                    viewModel.deleteModel()
                }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Batal") } },
        )
    }
}

@Composable
private fun ModelFact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

private fun operationLabel(operation: ModelOperation): String = when (operation) {
    ModelOperation.DOWNLOADING -> "Mengunduh model"
    ModelOperation.IMPORTING -> "Menyalin model dari penyimpanan"
    ModelOperation.VERIFYING -> "Memeriksa integritas model"
    ModelOperation.DELETING -> "Menghapus model"
    else -> "Memproses model"
}

private fun Boolean?.orFalse(): Boolean = this == true
