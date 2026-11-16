package com.catetin.app.domain.rules

import kotlinx.serialization.Serializable

@Serializable
data class RuleConfig(
    val logic: String = "AND",
    val conditions: List<RuleCondition> = emptyList(),
)

@Serializable
data class RuleCondition(
    val field: String = "MERCHANT",
    val operator: String = "CONTAINS",
    val value: String = "",
)

@Serializable
data class RuleAction(
    val actionType: String = "SET_CATEGORY",
    val value: String = "",
)

@Serializable
data class RuleActionConfig(
    val actions: List<RuleAction> = emptyList(),
)

enum class RuleField {
    AMOUNT, TYPE, CATEGORY, MERCHANT, NARRATION, RAW_TEXT, INSTITUTION, SOURCE,
    HOUR, DAY_OF_WEEK, DAY_OF_MONTH, DATE,
}

enum class RuleOperator {
    CONTAINS, NOT_CONTAINS, EQUALS, NOT_EQUALS, STARTS_WITH, ENDS_WITH,
    GREATER_THAN, LESS_THAN, BEFORE, AFTER,
}

enum class RuleActionType {
    SET_CATEGORY, SET_MERCHANT, SET_DIRECTION, APPEND_NARRATION,
    PREPEND_NARRATION, CLEAR_MERCHANT, BLOCK,
}

data class RuleContext(
    val amount: java.math.BigDecimal,
    val currency: String,
    val direction: String,
    val category: String,
    val merchant: String?,
    val narration: String?,
    val rawText: String,
    val institution: String?,
    val source: String,
    val occurredAt: Long,
)

data class RuleDraft(
    val amount: java.math.BigDecimal,
    val currency: String,
    val direction: String,
    val category: String,
    val merchant: String?,
    val narration: String?,
    val institution: String?,
    val reference: String?,
    val occurredAt: Long,
)

data class RuleOutcome(
    val draft: RuleDraft,
    val blocked: Boolean = false,
    val matchedRuleIds: List<Long> = emptyList(),
    val blockedByRuleId: Long? = null,
)
