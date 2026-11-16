package com.catetin.app.domain.rules

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.serialization.json.Json

object RuleEngine {

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun decodeConditions(raw: String): RuleConfig =
        runCatching { json.decodeFromString<RuleConfig>(raw) }.getOrDefault(RuleConfig())

    fun decodeActions(raw: String): RuleActionConfig =
        runCatching { json.decodeFromString<RuleActionConfig>(raw) }.getOrDefault(RuleActionConfig())

    fun apply(
        rules: List<Pair<Long, Pair<RuleConfig, RuleActionConfig>>>,
        draft: RuleDraft,
        rawText: String,
        source: String,
    ): RuleOutcome {
        var current = draft
        var blocked = false
        var blockedBy: Long? = null
        val matched = mutableListOf<Long>()

        for ((id, config) in rules) {
            if (blocked) break
            val context = contextOf(current, rawText, source)
            if (!matches(config.first, context)) continue
            matched += id
            for (action in config.second.actions) {
                when (action.actionType.uppercase(Locale.ROOT)) {
                    RuleActionType.SET_CATEGORY.name -> current = current.copy(category = action.value.ifBlank { current.category })
                    RuleActionType.SET_MERCHANT.name -> current = current.copy(merchant = action.value.ifBlank { current.merchant })
                    RuleActionType.SET_DIRECTION.name -> {
                        val next = action.value.uppercase(Locale.ROOT)
                        if (next in setOf("EXPENSE", "INCOME", "TRANSFER")) {
                            current = current.copy(direction = next)
                        }
                    }
                    RuleActionType.APPEND_NARRATION.name -> current = current.copy(
                        narration = listOfNotNull(current.narration, action.value).joinToString(" ").trim(),
                    )
                    RuleActionType.PREPEND_NARRATION.name -> current = current.copy(
                        narration = listOfNotNull(action.value, current.narration).joinToString(" ").trim(),
                    )
                    RuleActionType.CLEAR_MERCHANT.name -> current = current.copy(merchant = null)
                    RuleActionType.BLOCK.name -> {
                        blocked = true
                        blockedBy = id
                    }
                }
            }
        }
        return RuleOutcome(current, blocked, matched, blockedBy)
    }

    fun matches(config: RuleConfig, context: RuleContext): Boolean {
        if (config.conditions.isEmpty()) return false
        val results = config.conditions.map { matches(it, context) }
        return if (config.logic.equals("OR", ignoreCase = true)) {
            results.any { it }
        } else {
            results.all { it }
        }
    }

    private fun matches(condition: RuleCondition, context: RuleContext): Boolean {
        val field = runCatching { RuleField.valueOf(condition.field.uppercase(Locale.ROOT)) }.getOrNull()
            ?: return false
        val operator = runCatching { RuleOperator.valueOf(condition.operator.uppercase(Locale.ROOT)) }.getOrNull()
            ?: return false
        val expected = condition.value.trim()

        return when (field) {
            RuleField.AMOUNT -> compareNumber(context.amount, expected, operator)
            RuleField.HOUR -> compareNumber(
                BigDecimal(Instant.ofEpochMilli(context.occurredAt).atZone(ZoneId.systemDefault()).hour),
                expected,
                operator,
            )
            RuleField.DAY_OF_WEEK -> compareNumber(
                BigDecimal(Instant.ofEpochMilli(context.occurredAt).atZone(ZoneId.systemDefault()).dayOfWeek.value.toLong()),
                expected,
                operator,
            )
            RuleField.DAY_OF_MONTH -> compareNumber(
                BigDecimal(Instant.ofEpochMilli(context.occurredAt).atZone(ZoneId.systemDefault()).dayOfMonth.toLong()),
                expected,
                operator,
            )
            RuleField.DATE -> compareDate(
                Instant.ofEpochMilli(context.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate(),
                expected,
                operator,
            )
            RuleField.TYPE -> compareText(context.direction, expected, operator)
            RuleField.CATEGORY -> compareText(context.category, expected, operator)
            RuleField.MERCHANT -> compareText(context.merchant.orEmpty(), expected, operator)
            RuleField.NARRATION -> compareText(context.narration.orEmpty(), expected, operator)
            RuleField.RAW_TEXT -> compareText(context.rawText, expected, operator)
            RuleField.INSTITUTION -> compareText(context.institution.orEmpty(), expected, operator)
            RuleField.SOURCE -> compareText(context.source, expected, operator)
        }
    }

    private fun compareText(actual: String, expected: String, operator: RuleOperator): Boolean {
        val a = actual.lowercase(Locale.ROOT).trim()
        val e = expected.lowercase(Locale.ROOT).trim()
        return when (operator) {
            RuleOperator.CONTAINS -> a.contains(e)
            RuleOperator.NOT_CONTAINS -> !a.contains(e)
            RuleOperator.EQUALS -> a == e
            RuleOperator.NOT_EQUALS -> a != e
            RuleOperator.STARTS_WITH -> a.startsWith(e)
            RuleOperator.ENDS_WITH -> a.endsWith(e)
            RuleOperator.BEFORE, RuleOperator.AFTER, RuleOperator.GREATER_THAN, RuleOperator.LESS_THAN -> false
        }
    }

    private fun compareNumber(actual: BigDecimal, expected: String, operator: RuleOperator): Boolean {
        val value = expected.replace("Rp", "").replace(" ", "").replace(".", "").replace(",", ".")
            .toBigDecimalOrNull() ?: return false
        val comparison = actual.compareTo(value)
        return when (operator) {
            RuleOperator.GREATER_THAN, RuleOperator.AFTER -> comparison > 0
            RuleOperator.LESS_THAN, RuleOperator.BEFORE -> comparison < 0
            RuleOperator.EQUALS -> comparison == 0
            RuleOperator.NOT_EQUALS -> comparison != 0
            else -> false
        }
    }

    private fun compareDate(actual: LocalDate, expected: String, operator: RuleOperator): Boolean {
        val date = runCatching { LocalDate.parse(expected) }.getOrNull() ?: return false
        return when (operator) {
            RuleOperator.BEFORE -> actual.isBefore(date)
            RuleOperator.AFTER -> actual.isAfter(date)
            RuleOperator.EQUALS -> actual == date
            RuleOperator.NOT_EQUALS -> actual != date
            else -> false
        }
    }

    private fun contextOf(draft: RuleDraft, rawText: String, source: String): RuleContext =
        RuleContext(
            amount = draft.amount,
            currency = draft.currency,
            direction = draft.direction,
            category = draft.category,
            merchant = draft.merchant,
            narration = draft.narration,
            rawText = rawText,
            institution = draft.institution,
            source = source,
            occurredAt = draft.occurredAt,
        )
}
