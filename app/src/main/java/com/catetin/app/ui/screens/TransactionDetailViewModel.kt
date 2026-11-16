package com.catetin.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catetin.app.data.db.SourceEventEntity
import com.catetin.app.data.db.TransactionEntity
import com.catetin.app.data.repo.FinanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class TransactionDetailState(
    val transaction: TransactionEntity? = null,
    val sources: List<SourceEventEntity> = emptyList(),
    val loading: Boolean = true,
    val deleting: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val repository: FinanceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TransactionDetailState())
    val state: StateFlow<TransactionDetailState> = _state.asStateFlow()
    private var job: Job? = null

    fun load(id: Long) {
        if (loadedId == id && job?.isActive == true) return
        job?.cancel()
        loadedId = id
        _state.value = TransactionDetailState()
        job = viewModelScope.launch {
            combine(
                repository.observeTransaction(id),
                repository.observeSources(id),
            ) { transaction, sources ->
                TransactionDetailState(transaction, sources, loading = false)
            }.collect { _state.value = it }
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = _state.value.transaction?.id ?: return
        if (_state.value.deleting) return
        _state.value = _state.value.copy(deleting = true, error = null)
        viewModelScope.launch {
            try {
                repository.deleteTransaction(id)
                onDone()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _state.value = _state.value.copy(
                    deleting = false,
                    error = "Transaksi belum dapat dihapus. Coba lagi.",
                )
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private var loadedId: Long? = null
}
