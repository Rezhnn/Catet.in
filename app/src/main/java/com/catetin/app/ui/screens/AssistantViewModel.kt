package com.catetin.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catetin.app.ai.query.AssistantAnswer
import com.catetin.app.ai.query.AssistantFigure
import com.catetin.app.ai.query.QueryExecutor
import com.catetin.app.ai.engine.AiEngineState
import com.catetin.app.ai.engine.AiEngineStatus
import com.catetin.app.ai.engine.LocalLlmEngine
import com.catetin.app.ai.lifecycle.AiLifecycleCoordinator
import com.catetin.app.ai.model.ModelInstaller
import com.catetin.app.ai.model.ModelOperationState
import com.catetin.app.ai.router.IndonesianKeywordIntentRouter
import com.catetin.app.data.db.TransactionEntity
import com.catetin.app.data.prefs.SettingsRepository
import com.catetin.app.data.repo.FinanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    val figures: List<AssistantFigure> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val fastPath: Boolean = false,
)

data class AssistantUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val busy: Boolean = false,
    val fastPathHits: Int = 0,
)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val queryExecutor: QueryExecutor,
    private val repository: FinanceRepository,
    private val aiEngine: LocalLlmEngine,
    private val aiLifecycle: AiLifecycleCoordinator,
    private val modelInstaller: ModelInstaller,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AssistantUiState())
    val state: StateFlow<AssistantUiState> = _state.asStateFlow()
    val aiStatus: StateFlow<AiEngineStatus> = aiLifecycle.status
    val modelOperation: StateFlow<ModelOperationState> = modelInstaller.state
    val aiModelConsent: StateFlow<Boolean> = settingsRepository.settings
        .map { it.aiModelConsent }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    private var nextId = 1L
    private var requestJob: Job? = null
    private var visible = false

    fun onVisible() {
        visible = true
        viewModelScope.launch {
            activateInstalledModel()
        }
    }

    fun onHidden() {
        visible = false
        aiLifecycle.onAssistantHidden()
    }

    fun retryModel() {
        onVisible()
    }

    fun acceptModelConsentAndDownload() {
        if (modelOperation.value.busy) return
        viewModelScope.launch {
            val consentStored = runCatching {
                settingsRepository.setAiModelConsent(true)
            }.isSuccess
            if (consentStored) downloadModelNow()
        }
    }

    fun retryModelDownload() {
        if (modelOperation.value.busy) return
        viewModelScope.launch {
            val consentGiven = runCatching {
                settingsRepository.settings.first().aiModelConsent
            }.getOrDefault(false)
            if (consentGiven) downloadModelNow()
        }
    }

    fun setInput(value: String) { _state.value = _state.value.copy(input = value) }

    fun send() {
        val question = _state.value.input.trim()
        if (question.isEmpty() || _state.value.busy) return
        val userMessage = ChatMessage(nextId++, fromUser = true, text = question)
        _state.value = _state.value.copy(
            messages = _state.value.messages + userMessage,
            input = "",
            busy = true,
        )
        requestJob = viewModelScope.launch {
            try {
            val intent = IndonesianKeywordIntentRouter.match(question)
            if (intent != null) {
                val answer = queryExecutor.execute(intent)
                appendAnswer(answer, fastPath = true)
            } else {
                val model = repository.assistantModel.first()
                val modelPath = model?.filePath?.takeIf { File(it).isFile }
                if (modelPath == null) {
                    appendAnswer(
                        AssistantAnswer(
                            "Pertanyaan itu memerlukan model AI lokal. Ketuk “Unduh model AI” di bagian atas tab Asisten agar saya bisa memahami pertanyaan yang lebih bebas. " +
                                "Untuk sekarang coba tanyakan: “Pengeluaran bulan ini”, “Belanja makan minggu lalu”, atau “Saldo saat ini”.",
                        ),
                        fastPath = false,
                    )
                } else {
                    aiLifecycle.onAssistantVisible(modelPath)
                    val ready = withTimeoutOrNull(120_000) {
                        aiStatus.first {
                            it.state in setOf(AiEngineState.READY, AiEngineState.ERROR, AiEngineState.UNLOADED)
                        }
                    }
                    if (ready?.state == AiEngineState.READY) {
                        val categories = repository.categories.first().map { it.name }
                        val generated = aiEngine.generateIntent(question, categories)
                        generated.fold(
                            onSuccess = { query -> appendAnswer(queryExecutor.execute(query), fastPath = false) },
                            onFailure = {
                                appendAnswer(
                                    AssistantAnswer(
                                        "Maaf, saya belum memahami pertanyaan itu. Coba tanyakan misalnya: “Pengeluaran bulan ini” atau “Belanja makan minggu lalu”.",
                                    ),
                                    fastPath = false,
                                )
                            },
                        )
                    } else {
                        val message = when (ready?.state) {
                            AiEngineState.ERROR -> "Asisten AI diistirahatkan sementara karena memori atau model tidak dapat dimuat. Mode cepat tetap siap."
                            else -> "Model AI masih disiapkan. Sementara itu, coba pertanyaan mode cepat seperti “Pengeluaran bulan ini”."
                        }
                        appendAnswer(AssistantAnswer(message), fastPath = false)
                    }
                }
            }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                appendAnswer(
                    AssistantAnswer("Maaf, data belum dapat dibaca saat ini. Coba lagi sebentar lagi."),
                    fastPath = false,
                )
            }
        }
    }

    private fun appendAnswer(answer: AssistantAnswer, fastPath: Boolean) {
        _state.value = _state.value.copy(
            messages = _state.value.messages + ChatMessage(
                id = nextId++,
                fromUser = false,
                text = answer.text,
                figures = answer.figures,
                transactions = answer.transactions,
                fastPath = fastPath,
            ),
            busy = false,
            fastPathHits = _state.value.fastPathHits + if (fastPath) 1 else 0,
        )
    }

    fun clear() {
        requestJob?.cancel()
        requestJob = null
        _state.value = AssistantUiState()
    }

    private suspend fun activateInstalledModel() {
        val modelPath = repository.assistantModel.first()?.filePath?.takeIf { File(it).isFile }
        aiLifecycle.onAssistantVisible(modelPath)
    }

    private suspend fun downloadModelNow() {
        val downloaded = runCatching { modelInstaller.download() }.isSuccess
        if (downloaded && visible) activateInstalledModel()
    }
}
