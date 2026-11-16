@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.catetin.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catetin.app.BuildConfig
import com.catetin.app.ingestion.notification.NotificationAccess
import com.catetin.app.ui.MainViewModel
import com.catetin.app.ui.components.CatetCard
import com.catetin.app.ui.components.StatusPill
import com.catetin.app.ui.theme.Pine
import com.catetin.app.ui.util.rememberNotificationAccessGranted
import com.catetin.app.util.Dates

@Composable
fun SourcesScreen(
    mainViewModel: MainViewModel,
    onBack: () -> Unit,
    onReview: () -> Unit,
    sourcesViewModel: SourcesViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val granted = rememberNotificationAccessGranted()
    val allowlist by mainViewModel.allowlist.collectAsStateWithLifecycle()
    val gmail by mainViewModel.gmailSync.collectAsStateWithLifecycle()
    val gmailSenders by mainViewModel.gmailSenders.collectAsStateWithLifecycle()
    val unparsed by mainViewModel.unparsed.collectAsStateWithLifecycle()
    val notificationStats by mainViewModel.notificationStats.collectAsStateWithLifecycle()
    val operation by sourcesViewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showGmailHelp by remember { mutableStateOf(false) }
    var showDisconnect by remember { mutableStateOf(false) }
    var keepGmailData by remember { mutableStateOf(true) }
    var showAddSender by remember { mutableStateOf(false) }
    val powerManager = context.getSystemService(PowerManager::class.java)
    val batteryUnrestricted = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    val listenerConnected = NotificationAccess.isConnected()

    val gmailAuthLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK || result.data != null) {
            result.data?.let(sourcesViewModel::completeGmailSignIn)
        }
    }

    LaunchedEffect(operation.message) {
        operation.message?.let {
            snackbar.showSnackbar(it)
            sourcesViewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sumber data") },
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                CatetCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (granted) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
                            contentDescription = null,
                            tint = if (granted) Pine else MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Akses notifikasi", style = MaterialTheme.typography.titleMedium)
                            Text(
                                when {
                                    !granted -> "Mati — transaksi otomatis tidak berjalan"
                                    listenerConnected -> "Aktif · pembaca notifikasi terhubung"
                                    else -> "Izin aktif, menunggu layanan tersambung"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        StatusPill(
                            if (granted && listenerConnected) "Terhubung" else if (granted) "Menunggu" else "Mati",
                            positive = granted && listenerConnected,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { NotificationAccess.openSettings(context) },
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(if (granted) "Buka pengaturan notifikasi" else "Aktifkan akses notifikasi")
                    }
                }
            }

            item {
                CatetCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Mail, contentDescription = null, tint = Pine)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Gmail · hanya baca", style = MaterialTheme.typography.titleMedium)
                            Text(gmailStatusLine(gmail?.status, gmail?.accountHint, gmail?.lastSyncAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusPill(
                            when (gmail?.status) {
                                "OK" -> "Siap"
                                "SYNCING" -> "Sinkron"
                                "RECONNECT_NEEDED" -> "Ulangi"
                                "ERROR" -> "Gagal"
                                else -> "Belum"
                            },
                            positive = gmail?.status == "OK",
                        )
                    }
                    if (gmail?.status in setOf("ERROR", "RECONNECT_NEEDED") && !gmail?.message.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(gmail?.message.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(10.dp))
                    if (gmail?.status != null && gmail?.status != "NOT_CONNECTED") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = sourcesViewModel::syncNow,
                                enabled = !operation.busy && gmail?.status != "SYNCING",
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Icon(Icons.Outlined.CloudSync, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Sinkronkan")
                            }
                            OutlinedButton(onClick = { showDisconnect = true }, shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.Outlined.LinkOff, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Putuskan")
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                if (!sourcesViewModel.isGmailConfigured()) {
                                    showGmailHelp = true
                                } else {
                                    val intent = sourcesViewModel.createGmailSignInIntent()
                                    if (intent != null) gmailAuthLauncher.launch(intent) else showGmailHelp = true
                                }
                            },
                            enabled = !operation.busy,
                            shape = RoundedCornerShape(14.dp),
                        ) { Text("Hubungkan Gmail") }
                    }
                }
            }

            if (gmail?.status != null && gmail?.status != "NOT_CONNECTED") {
                item {
                    Text("Pengirim email yang diizinkan", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
                    Text(
                        "Hanya pengirim aktif yang diminta dari Gmail. Email pengirim lain tidak diunduh.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(gmailSenders, key = { "gmail-${it.pattern}" }) { sender ->
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(sender.label, style = MaterialTheme.typography.titleSmall)
                                Text(sender.pattern, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = sender.enabled, onCheckedChange = { sourcesViewModel.setGmailSenderEnabled(sender.pattern, it) })
                        }
                    }
                }
                item {
                    OutlinedButton(onClick = { showAddSender = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Tambah pengirim Gmail")
                    }
                }
            }

            item { Text("Aplikasi yang dipantau", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            item {
                Text(
                    "Teks notifikasi dari aplikasi yang tidak dicentang tidak pernah dibaca.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(allowlist, key = { it.packageName }) { entry ->
                val stat = notificationStats.firstOrNull { it.sourceApp == entry.packageName }
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.label, style = MaterialTheme.typography.titleSmall)
                            Text(entry.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (stat != null) {
                                Text(
                                    "${stat.count} sumber · terakhir ${Dates.formatDateTime(stat.receivedAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Switch(checked = entry.enabled, onCheckedChange = { mainViewModel.setAllowlistEnabled(entry.packageName, it) })
                    }
                }
            }

            item {
                CatetCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Belum terbaca", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (unparsed.isEmpty()) "Semua sumber allow-list berhasil dibaca" else "${unparsed.size} item perlu diperiksa",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = onReview) { Text("Tinjau") }
                    }
                }
            }

            item {
                CatetCard(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.BatteryAlert, contentDescription = null, tint = Pine)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Agar notifikasi tidak terhenti", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (batteryUnrestricted) {
                                    "Optimasi baterai untuk Catet.in sudah dibatasi. Di Xiaomi, Oppo, Vivo, dan Samsung, izinkan juga autostart serta matikan penghemat baterai khusus aplikasi ini."
                                } else {
                                    "Izinkan Catet.in berjalan tanpa optimasi baterai. Di Xiaomi, Oppo, Vivo, dan Samsung, izinkan juga autostart serta matikan penghemat baterai khusus aplikasi ini."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager?.isIgnoringBatteryOptimizations(context.packageName) == false) {
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:${context.packageName}"))
                        } else {
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        }
                        runCatching { context.startActivity(intent) }
                    }) { Text(if (batteryUnrestricted) "Buka pengaturan baterai" else "Izinkan di latar belakang") }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().clickable {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://myaccount.google.com/permissions"))) }
                    }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text("Kelola izin akun Google", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showGmailHelp) {
        AlertDialog(
            onDismissRequest = { showGmailHelp = false },
            title = { Text("Menyiapkan Gmail") },
            text = {
                Text(
                    if (!sourcesViewModel.isGmailConfigured()) {
                        "Build ini belum memiliki OAuth client ID. Buat Android OAuth client di Google Cloud dengan paket com.catetin.app dan SHA-1 keystore yang dipakai, lalu isi GMAIL_OAUTH_CLIENT_ID saat build. Panduan lengkap ada di docs/GMAIL_SETUP.md. Hanya scope gmail.readonly yang diminta."
                    } else {
                        "Catet.in hanya meminta izin baca Gmail. Email dari pengirim aktif diubah menjadi transaksi di perangkat; isi email tidak dikirim ke server mana pun."
                    },
                )
            },
            confirmButton = { TextButton(onClick = { showGmailHelp = false }) { Text("Tutup") } },
        )
    }

    if (showDisconnect) {
        AlertDialog(
            onDismissRequest = { showDisconnect = false },
            title = { Text("Putuskan Gmail?") },
            text = {
                Column {
                    Text("Sinkronisasi akan berhenti dan kredensial dihapus dari perangkat.")
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Simpan transaksi dari email", style = MaterialTheme.typography.titleSmall)
                            Text("Data yang sudah tercatat tetap ada, tetapi tidak dapat disinkronkan lagi tanpa menghubungkan ulang.", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = keepGmailData, onCheckedChange = { keepGmailData = it })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showDisconnect = false
                    sourcesViewModel.disconnect(deleteImportedEmailData = !keepGmailData)
                }) { Text("Putuskan") }
            },
            dismissButton = { TextButton(onClick = { showDisconnect = false }) { Text("Batal") } },
        )
    }

    if (showAddSender) {
        AddGmailSenderDialog(
            onDismiss = { showAddSender = false },
            onAdd = { pattern, label ->
                sourcesViewModel.addGmailSender(pattern, label) { showAddSender = false }
            },
        )
    }
}

@Composable
private fun AddGmailSenderDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var pattern by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah pengirim Gmail") },
        text = {
            Column {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("Email atau domain") },
                    placeholder = { Text("no-reply@bank.co.id") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (opsional)") },
                    placeholder = { Text("Bank saya") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text("Gunakan alamat lengkap untuk pengirim tertentu, atau domain untuk seluruh subdomain.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = { onAdd(pattern, label) }, enabled = pattern.isNotBlank()) { Text("Tambah") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

private fun gmailStatusLine(status: String?, account: String?, lastSync: Long?): String = when (status) {
    "OK" -> (account ?: "Terhubung") + " · terakhir " + (lastSync?.let(Dates::formatDateTime) ?: "—")
    "SYNCING" -> "Menyinkronkan email transaksi…"
    "RECONNECT_NEEDED" -> "Perlu dihubungkan ulang"
    "ERROR" -> "Sinkronisasi terakhir gagal"
    else -> "Belum terhubung"
}
