package com.catetin.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catetin.app.data.db.RuleEntity
import com.catetin.app.data.repo.FinanceRepository
import com.catetin.app.domain.rules.RuleActionConfig
import com.catetin.app.domain.rules.RuleConfig
import com.catetin.app.domain.rules.RuleDraft
import com.catetin.app.domain.rules.RuleEngine
import com.catetin.app.util.Dates
import com.catetin.app.util.PeriodPreset
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RulesViewModel @Inject constructor(
    private val repository: FinanceRepository,
) : ViewModel() {
    val rules: StateFlow<List<RuleEntity>> = repository.rules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggle(rule: RuleEntity, enabled: Boolean) {
        viewModelScope.launch { repository.updateRule(rule.copy(enabled = enabled)) }
    }

    fun delete(rule: RuleEntity) {
        viewModelScope.launch { repository.deleteRule(rule.id) }
    }

    fun save(
        ruleId: Long?,
        name: String,
        config: RuleConfig,
        actions: RuleActionConfig,
        onSaved: () -> Unit,
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            if (ruleId == null) {
                repository.saveRule(
                    name = name.trim(),
                    conditionsJson = RuleEngine.json.encodeToString(RuleConfig.serializer(), config),
                    actionsJson = RuleEngine.json.encodeToString(RuleActionConfig.serializer(), actions),
                )
            } else {
                val existing = rules.value.firstOrNull { it.id == ruleId }
                if (existing != null) {
                    repository.updateRule(
                        existing.copy(
                            name = name.trim(),
                            conditionsJson = RuleEngine.json.encodeToString(RuleConfig.serializer(), config),
                            actionsJson = RuleEngine.json.encodeToString(RuleActionConfig.serializer(), actions),
                        ),
                    )
                }
            }
            onSaved()
        }
    }

    fun preview(config: RuleConfig, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val range = Dates.range(PeriodPreset.ALL)
            val transactions = repository.getTransactionsBetween(range.start, range.end)
            val count = transactions.count { transaction ->
                RuleEngine.matches(
                    config,
                    com.catetin.app.domain.rules.RuleContext(
                        amount = transaction.amount.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO,
                        currency = transaction.currency,
                        direction = transaction.direction,
                        category = transaction.category,
                        merchant = transaction.merchant,
                        narration = transaction.narration,
                        rawText = transaction.narration.orEmpty(),
                        institution = transaction.institution,
                        source = "MANUAL",
                        occurredAt = transaction.occurredAt,
                    ),
                )
            }
            onResult(count)
        }
    }
}
