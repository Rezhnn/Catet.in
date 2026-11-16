package com.catetin.app.domain.dedupe

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class DedupeEngineTest {

    private val minute = 60_000L
    private val base = 1_779_000_000_000L

    private fun input(
        amount: String = "50000",
        direction: String = "EXPENSE",
        merchant: String? = "Kopi Kenangan",
        reference: String? = null,
        at: Long = base,
        source: String = "NOTIFICATION",
    ) = DedupeInput(
        amount = BigDecimal(amount),
        currency = "IDR",
        direction = direction,
        institution = "BCA",
        merchant = merchant,
        reference = reference,
        occurredAt = at,
        sourceType = source,
    )

    private fun candidate(
        id: Long = 1,
        amount: String = "50000",
        direction: String = "EXPENSE",
        merchant: String? = "Kopi Kenangan",
        reference: String? = null,
        at: Long = base,
        sources: Set<String> = setOf("NOTIFICATION"),
    ) = DedupeCandidate(
        transactionId = id,
        amount = BigDecimal(amount),
        currency = "IDR",
        direction = direction,
        institution = "BCA",
        merchant = merchant,
        reference = reference,
        occurredAt = at,
        sourceTypes = sources,
    )

    @Test
    fun `exact notification re-post is merged`() {
        val decision = DedupeEngine.decide(input(), listOf(candidate(at = base + 2 * minute)))
        assertEquals(DedupeMatch.EXACT_SAME_SOURCE, decision.match)
        assertEquals(1L, decision.candidateId)
    }

    @Test
    fun `delayed email with the same merchant is merged with notification`() {
        val decision = DedupeEngine.decide(
            input(source = "EMAIL", at = base + 300 * minute),
            listOf(candidate(sources = setOf("NOTIFICATION"))),
        )
        assertEquals(DedupeMatch.EXACT_CROSS_SOURCE, decision.match)
    }

    @Test
    fun `reference number defeats a different timestamp`() {
        val decision = DedupeEngine.decide(
            input(reference = "INV-123", at = base + 5000 * minute),
            listOf(candidate(reference = "INV-123", at = base, sources = setOf("EMAIL"))),
        )
        assertEquals(DedupeMatch.STRONG_REFERENCE, decision.match)
    }

    @Test
    fun `same amount at a different merchant is not merged`() {
        val decision = DedupeEngine.decide(
            input(merchant = "Toko Buku"),
            listOf(candidate(merchant = "Kopi Kenangan", at = base + minute)),
        )
        assertEquals(DedupeMatch.NONE, decision.match)
    }

    @Test
    fun `two identical genuine purchases go to review`() {
        val decision = DedupeEngine.decide(
            input(at = base + 5 * minute),
            listOf(
                candidate(id = 1, at = base),
                candidate(id = 2, at = base + 2 * minute),
            ),
        )
        assertEquals(DedupeMatch.AMBIGUOUS, decision.match)
    }

    @Test
    fun `refund is not merged with expense`() {
        val decision = DedupeEngine.decide(
            input(direction = "INCOME", source = "EMAIL"),
            listOf(candidate(direction = "EXPENSE")),
        )
        assertEquals(DedupeMatch.NONE, decision.match)
    }

    @Test
    fun `top up transfer is not merged with expense`() {
        val decision = DedupeEngine.decide(
            input(direction = "TRANSFER", at = base + 10 * minute),
            listOf(candidate(direction = "EXPENSE")),
        )
        assertEquals(DedupeMatch.NONE, decision.match)
    }

    @Test
    fun `ambiguous reference also goes to review`() {
        val decision = DedupeEngine.decide(
            input(reference = "REF-1"),
            listOf(candidate(id = 1, reference = "REF-1"), candidate(id = 2, reference = "REF-1")),
        )
        assertEquals(DedupeMatch.AMBIGUOUS, decision.match)
    }

    @Test
    fun `delayed email merchant wording with a prefix is still one purchase`() {
        val decision = DedupeEngine.decide(
            input(merchant = "Pembayaran ke Kopi Kenangan", source = "EMAIL", at = base + 180 * minute),
            listOf(candidate(merchant = "Kopi Kenangan", sources = setOf("NOTIFICATION"))),
        )
        assertEquals(DedupeMatch.EXACT_CROSS_SOURCE, decision.match)
    }

    @Test
    fun `reference formatting differences still match`() {
        val decision = DedupeEngine.decide(
            input(reference = "INV 123-456", source = "EMAIL", at = base + 500 * minute),
            listOf(candidate(reference = "inv123456", sources = setOf("NOTIFICATION"))),
        )
        assertEquals(DedupeMatch.STRONG_REFERENCE, decision.match)
    }
}
