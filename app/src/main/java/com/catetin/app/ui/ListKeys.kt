package com.catetin.app.ui

/** Stable, type-prefixed Lazy list keys prevent auto-increment IDs from different tables colliding. */
object ListKeys {
    fun budget(id: Long): String = "budget:$id"
    fun transaction(id: Long): String = "transaction:$id"
    fun dayHeader(day: String): String = "day:$day"
    fun gmailSender(pattern: String): String = "gmail-sender:$pattern"
    fun allowlist(packageName: String): String = "allowlist:$packageName"
    fun merchant(name: String): String = "merchant:$name"
    fun recurring(merchant: String, currency: String): String = "recurring:$merchant:$currency"
}
