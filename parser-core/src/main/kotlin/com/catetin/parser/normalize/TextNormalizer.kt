package com.catetin.parser.normalize

import java.text.Normalizer
import java.util.Locale

object TextNormalizer {
    fun normalize(input: String): String {
        val unicode = Normalizer.normalize(input, Normalizer.Form.NFKC)
        val withoutZeroWidth = unicode.replace(Regex("[\u200B-\u200D\uFEFF]"), "")
        return withoutZeroWidth
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(Regex("[ \t\u00A0]+"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    fun lowercase(input: String): String = input.lowercase(Locale.ROOT)

    fun stripControlTokens(input: String): String =
        input.replace(Regex("<\\|(?:im_start|im_end|endoftext)\\|>"), "")
            .replace(Regex("</?think>"), "")
            .replace(Regex("<\\|(?:start|end)_of_turn\\|>"), "")
}
