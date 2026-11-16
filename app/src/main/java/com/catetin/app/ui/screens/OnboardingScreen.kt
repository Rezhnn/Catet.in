package com.catetin.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catetin.app.ingestion.notification.NotificationAccess
import com.catetin.app.ui.MainViewModel
import com.catetin.app.ui.components.CatetCard
import com.catetin.app.ui.theme.AmberSoft
import com.catetin.app.ui.theme.Pine
import com.catetin.app.ui.util.rememberNotificationAccessGranted
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    mainViewModel: MainViewModel,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val pager = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val granted = rememberNotificationAccessGranted()
    val allowlist by mainViewModel.allowlist.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Catet.in", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = { mainViewModel.completeOnboarding() }) {
                Text("Lewati dulu")
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            when (page) {
                0 -> WelcomePage()
                1 -> PermissionPage(granted = granted) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    NotificationAccess.openSettings(context)
                }
                else -> AllowlistPage(
                    entries = allowlist,
                    onToggle = { packageName, enabled -> mainViewModel.setAllowlistEnabled(packageName, enabled) },
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(3) { index ->
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (pager.currentPage == index) 22.dp else 8.dp, 8.dp)
                        .clip(CircleShape)
                        .background(if (pager.currentPage == index) Pine else MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
        if (pager.currentPage == 2) {
            Button(
                onClick = { mainViewModel.completeOnboarding() },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("Mulai mencatat", style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Button(
                onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("Lanjut", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(112.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Pine),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Bolt, contentDescription = null, tint = AmberSoft, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "Catat otomatis, tanpa ribet.",
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Notifikasi dan email transaksi dari bank serta e-wallet favoritmu menjadi catatan keuangan yang rapi — semuanya di perangkat ini.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeaturePill("Lokal")
            FeaturePill("Tanpa akun")
            FeaturePill("IDR")
        }
    }
}

@Composable
private fun FeaturePill(text: String) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun PermissionPage(granted: Boolean, onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text("Izinkan Catet.in membaca notifikasi", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "Hanya notifikasi dari aplikasi yang kamu centang yang akan dibaca. Aplikasi lain dibuang sebelum teksnya disentuh.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        CatetCard(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Shield, contentDescription = null, tint = Pine)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Data transaksi tidak pernah dikirim ke server. Tidak ada iklan atau pelacak.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        if (granted) {
            Text("Akses notifikasi aktif", color = Color(0xFF2E7D5B), style = MaterialTheme.typography.labelLarge)
        } else {
            Button(onClick = onGrant, shape = RoundedCornerShape(18.dp)) {
                Text("Buka pengaturan notifikasi")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Di Android terbaru, izinkan juga “Pengaturan terbatas” untuk Catet.in bila diminta.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AllowlistPage(
    entries: List<com.catetin.app.data.db.AllowlistEntity>,
    onToggle: (String, Boolean) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Text("Pilih sumber yang dipantau", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Matikan aplikasi yang tidak kamu ingin Catet.in baca. Kamu bisa mengubahnya kapan saja di Pengaturan.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entries.take(14).forEach { entry ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.label, style = MaterialTheme.typography.titleMedium)
                            Text(entry.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = entry.enabled, onCheckedChange = { onToggle(entry.packageName, it) })
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Daftar ini dapat bertambah setelah kamu memakai aplikasi lain; buka Pengaturan → Sumber Data.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
