package com.catetin.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catetin.app.data.db.TransactionEntity
import com.catetin.app.data.repo.FinanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val repository: FinanceRepository,
) : ViewModel() {

    val query = MutableStateFlow("")
    val type = MutableStateFlow<String?>(null)
    val category = MutableStateFlow<String?>(null)

    val transactions: StateFlow<List<TransactionEntity>> = combine(
        query.debounce(180),
        type,
        category,
    ) { q, t, c -> Triple(q, t, c) }
        .flatMapLatest { (q, t, c) -> repository.searchTransactions(q, t, c) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) { query.value = value }
    fun setType(value: String?) { type.value = value }
    fun setCategory(value: String?) { category.value = value }
}
