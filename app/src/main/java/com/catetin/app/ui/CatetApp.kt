package com.catetin.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catetin.app.ui.nav.Overlay
import com.catetin.app.ui.screens.AddTransactionScreen
import com.catetin.app.ui.screens.AssistantScreen
import com.catetin.app.ui.screens.AnalyticsScreen
import com.catetin.app.ui.screens.BackupScreen
import com.catetin.app.ui.screens.BudgetsScreen
import com.catetin.app.ui.screens.HomeScreen
import com.catetin.app.ui.screens.ModelManagerScreen
import com.catetin.app.ui.screens.OnboardingScreen
import com.catetin.app.ui.screens.ReviewScreen
import com.catetin.app.ui.screens.RuleEditorScreen
import com.catetin.app.ui.screens.RulesScreen
import com.catetin.app.ui.screens.SettingsScreen
import com.catetin.app.ui.screens.SourcesScreen
import com.catetin.app.ui.screens.TransactionDetailScreen
import com.catetin.app.ui.screens.TransactionsScreen
import com.catetin.app.ui.theme.CatetTheme

private data class Tab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val tabs = listOf(
    Tab("Beranda", Icons.Filled.Home, Icons.Outlined.Home),
    Tab("Transaksi", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    Tab("Anggaran", Icons.Filled.Savings, Icons.Outlined.Savings),
    Tab("Analitik", Icons.Filled.BarChart, Icons.Outlined.BarChart),
    Tab("Asisten", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
)

@Composable
fun CatetApp(mainViewModel: MainViewModel = hiltViewModel()) {
    val settings by mainViewModel.settings.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val current = settings
    val dark = when (current?.themeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> systemDark
    }

    CatetTheme(darkTheme = dark, dynamicColor = current?.dynamicColor == true) {
        if (current == null) {
            SplashScreen()
        } else if (!current.onboardingComplete) {
            OnboardingScreen(mainViewModel)
        } else {
            AppShell(mainViewModel)
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Text("Catet.in", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun AppShell(mainViewModel: MainViewModel) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val overlays = remember { mutableStateListOf<Overlay>() }
    val overlay = overlays.lastOrNull()
    fun open(next: Overlay) { overlays.add(next) }
    fun close() { if (overlays.isNotEmpty()) overlays.removeAt(overlays.lastIndex) }

    BackHandler(enabled = overlay != null) { close() }

    if (overlay != null) {
        when (val screen = overlay) {
            Overlay.Settings -> SettingsScreen(mainViewModel, onBack = ::close, onOpen = ::open)
            Overlay.Sources -> SourcesScreen(mainViewModel, onBack = ::close, onReview = { open(Overlay.Review) })
            Overlay.Review -> ReviewScreen(
                mainViewModel,
                onBack = ::close,
                onOpenTransaction = { open(Overlay.Detail(it)) },
            )
            Overlay.Rules -> RulesScreen(mainViewModel, onBack = ::close, onOpenRule = { id, merchant -> open(Overlay.RuleEditor(id, merchant)) })
            Overlay.Backup -> BackupScreen(onBack = ::close)
            Overlay.ModelManager -> ModelManagerScreen(onBack = ::close)
            is Overlay.Detail -> TransactionDetailScreen(
                mainViewModel,
                transactionId = screen.transactionId,
                onBack = ::close,
                onEdit = { open(Overlay.AddTransaction(screen.transactionId)) },
                onRule = { merchant -> open(Overlay.RuleEditor(null, merchant)) },
            )
            is Overlay.AddTransaction -> AddTransactionScreen(
                mainViewModel,
                transactionId = screen.transactionId,
                onBack = ::close,
                onSaved = ::close,
            )
            is Overlay.RuleEditor -> RuleEditorScreen(
                mainViewModel,
                ruleId = screen.ruleId,
                seedMerchant = screen.seedMerchant,
                onBack = ::close,
            )
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                if (selectedTab == index) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label,
                            )
                        },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "tab",
            ) { tab ->
                when (tab) {
                    0 -> HomeScreen(
                        mainViewModel,
                        onOpenSettings = { open(Overlay.Settings) },
                        onOpenSources = { open(Overlay.Sources) },
                        onOpenReview = { open(Overlay.Review) },
                        onOpenBudgets = { selectedTab = 2 },
                        onAdd = { open(Overlay.AddTransaction()) },
                        onOpenTransaction = { open(Overlay.Detail(it)) },
                        onSeeAll = { selectedTab = 1 },
                    )
                    1 -> TransactionsScreen(
                        mainViewModel,
                        onAdd = { open(Overlay.AddTransaction()) },
                        onOpenTransaction = { open(Overlay.Detail(it)) },
                    )
                    2 -> BudgetsScreen(mainViewModel, onAdd = { open(Overlay.AddTransaction()) })
                    3 -> AnalyticsScreen(mainViewModel)
                    else -> AssistantScreen(
                        mainViewModel,
                        onOpenModel = { open(Overlay.ModelManager) },
                        onOpenTransaction = { open(Overlay.Detail(it)) },
                    )
                }
            }
        }
    }
}
