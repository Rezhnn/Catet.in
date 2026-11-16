package com.catetin.parser.model

import java.math.BigDecimal

enum class SourceType { NOTIFICATION, EMAIL, MANUAL, IMPORT }

enum class TransactionDirection { EXPENSE, INCOME, TRANSFER }

enum class ParseConfidence { HIGH, MEDIUM, LOW }

data class RawArtifact(
    val sourceType: SourceType,
    val sourceApp: String,
    val text: String,
    val timestampMillis: Long,
    val title: String? = null,
    val externalId: String? = null,
    val subject: String? = null,
)

data class ParsedTransaction(
    val amount: BigDecimal,
    val currency: String,
    val direction: TransactionDirection,
    val institution: String,
    val institutionId: String,
    val merchant: String?,
    val category: String,
    val accountHint: String?,
    val reference: String?,
    val occurredAtMillis: Long,
    val narration: String?,
    val balanceAfter: BigDecimal? = null,
    val parserId: String,
    val confidence: ParseConfidence,
)

sealed interface ParseResult {
    data class Parsed(val transaction: ParsedTransaction) : ParseResult
    data class Ignored(val reason: String) : ParseResult
    data class Unparsed(val reason: String) : ParseResult
}
