package com.catetin.app.ai.service

internal object AiProtocol {
    const val MSG_LOAD = 1
    const val MSG_GENERATE = 2
    const val MSG_UNLOAD = 3
    const val MSG_REPLY = 100

    const val KEY_REQUEST_ID = "request_id"
    const val KEY_MODEL_PATH = "model_path"
    const val KEY_PROMPT = "prompt"
    const val KEY_SYSTEM_PROMPT = "system_prompt"
    const val KEY_MAX_TOKENS = "max_tokens"
    const val KEY_OK = "ok"
    const val KEY_TEXT = "text"
    const val KEY_ERROR = "error"
    const val KEY_STATE = "state"
}
