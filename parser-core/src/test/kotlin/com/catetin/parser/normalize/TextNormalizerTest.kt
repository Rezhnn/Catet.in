package com.catetin.parser.normalize

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun `normalizes unicode and whitespace`() {
        val input = "  Pembayaran\u00A0berhasil\r\n\r\n\r\nRp 25.000  "
        assertEquals("Pembayaran berhasil\n\nRp 25.000", TextNormalizer.normalize(input))
    }

    @Test
    fun `strips model control tokens`() {
        val clean = TextNormalizer.stripControlTokens("<|im_start|>merchant<|im_end|> <think>rahasia</think>")
        assertFalse(clean.contains("<|"))
        assertFalse(clean.contains("think"))
    }
}
