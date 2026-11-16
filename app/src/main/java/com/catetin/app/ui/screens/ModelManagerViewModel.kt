package com.catetin.app.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catetin.app.ai.lifecycle.AiLifecycleCoordinator
import com.catetin.app.ai.model.ModelInstaller
import com.catetin.app.ai.model.ModelOperationState
import com.catetin.app.data.db.AssistantModelEntity
import com.catetin.app.data.prefs.AppSettings
import com.catetin.app.data.prefs.SettingsRepository
import com.catetin.app.data.repo.FinanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ModelManagerViewModel @Inject constructor(
    private val installer: ModelInstaller,
    private val repository: FinanceRepository,
    private val settingsRepository: SettingsRepository,
    private val lifecycleCoordinator: AiLifecycleCoordinator,
) : ViewModel() {
    val model: StateFlow<AssistantModelEntity?> = repository.assistantModel.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )
    val settings: StateFlow<AppSettings?> = settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )
    val operation: StateFlow<ModelOperationState> = installer.state

    fun acceptConsentAndDownload() {
        viewModelScope.launch {
            settingsRepository.setAiModelConsent(true)
            runCatching { installer.download() }
        }
    }

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            lifecycleCoordinator.onAppBackgrounded()
            runCatching { installer.importFrom(uri) }
        }
    }

    fun deleteModel() {
        viewModelScope.launch {
            lifecycleCoordinator.onAppBackgrounded()
            installer.delete()
        }
    }

    fun clearOperationMessage() = installer.clearResult()
}
