package com.catetin.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catetin.app.data.db.BudgetEntity
import com.catetin.app.data.repo.FinanceRepository
import com.catetin.app.domain.budget.BudgetMath
import com.catetin.app.util.CurrencyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BudgetEditorState(
    val saving: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val repository: FinanceRepository,
) : ViewModel() {

    private val _editorState = MutableStateFlow(BudgetEditorState())
    val editorState: StateFlow<BudgetEditorState> = _editorState.asStateFlow()

    fun saveBudget(
        existing: BudgetEntity?,
        name: String,
        amountText: String,
        category: String?,
        type: String,
        onDone: () -> Unit,
    ) {
        if (_editorState.value.saving) return
        val amount = CurrencyFormatter.parse(amountText) ?: BigDecimal.ZERO
        val cleanName = name.trim()
        val cleanType = type.takeIf { it in setOf(BudgetMath.LIMIT, BudgetMath.EXPECTED, BudgetMath.TARGET) }
            ?: BudgetMath.LIMIT
        val validation = when {
            cleanName.isBlank() -> "Nama rencana belum diisi."
            amount <= BigDecimal.ZERO -> "Nominal harus lebih dari Rp0."
            else -> null
        }
        if (validation != null) {
            _editorState.value = BudgetEditorState(error = validation)
            return
        }

        _editorState.value = BudgetEditorState(saving = true)
        viewModelScope.launch {
            val budget = (existing ?: BudgetEntity(name = cleanName, amount = "0")).copy(
                name = cleanName,
                type = cleanType,
                amount = amount.stripTrailingZeros().toPlainString(),
                currency = "IDR",
                category = category?.takeIf { cleanType != BudgetMath.TARGET },
                period = "MONTHLY",
            )
            runCatching { repository.saveBudget(budget) }
                .onSuccess {
                    _editorState.value = BudgetEditorState()
                    onDone()
                }
                .onFailure {
                    _editorState.value = BudgetEditorState(
                        error = "Rencana belum tersimpan. Periksa ruang penyimpanan lalu coba lagi.",
                    )
                }
        }
    }

    fun deleteBudget(budget: BudgetEntity, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.deleteBudget(budget.id) }
                .onSuccess { onDone() }
                .onFailure {
                    _editorState.value = BudgetEditorState(
                        error = "Rencana belum dapat dihapus. Coba lagi.",
                    )
                }
        }
    }

    fun clearError() {
        if (!_editorState.value.saving) _editorState.value = BudgetEditorState()
    }
}
