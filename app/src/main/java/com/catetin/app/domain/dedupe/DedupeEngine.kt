package com.catetin.app.domain.dedupe

import java.math.BigDecimal
import java.util.Locale

data class DedupeInput(
    val amount: BigDecimal,
    val currency: String,
    val direction: String,
    val institution: String?,
    val merchant: String?,
    val reference: String?,
    val occurredAt: Long,
    val sourceType: String,
)

data class DedupeCandidate(
    val transactionId: Long,
    val amount: BigDecimal,
    val currency: String,
    val direction: String,
    val institution: String?,
    val merchant: String?,
    val reference: String?,
    val occurredAt: Long,
    val sourceTypes: Set<String>,
)

enum class DedupeMatch { STRONG_REFERENCE, EXACT_CROSS_SOURCE, EXACT_SAME_SOURCE, WEAK, AMBIGUOUS, NONE }

data class DedupeDecision(
    val match: DedupeMatch,
    val candidateId: Long? = null,
    val reason: String = "",
)

/**
 * Decides whether an incoming artifact describes an existing transaction.
 *
 * The same purchase often arrives twice: once as a bank/e-wallet notification
 * and once as an email receipt. A reference number is authoritative. Without
 * one, the amount, currency, direction, institution and merchant must all agree;
 * cross-source duplicates get a 72-hour window because receipts are often sent
 * later, while same-source re-posts get only 15 minutes. If more than one
 * transaction fits, the item goes to review instead of being merged silently.
 */
object DedupeEngine {
    private const val MINUTE = 60_000L
    private const val SAME_SOURCE_EXACT_WINDOW = 15 * MINUTE
    private const val CROSS_SOURCE_EXACT_WINDOW = 72 * 60 * MINUTE
    private const val SAME_SOURCE_WEAK_WINDOW = 10 * MINUTE
    private const val CROSS_SOURCE_WEAK_WINDOW = 30 * MINUTE

    fun decide(input: DedupeInput, candidates: List<DedupeCandidate>): DedupeDecision {
        if (!input.reference.isNullOrBlank()) {
            val normalizedReference = normalizeReference(input.reference)
            val referenceMatches = if (normalizedReference.length >= 3) {
                candidates.filter {
                    !it.reference.isNullOrBlank() && normalizeReference(it.reference) == normalizedReference
                }
            } else emptyList()
            when (referenceMatches.size) {
                1 -> return DedupeDecision(
                    DedupeMatch.STRONG_REFERENCE,
                    referenceMatches.single().transactionId,
                    "reference number matches",
                )
                in 2..Int.MAX_VALUE -> return DedupeDecision(
                    DedupeMatch.AMBIGUOUS,
                    reason = "reference number matches more than one transaction",
                )
            }
        }

        val sameMoney = candidates.filter {
            it.amount.compareTo(input.amount) == 0 &&
                it.currency.equals(input.currency, ignoreCase = true) &&
                it.direction.equals(input.direction, ignoreCase = true) &&
                normalize(it.institution) == normalize(input.institution)
        }
        if (sameMoney.isEmpty()) return DedupeDecision(DedupeMatch.NONE, reason = "no amount match")

        val incomingMerchant = normalizeMerchant(input.merchant)
        val exact = sameMoney.filter { candidate ->
            val candidateMerchant = normalizeMerchant(candidate.merchant)
            val crossSource = candidate.sourceTypes.any { !it.equals(input.sourceType, ignoreCase = true) }
            val window = if (crossSource) CROSS_SOURCE_EXACT_WINDOW else SAME_SOURCE_EXACT_WINDOW
            incomingMerchant != null &&
                candidateMerchant != null &&
                merchantCompatible(incomingMerchant, candidateMerchant, crossSource) &&
                kotlin.math.abs(candidate.occurredAt - input.occurredAt) <= window
        }
        when (exact.size) {
            1 -> {
                val candidate = exact.single()
                val cross = candidate.sourceTypes.any { !it.equals(input.sourceType, ignoreCase = true) }
                return DedupeDecision(
                    if (cross) DedupeMatch.EXACT_CROSS_SOURCE else DedupeMatch.EXACT_SAME_SOURCE,
                    candidate.transactionId,
                    if (cross) "same amount, institution and merchant across sources" else "same purchase re-posted",
                )
            }
            in 2..Int.MAX_VALUE -> return DedupeDecision(
                DedupeMatch.AMBIGUOUS,
                reason = "more than one identical purchase is a possible match",
            )
        }

        val weak = sameMoney.filter { candidate ->
            val crossSource = candidate.sourceTypes.any { !it.equals(input.sourceType, ignoreCase = true) }
            val window = if (crossSource) CROSS_SOURCE_WEAK_WINDOW else SAME_SOURCE_WEAK_WINDOW
            val candidateMerchant = normalizeMerchant(candidate.merchant)
            val merchantsCompatible = incomingMerchant == null ||
                candidateMerchant == null ||
                incomingMerchant == candidateMerchant
            merchantsCompatible && kotlin.math.abs(candidate.occurredAt - input.occurredAt) <= window
        }
        return when (weak.size) {
            1 -> DedupeDecision(
                DedupeMatch.WEAK,
                weak.single().transactionId,
                "same amount and institution within the time window",
            )
            in 2..Int.MAX_VALUE -> DedupeDecision(
                DedupeMatch.AMBIGUOUS,
                reason = "several same-amount transactions are possible matches",
            )
            else -> DedupeDecision(DedupeMatch.NONE, reason = "outside dedupe window")
        }
    }

    private fun normalize(value: String?): String = value.orEmpty().trim().lowercase(Locale.ROOT)

    private fun normalizeReference(value: String): String =
        value.uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]"), "")

    private fun normalizeMerchant(value: String?): String? {
        val normalized = value.orEmpty().lowercase(Locale.ROOT)
            .replace(Regex("^(pembayaran|payment|pembelian|purchase|transaksi|trx|belanja)\\s+(ke|di|at|to)\\s+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return normalized.ifBlank { null }
    }

    private fun merchantCompatible(first: String, second: String, crossSource: Boolean): Boolean {
        if (first == second) return true
        if (!crossSource) return false
        if (first.length < 5 || second.length < 5) return false
        return first.contains(second) || second.contains(first)
    }
}
