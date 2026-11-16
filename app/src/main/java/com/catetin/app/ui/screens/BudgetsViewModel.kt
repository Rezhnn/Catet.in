package com.catetin.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catetin.app.data.db.BudgetEntity
import com.catetin.app.data.repo.FinanceRepository
import com.catetin.app.util.CurrencyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val repository: FinanceRepository,
) : ViewModel() {

    fun addBudget(name: String, amountText: String, category: String?, type: String, onDone: () -> Unit) {
        val amount = CurrencyFormatter.parse(amountText) ?: return
        if (amount <= java.math.BigDecimal.ZERO || name.isBlank()) return
        viewModelScope.launch {
            repository.saveBudget(
                BudgetEntity(
                    name = name.trim(),
                    type = type,
                    amount = amount.stripTrailingZeros().toPlainString(),
                    currency = "IDR",
                    category = category,
                    period = "MONTHLY",
                ),
            )
            onDone()
        }
    }

    fun deleteBudget(budget: BudgetEntity) {
        viewModelScope.launch { repository.deleteBudget(budget.id) }
    }
}
