package com.catetin.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catetin.app.data.db.AccountEntity
import com.catetin.app.data.db.AllowlistEntity
import com.catetin.app.data.db.AssistantModelEntity
import com.catetin.app.data.db.BudgetEntity
import com.catetin.app.data.db.CategoryEntity
import com.catetin.app.data.db.GmailSenderEntity
import com.catetin.app.data.db.GmailSyncEntity
import com.catetin.app.data.db.TransactionEntity
import com.catetin.app.data.db.SourceEventEntity
import com.catetin.app.data.prefs.AppSettings
import com.catetin.app.data.prefs.SettingsRepository
import com.catetin.app.data.repo.FinanceRepository
import com.catetin.app.util.PeriodPreset
import com.catetin.app.ai.lifecycle.AiLifecycleCoordinator
import com.catetin.app.ai.model.ModelInstaller
import com.catetin.app.ingestion.gmail.GmailAuthManager
import com.catetin.app.ingestion.gmail.GmailSyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val repository: FinanceRepository,
    private val aiLifecycle: AiLifecycleCoordinator,
    private val modelInstaller: ModelInstaller,
    private val gmailAuthManager: GmailAuthManager,
    private val gmailSyncScheduler: GmailSyncScheduler,
) : ViewModel() {

    val settings: StateFlow<AppSettings?> = settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )
    val monthTransactions: StateFlow<List<TransactionEntity>> = repository
        .observeTransactions(PeriodPreset.THIS_MONTH)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recentTransactions: StateFlow<List<TransactionEntity>> = repository.recentTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val budgets: StateFlow<List<BudgetEntity>> = repository.budgets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories: StateFlow<List<CategoryEntity>> = repository.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allowlist: StateFlow<List<AllowlistEntity>> = repository.allowlist
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val pendingReviewCount: StateFlow<Int> = repository.pendingReviewCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val pendingReviews: StateFlow<List<com.catetin.app.data.db.ReviewItemEntity>> = repository.pendingReviews
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val unparsed: StateFlow<List<SourceEventEntity>> = repository.unparsed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val notificationStats: StateFlow<List<com.catetin.app.data.db.SourceStat>> = repository.notificationStats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val gmailSync: StateFlow<GmailSyncEntity?> = repository.gmailSync
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val gmailSenders: StateFlow<List<GmailSenderEntity>> = repository.gmailSenders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val accounts: StateFlow<List<AccountEntity>> = repository.accounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val assistantModel: StateFlow<AssistantModelEntity?> = repository.assistantModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun completeOnboarding() {
        viewModelScope.launch { settingsRepository.setOnboardingComplete(true) }
    }

    fun setAllowlistEnabled(packageName: String, enabled: Boolean) {
        viewModelScope.launch { repository.setAllowlistEnabled(packageName, enabled) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAppLockEnabled(enabled) }
    }

    fun setLockTimeout(seconds: Int) {
        viewModelScope.launch { settingsRepository.setLockTimeoutSeconds(seconds) }
    }

    fun setRawTextRetention(days: Int) {
        viewModelScope.launch { settingsRepository.setRawTextRetentionDays(days) }
    }

    fun setIncludeRawTextInBackup(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setIncludeRawTextInBackup(enabled) }
    }

    fun setGmailBackfillDays(days: Int) {
        viewModelScope.launch { settingsRepository.setGmailBackfillDays(days) }
    }

    fun setGmailSenderEnabled(pattern: String, enabled: Boolean) {
        viewModelScope.launch { repository.setGmailSenderEnabled(pattern, enabled) }
    }

    fun addGmailSender(pattern: String, label: String) {
        viewModelScope.launch { repository.addGmailSender(pattern, label) }
    }

    fun setKeepScreenSecure(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setKeepScreenSecure(enabled) }
    }

    fun deleteAllData() {
        viewModelScope.launch {
            aiLifecycle.onAppBackgrounded()
            gmailSyncScheduler.cancel()
            gmailAuthManager.disconnect()
            runCatching { modelInstaller.delete() }
            repository.deleteAllData()
            settingsRepository.setAiModelConsent(false)
            settingsRepository.setIncludeRawTextInBackup(false)
        }
    }
}
