package com.catetin.app.ai.router

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmIntentParserTest {
    private val categories = listOf("Makanan & Minuman", "Transportasi", "Belanja")

    @Test
    fun `parses a valid intent and derives direction`() {
        val intent = LlmIntentParser.parse(
            """{"metric":"SPENDING","period":"LAST_WEEK","category":"Makanan & Minuman","limit":0}""",
            categories,
        ).getOrThrow()
        assertEquals(IntentMetric.SPENDING, intent.metric)
        assertEquals(IntentPeriod.LAST_WEEK, intent.period)
        assertEquals("EXPENSE", intent.direction)
        assertEquals("Makanan & Minuman", intent.category)
    }

    @Test
    fun `strips thinking and markdown fences`() {
        val raw = "```json\n{\"metric\":\"INCOME\",\"period\":\"THIS_MONTH\"}\n```"
        val intent = LlmIntentParser.parse(raw, categories).getOrThrow()
        assertEquals(IntentMetric.INCOME, intent.metric)
        assertEquals("INCOME", intent.direction)
    }

    @Test
    fun `rejects unsupported questions`() {
        val result = LlmIntentParser.parse("{\"metric\":\"UNSUPPORTED\"}", categories)
        assertTrue(result.isFailure)
    }

    @Test
    fun `drops a category outside the whitelist`() {
        val intent = LlmIntentParser.parse(
            "{\"metric\":\"SPENDING\",\"period\":\"TODAY\",\"category\":\"Tidak Ada\"}",
            categories,
        ).getOrThrow()
        assertNull(intent.category)
    }

    @Test
    fun `extracts object even with trailing prose`() {
        val intent = LlmIntentParser.parse(
            "Here you go {\"metric\":\"COUNT\",\"period\":\"THIS_YEAR\"} done",
            categories,
        ).getOrThrow()
        assertEquals(IntentMetric.COUNT, intent.metric)
    }
}
