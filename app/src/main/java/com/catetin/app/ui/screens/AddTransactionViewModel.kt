package com.catetin.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catetin.app.data.db.TransactionEntity
import com.catetin.app.data.repo.FinanceRepository
import com.catetin.app.util.CurrencyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AddTransactionState(
    val amount: String = "",
    val direction: String = "EXPENSE",
    val merchant: String = "",
    val category: String = "Lainnya",
    val note: String = "",
    val occurredAt: Long = System.currentTimeMillis(),
    val editingId: Long? = null,
    val error: String? = null,
    val saving: Boolean = false,
)

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val repository: FinanceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AddTransactionState())
    val state: StateFlow<AddTransactionState> = _state.asStateFlow()

    fun setAmount(value: String) { _state.value = _state.value.copy(amount = value.filter { it.isDigit() || it == '.' || it == ',' }, error = null) }
    fun setDirection(value: String) { _state.value = _state.value.copy(direction = value) }
    fun setMerchant(value: String) { _state.value = _state.value.copy(merchant = value) }
    fun setCategory(value: String) { _state.value = _state.value.copy(category = value) }
    fun setNote(value: String) { _state.value = _state.value.copy(note = value) }
    fun setDate(date: LocalDate) {
        _state.value = _state.value.copy(
            occurredAt = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
    }
    fun shiftDate(days: Long) {
        val current = java.time.Instant.ofEpochMilli(_state.value.occurredAt)
            .atZone(ZoneId.systemDefault()).toLocalDate()
        setDate(current.plusDays(days))
    }

    fun load(id: Long) {
        viewModelScope.launch {
            val transaction = repository.getTransaction(id) ?: return@launch
            _state.value = AddTransactionState(
                amount = transaction.amount,
                direction = transaction.direction,
                merchant = transaction.merchant.orEmpty(),
                category = transaction.category,
                note = transaction.narration.orEmpty(),
                occurredAt = transaction.occurredAt,
                editingId = transaction.id,
            )
        }
    }

    fun save(onSaved: () -> Unit) {
        val current = _state.value
        val amount = CurrencyFormatter.parse(current.amount)
        if (amount == null || amount <= java.math.BigDecimal.ZERO) {
            _state.value = current.copy(error = "Nominal belum valid.")
            return
        }
        _state.value = current.copy(saving = true, error = null)
        viewModelScope.launch {
            if (current.editingId != null) {
                val existing = repository.getTransaction(current.editingId)
                if (existing != null) {
                    repository.updateTransaction(
                        existing.copy(
                            amount = amount.stripTrailingZeros().toPlainString(),
                            direction = current.direction,
                            merchant = current.merchant.trim().ifBlank { null },
                            category = current.category,
                            occurredAt = current.occurredAt,
                            narration = current.note.trim().ifBlank { null },
                            status = "CONFIRMED",
                        ),
                    )
                }
            } else {
                repository.addManual(
                    amount = amount,
                    direction = current.direction,
                    merchant = current.merchant.trim().ifBlank { null },
                    category = current.category,
                    occurredAt = current.occurredAt,
                    narration = current.note.trim().ifBlank { null },
                )
            }
            _state.value = _state.value.copy(saving = false)
            onSaved()
        }
    }
}
