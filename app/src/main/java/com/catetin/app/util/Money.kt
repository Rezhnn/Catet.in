package com.catetin.app.util

import java.math.BigDecimal

data class Money(val amount: BigDecimal, val currency: String) {
    fun formatted(): String = CurrencyFormatter.format(amount, currency)
}

fun Iterable<Money>.sumByCurrency(): Map<String, BigDecimal> = fold(mutableMapOf()) { acc, money ->
    acc[money.currency] = (acc[money.currency] ?: BigDecimal.ZERO) + money.amount
    acc
}
