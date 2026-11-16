package com.catetin.app.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Currency
import java.util.Locale

object CurrencyFormatter {
    private const val IDR = "IDR"
    private val indonesian = Locale("id", "ID")

    fun format(amount: BigDecimal, currencyCode: String = IDR): String {
        val code = currencyCode.uppercase(Locale.ROOT)
        val value = amount.abs()
        if (code == IDR) {
            val symbols = DecimalFormatSymbols(indonesian)
            val formatter = DecimalFormat("#,##0", symbols)
            formatter.roundingMode = RoundingMode.HALF_UP
            return "Rp" + formatter.format(value)
        }
        val symbols = DecimalFormatSymbols(indonesian)
        val formatter = DecimalFormat("#,##0.00", symbols)
        val currency = runCatching { Currency.getInstance(code) }.getOrNull()
        val symbol = currency?.getSymbol(indonesian) ?: code
        return symbol + formatter.format(value)
    }

    fun formatSigned(amount: BigDecimal, currencyCode: String, positivePrefix: String = "+"): String {
        val sign = if (amount.signum() >= 0) positivePrefix else "−"
        return sign + format(amount, currencyCode)
    }

    fun formatCompact(amount: BigDecimal, currencyCode: String = IDR): String {
        val abs = amount.abs()
        return when {
            abs >= BigDecimal("1000000000") -> format(abs.divide(BigDecimal("1000000000"), 1, RoundingMode.HALF_UP), currencyCode) + " M"
            abs >= BigDecimal("1000000") -> format(abs.divide(BigDecimal("1000000"), 1, RoundingMode.HALF_UP), currencyCode) + " jt"
            abs >= BigDecimal("1000") -> format(abs.divide(BigDecimal("1000"), 0, RoundingMode.HALF_UP), currencyCode) + " rb"
            else -> format(abs, currencyCode)
        }
    }

    fun parse(text: String): BigDecimal? {
        val cleaned = text.trim().replace("Rp", "").replace(" ", "").replace(".", "").replace(",", ".")
        return cleaned.toBigDecimalOrNull()
    }
}
