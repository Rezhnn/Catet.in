package com.catetin.app.ai.model

import com.catetin.app.BuildConfig

data class AssistantModelSpec(
    val id: String,
    val displayName: String,
    val variant: String,
    val fileName: String,
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val license: String,
    val sourceUrl: String,
)

object ModelCatalog {
    val qwenQ4 = AssistantModelSpec(
        id = "qwen3.5-0.8b",
        displayName = "Qwen3.5-0.8B",
        variant = "Q4_0",
        fileName = "Qwen3.5-0.8B-Q4_0.gguf",
        downloadUrl = BuildConfig.MODEL_DOWNLOAD_URL,
        sha256 = BuildConfig.MODEL_SHA256.lowercase(),
        sizeBytes = BuildConfig.MODEL_SIZE_BYTES,
        license = "Apache-2.0",
        sourceUrl = "https://huggingface.co/ggml-org/Qwen3.5-0.8B-GGUF",
    )

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "ukuran tidak diketahui"
        val mib = bytes / (1024.0 * 1024.0)
        return if (mib >= 1024) {
            String.format(java.util.Locale.forLanguageTag("id-ID"), "%.1f GB", mib / 1024.0)
        } else {
            String.format(java.util.Locale.forLanguageTag("id-ID"), "%.0f MB", mib)
        }
    }
}
