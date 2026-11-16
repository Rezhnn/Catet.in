@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.catetin.app.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catetin.app.BuildConfig
import com.catetin.app.ui.MainViewModel
import com.catetin.app.ui.nav.Overlay

@Composable
fun SettingsScreen(
    mainViewModel: MainViewModel,
    onBack: () -> Unit,
    onOpen: (Overlay) -> Unit,
) {
    val settings by mainViewModel.settings.collectAsStateWithLifecycle()
    val reviewCount by mainViewModel.pendingReviewCount.collectAsStateWithLifecycle()
    val unparsed by mainViewModel.unparsed.collectAsStateWithLifecycle()
    var showTheme by remember { mutableStateOf(false) }
    var showLock by remember { mutableStateOf(false) }
    var showRetention by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { SettingsSection("Sumber data") }
            item {
                SettingsRow(
                    icon = Icons.Outlined.NotificationsActive,
                    title = "Sumber & kesehatan",
                    subtitle = "Akses notifikasi, allow-list, Gmail, item belum terbaca",
                    onClick = { onOpen(Overlay.Sources) },
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Outlined.RateReview,
                    title = "Tinjauan",
                    subtitle = if (reviewCount > 0) reviewCount.toString() + " item menunggu keputusan" else "Tidak ada antrean",
                    onClick = { onOpen(Overlay.Review) },
                    trailing = { Text(if (reviewCount > 0) reviewCount.toString() else "—") },
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Rule,
                    title = "Aturan otomatis",
                    subtitle = "Kategorikan, ubah, atau blokir transaksi",
                    onClick = { onOpen(Overlay.Rules) },
                )
            }

            item { SettingsSection("Tampilan") }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Palette,
                    title = "Tema",
                    subtitle = when (settings?.themeMode) {
                        "DARK" -> "Gelap"
                        "LIGHT" -> "Terang"
                        else -> "Mengikuti sistem"
                    },
                    onClick = { showTheme = true },
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Outlined.ColorLens,
                    title = "Warna dinamis",
                    subtitle = "Ikuti wallpaper (Android 12+)",
                    onClick = {},
                    trailing = {
                        Switch(
                            checked = settings?.dynamicColor == true,
                            onCheckedChange = mainViewModel::setDynamicColor,
                        )
                    },
                )
            }

            item { SettingsSection("Keamanan") }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Fingerprint,
                    title = "Kunci biometrik",
                    subtitle = "Minta sidik jari atau wajah saat membuka aplikasi",
                    onClick = {},
                    trailing = {
                        Switch(
                            checked = settings?.appLockEnabled == true,
                            onCheckedChange = mainViewModel::setAppLockEnabled,
                        )
                    },
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Security,
                    title = "Batas waktu kunci",
                    subtitle = when (settings?.lockTimeoutSeconds) {
                        30 -> "30 detik"
                        300 -> "5 menit"
                        900 -> "15 menit"
                        else -> "1 menit"
                    },
                    onClick = { showLock = true },
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Storage,
                    title = "Sembunyikan pratinjau",
                    subtitle = "Blokir tangkapan layar dan pratinjau aplikasi",
                    onClick = {},
                    trailing = {
                        Switch(
                            checked = settings?.keepScreenSecure == true,
                            onCheckedChange = mainViewModel::setKeepScreenSecure,
                        )
                    },
                )
            }

            item { SettingsSection("Asisten AI") }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Memory,
                    title = "Kelola model AI",
                    subtitle = "Qwen3.5-0.8B · dijalankan di proses :ai",
                    onClick = { onOpen(Overlay.ModelManager) },
                )
            }

            item { SettingsSection("Data") }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Backup,
                    title = "Cadangkan & pulihkan",
                    subtitle = "Berkas Catet.in, tanpa kredensial Gmail",
                    onClick = { onOpen(Overlay.Backup) },
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Outlined.DeleteForever,
                    title = "Simpan teks mentah",
                    subtitle = "Hapus otomatis setelah " + (settings?.rawTextRetentionDays ?: 90) + " hari",
                    onClick = { showRetention = true },
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Outlined.DeleteForever,
                    title = "Hapus semua data",
                    subtitle = "Transaksi, aturan, anggaran, dan teks sumber",
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = { showDelete = true },
                )
            }

            item { SettingsSection("Tentang") }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Info,
                    title = "Catet.in " + BuildConfig.VERSION_NAME,
                    subtitle = "Lisensi AGPL-3.0 · dibuat untuk penggunaan pribadi",
                    onClick = {},
                )
            }
            item {
                Text(
                    "Semua data keuangan disimpan di perangkat. Tidak ada server, iklan, atau pelacak.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }

    if (showTheme) {
        ChoiceDialog(
            title = "Tema",
            options = listOf("Sistem" to "SYSTEM", "Terang" to "LIGHT", "Gelap" to "DARK"),
            selected = settings?.themeMode ?: "SYSTEM",
            onSelect = { mainViewModel.setThemeMode(it); showTheme = false },
            onDismiss = { showTheme = false },
        )
    }
    if (showLock) {
        ChoiceDialog(
            title = "Batas waktu kunci",
            options = listOf("30 detik" to "30", "1 menit" to "60", "5 menit" to "300", "15 menit" to "900"),
            selected = (settings?.lockTimeoutSeconds ?: 60).toString(),
            onSelect = { mainViewModel.setLockTimeout(it.toInt()); showLock = false },
            onDismiss = { showLock = false },
        )
    }
    if (showRetention) {
        ChoiceDialog(
            title = "Simpan teks mentah",
            options = listOf("30 hari" to "30", "90 hari" to "90", "180 hari" to "180", "365 hari" to "365"),
            selected = (settings?.rawTextRetentionDays ?: 90).toString(),
            onSelect = { mainViewModel.setRawTextRetention(it.toInt()); showRetention = false },
            onDismiss = { showRetention = false },
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Hapus semua data?") },
            text = { Text("Tindakan ini tidak dapat dibatalkan. Cadangkan dulu jika kamu masih membutuhkannya.") },
            confirmButton = {
                Button(onClick = {
                    mainViewModel.deleteAllData()
                    showDelete = false
                }) { Text("Hapus permanen") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Batal") } },
        )
    }
}

@Composable
fun SettingsSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
    )
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(9.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = titleColor)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (label, value) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(value) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        if (selected == value) Text("Dipilih", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}
