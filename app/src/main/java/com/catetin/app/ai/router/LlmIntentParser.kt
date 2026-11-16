package com.catetin.app.ai.router

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class LlmIntentDto(
    val metric: String = "UNSUPPORTED",
    val period: String = "THIS_MONTH",
    val category: String? = null,
    val limit: Int = 0,
)

object LlmIntentParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    fun parse(raw: String, allowedCategories: Collection<String>): Result<QueryIntent> = runCatching {
        val candidate = extractJsonObject(stripThinking(raw))
        require(candidate.isNotBlank()) { "Model tidak mengembalikan JSON" }
        val dto = json.decodeFromString(LlmIntentDto.serializer(), candidate)
        require(dto.metric.uppercase() != "UNSUPPORTED") { "Pertanyaan tidak didukung model" }
        val metric = runCatching { IntentMetric.valueOf(dto.metric.uppercase()) }
            .getOrElse { error("Metric tidak dikenal: ${dto.metric}") }
        val period = runCatching { IntentPeriod.valueOf(dto.period.uppercase()) }
            .getOrElse { IntentPeriod.THIS_MONTH }
        val category = dto.category
            ?.trim()
            ?.takeIf { it.isNotBlank() && it in allowedCategories }
        val direction = when (metric) {
            IntentMetric.INCOME -> "INCOME"
            IntentMetric.SPENDING, IntentMetric.LARGEST, IntentMetric.BREAKDOWN -> "EXPENSE"
            else -> null
        }
        QueryIntent(
            metric = metric,
            period = period,
            category = category,
            direction = direction,
            limit = if (metric == IntentMetric.LARGEST) 1 else 0,
        )
    }

    fun stripThinking(raw: String): String = raw
        .replace(Regex("(?is)<think(?:ing)?>.*?</think>"), "")
        .replace(Regex("(?is)```(?:json)?\\s*"), "")
        .replace("```", "")
        .trim()

    private fun extractJsonObject(text: String): String {
        val start = text.indexOf('{')
        if (start < 0) return ""
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until text.length) {
            val ch = text[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == '"' -> inString = false
                }
            } else {
                when (ch) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return text.substring(start, index + 1)
                    }
                }
            }
        }
        return ""
    }
}
