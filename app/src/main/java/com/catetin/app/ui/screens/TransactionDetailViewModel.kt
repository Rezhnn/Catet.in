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
)

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val repository: FinanceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TransactionDetailState())
    val state: StateFlow<TransactionDetailState> = _state.asStateFlow()
    private var job: Job? = null

    fun load(id: Long) {
        if (job?.isActive == true) return
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
        viewModelScope.launch {
            repository.deleteTransaction(id)
            onDone()
        }
    }
}
