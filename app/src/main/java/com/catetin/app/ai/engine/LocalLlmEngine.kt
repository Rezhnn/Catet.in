package com.catetin.app.ai.engine

import com.catetin.app.ai.router.QueryIntent
import kotlinx.coroutines.flow.StateFlow

enum class AiEngineState { UNLOADED, LOADING, READY, ERROR }

data class AiEngineStatus(
    val state: AiEngineState = AiEngineState.UNLOADED,
    val message: String = "Model belum dimuat",
)

interface LocalLlmEngine {
    val status: StateFlow<AiEngineStatus>
    suspend fun load(modelPath: String)
    suspend fun generateIntent(question: String, categories: List<String>): Result<QueryIntent>
    suspend fun unload()
}
