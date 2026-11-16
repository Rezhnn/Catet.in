package com.catetin.app.domain.rules

import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {

    private val draft = RuleDraft(
        amount = BigDecimal("25000"),
        currency = "IDR",
        direction = "EXPENSE",
        category = "Lainnya",
        merchant = "Kopi Kenangan",
        narration = "Pembayaran QRIS",
        institution = "GoPay",
        reference = null,
        occurredAt = LocalDateTime.of(2026, 11, 16, 9, 30)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    )

    @Test
    fun `AND conditions must all match`() {
        val config = RuleConfig(
            logic = "AND",
            conditions = listOf(
                RuleCondition("MERCHANT", "CONTAINS", "kopi"),
                RuleCondition("AMOUNT", "LESS_THAN", "50000"),
            ),
        )
        assertTrue(RuleEngine.matches(config, context()))
    }

    @Test
    fun `OR conditions only need one match`() {
        val config = RuleConfig(
            logic = "OR",
            conditions = listOf(
                RuleCondition("MERCHANT", "CONTAINS", "tidak ada"),
                RuleCondition("CATEGORY", "EQUALS", "Lainnya"),
            ),
        )
        assertTrue(RuleEngine.matches(config, context()))
    }

    @Test
    fun `set category action is applied`() {
        val outcome = RuleEngine.apply(
            rules = listOf(
                1L to (RuleConfig(conditions = listOf(RuleCondition("MERCHANT", "CONTAINS", "kopi"))) to
                    RuleActionConfig(listOf(RuleAction("SET_CATEGORY", "Makanan & Minuman")))),
            ),
            draft = draft,
            rawText = "Pembayaran kopi",
            source = "NOTIFICATION",
        )
        assertEquals("Makanan & Minuman", outcome.draft.category)
        assertEquals(listOf(1L), outcome.matchedRuleIds)
    }

    @Test
    fun `block action prevents saving`() {
        val outcome = RuleEngine.apply(
            rules = listOf(
                7L to (RuleConfig(conditions = listOf(RuleCondition("RAW_TEXT", "CONTAINS", "otp"))) to
                    RuleActionConfig(listOf(RuleAction("BLOCK")))),
            ),
            draft = draft,
            rawText = "Kode OTP 1234",
            source = "NOTIFICATION",
        )
        assertTrue(outcome.blocked)
        assertEquals(7L, outcome.blockedByRuleId)
    }

    @Test
    fun `source condition distinguishes notification and email`() {
        val config = RuleConfig(conditions = listOf(RuleCondition("SOURCE", "EQUALS", "EMAIL")))
        assertFalse(RuleEngine.matches(config, context()))
        assertTrue(RuleEngine.matches(config, context().copy(source = "EMAIL")))
    }

    private fun context() = RuleContext(
        amount = draft.amount,
        currency = draft.currency,
        direction = draft.direction,
        category = draft.category,
        merchant = draft.merchant,
        narration = draft.narration,
        rawText = "Pembayaran kopi",
        institution = draft.institution,
        source = "NOTIFICATION",
        occurredAt = draft.occurredAt,
    )
}
