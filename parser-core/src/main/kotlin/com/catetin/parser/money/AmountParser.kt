package com.catetin.parser.money

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

data class MoneyMatch(
    val amount: BigDecimal,
    val currency: String,
    val raw: String,
    val start: Int,
    val end: Int,
)

/**
 * Deterministic Indonesian-first money reader.
 *
 * The parser deliberately rejects a number when the separator convention is
 * ambiguous (for example "1.234" could be 1234 or 1.234). Confidence is more
 * valuable than a guess in a ledger.
 */
object AmountParser {

    private val currencyPrefix = Regex(
        """(?i)(S\$|RM|Rp\.?|IDR|USD|SGD|MYR|EUR|GBP|AUD|JPY|CNY|THB|\$|€|£)\s?([0-9][0-9.,]*)""",
    )
    private val bareGrouped = Regex("""(?<!\d)(\d{1,3}(?:[.,]\d{3})+(?:[.,]\d{1,2})?)(?!\d)""")
    private val barePlain = Regex("""(?<![\d.,])(\d{4,12})(?![\d.,])""")
    private val shorthand = Regex(
        """(?i)(\d+(?:[.,]\d+)?)(?:\s?(rb|ribu|jt|juta)|(k))(?![a-z])""",
    )

    fun findAll(text: String): List<MoneyMatch> {
        val results = mutableListOf<MoneyMatch>()

        shorthand.findAll(text).forEach { match ->
            val suffix = match.groupValues[2].ifEmpty { match.groupValues[3] }
            val amount = parseShorthand(match.groupValues[1], suffix) ?: return@forEach
            results += MoneyMatch(
                amount = amount,
                currency = "IDR",
                raw = match.value,
                start = match.range.first,
                end = match.range.last + 1,
            )
        }

        currencyPrefix.findAll(text).forEach { match ->
            if (results.any { it.start < match.range.last + 1 && match.range.first < it.end }) {
                return@forEach
            }
            val currency = currencyOf(match.groupValues[1]) ?: return@forEach
            val raw = match.groupValues[2]
            val amount = parseNumber(raw, currency) ?: return@forEach
            results += MoneyMatch(
                amount = amount,
                currency = currency,
                raw = match.value,
                start = match.range.first,
                end = match.range.last + 1,
            )
        }

        bareGrouped.findAll(text).forEach { match ->
            val overlapping = results.any { it.start < match.range.last + 1 && match.range.first < it.end }
            if (!overlapping) {
                val amount = parseNumber(match.groupValues[1], "IDR") ?: return@forEach
                results += MoneyMatch(
                    amount = amount,
                    currency = "IDR",
                    raw = match.value,
                    start = match.range.first,
                    end = match.range.last + 1,
                )
            }
        }

        barePlain.findAll(text).forEach { match ->
            val overlapping = results.any { it.start < match.range.last + 1 && match.range.first < it.end }
            if (!overlapping) {
                results += MoneyMatch(
                    amount = BigDecimal(match.groupValues[1]),
                    currency = "IDR",
                    raw = match.value,
                    start = match.range.first,
                    end = match.range.last + 1,
                )
            }
        }

        return results.sortedBy { it.start }
    }

    fun findPrimary(text: String, currencyHint: String? = null): MoneyMatch? {
        val all = findAll(text)
        if (all.isEmpty()) return null
        return all.firstOrNull { match ->
            val before = text.substring(maxOf(0, match.start - 18), match.start).lowercase(Locale.ROOT)
            !before.contains("saldo") && !before.contains("balance")
        } ?: all.first().takeIf { currencyHint == null || it.currency == currencyHint }
    }

    fun findBalance(text: String): MoneyMatch? {
        val pattern = Regex("""(?is)(?:saldo|balance|sisa)[^0-9]{0,24}(S\$|RM|Rp\.?|IDR|USD|SGD|MYR|EUR|GBP|\$|€|£)?\s?([0-9][0-9.,]*)""")
        val match = pattern.find(text) ?: return null
        val currency = currencyOf(match.groupValues[1]) ?: "IDR"
        val amount = parseNumber(match.groupValues[2], currency) ?: return null
        return MoneyMatch(amount, currency, match.value, match.range.first, match.range.last + 1)
    }

    fun currencyOf(token: String): String? = when (token.trim().uppercase(Locale.ROOT)) {
        "RP", "RP.", "IDR" -> "IDR"
        "USD", "$" -> "USD"
        "SGD", "S$" -> "SGD"
        "MYR", "RM" -> "MYR"
        "EUR", "€" -> "EUR"
        "GBP", "£" -> "GBP"
        "AUD" -> "AUD"
        "JPY" -> "JPY"
        "CNY" -> "CNY"
        "THB" -> "THB"
        else -> null
    }

    /**
     * Parses a numeric token using its separator pattern. Returns null when the
     * convention cannot be inferred without guessing.
     */
    fun parseNumber(raw: String, currency: String): BigDecimal? {
        val cleaned = raw.trim().removeSuffix(",-").replace(" ", "")
        if (cleaned.isEmpty() || cleaned.none { it.isDigit() }) return null
        val hasDot = cleaned.contains('.')
        val hasComma = cleaned.contains(',')

        val normalized = when {
            hasDot && hasComma -> {
                val lastDot = cleaned.lastIndexOf('.')
                val lastComma = cleaned.lastIndexOf(',')
                val decimalSep = if (lastDot > lastComma) '.' else ','
                val thousandsSep = if (decimalSep == '.') ',' else '.'
                val digitsAfterLast = cleaned.length - maxOf(lastDot, lastComma) - 1
                if (digitsAfterLast !in 1..2) return null
                cleaned.replace("$thousandsSep", "").replace(' ', ' ')
                    .replace(decimalSep, '.')
            }
            hasDot -> {
                val parts = cleaned.split('.')
                when {
                    parts.size == 2 && parts.last().length in 1..2 ->
                        cleaned.replace(".", ".")
                    parts.drop(1).all { it.length == 3 } -> cleaned.replace(".", "")
                    else -> return null
                }
            }
            hasComma -> {
                val parts = cleaned.split(',')
                when {
                    parts.drop(1).all { it.length == 3 } -> cleaned.replace(",", "")
                    parts.size == 2 && parts.last().length in 1..2 -> cleaned.replace(',', '.')
                    else -> return null
                }
            }
            else -> cleaned
        }
        return normalized.toBigDecimalOrNull()?.let { value ->
            when {
                value.scale() <= 2 -> value
                else -> value.setScale(2, RoundingMode.HALF_UP)
            }
        }
    }

    private fun parseShorthand(number: String, suffix: String): BigDecimal? {
        val base = parseNumber(number, "IDR") ?: return null
        val multiplier = when (suffix.lowercase(Locale.ROOT)) {
            "k", "rb", "ribu" -> BigDecimal(1_000)
            "jt", "juta" -> BigDecimal(1_000_000)
            "m" -> BigDecimal(1_000_000)
            else -> return null
        }
        val value = base.multiply(multiplier)
        val stripped = value.stripTrailingZeros()
        return if (stripped.scale() < 0) stripped.setScale(0) else stripped
    }
}
