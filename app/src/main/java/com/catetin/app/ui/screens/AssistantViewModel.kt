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
import com.catetin.app.ai.router.IndonesianKeywordIntentRouter
import com.catetin.app.data.db.TransactionEntity
import com.catetin.app.data.repo.FinanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
) : ViewModel() {

    private val _state = MutableStateFlow(AssistantUiState())
    val state: StateFlow<AssistantUiState> = _state.asStateFlow()
    val aiStatus: StateFlow<AiEngineStatus> = aiLifecycle.status
    private var nextId = 1L

    fun onVisible() {
        viewModelScope.launch {
            val model = repository.assistantModel.first()
            aiLifecycle.onAssistantVisible(model?.filePath)
        }
    }

    fun onHidden() = aiLifecycle.onAssistantHidden()

    fun retryModel() {
        onVisible()
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
        viewModelScope.launch {
            val intent = IndonesianKeywordIntentRouter.match(question)
            if (intent != null) {
                val answer = queryExecutor.execute(intent)
                appendAnswer(answer, fastPath = true)
            } else {
                val model = repository.assistantModel.first()
                if (model?.filePath == null) {
                    appendAnswer(
                        AssistantAnswer(
                            "Pertanyaan itu memerlukan model AI lokal. Unduh Qwen3.5-0.8B lewat “Kelola model AI” agar saya bisa memahami pertanyaan yang lebih bebas. " +
                                "Untuk sekarang coba tanyakan: “Pengeluaran bulan ini”, “Belanja makan minggu lalu”, atau “Saldo saat ini”.",
                        ),
                        fastPath = false,
                    )
                } else {
                    aiLifecycle.onAssistantVisible(model.filePath)
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
        _state.value = AssistantUiState()
    }
}
