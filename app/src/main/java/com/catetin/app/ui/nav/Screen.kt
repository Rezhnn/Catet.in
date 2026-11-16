package com.catetin.app.ui.nav

sealed interface Overlay {
    data object Settings : Overlay
    data object Sources : Overlay
    data object Review : Overlay
    data object Rules : Overlay
    data object Backup : Overlay
    data object ModelManager : Overlay
    data class Detail(val transactionId: Long) : Overlay
    data class AddTransaction(val transactionId: Long? = null) : Overlay
    data class RuleEditor(val ruleId: Long? = null, val seedMerchant: String? = null) : Overlay
}
