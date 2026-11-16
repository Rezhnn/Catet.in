package com.catetin.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catetin.app.data.db.ReviewItemEntity
import com.catetin.app.data.db.SourceEventEntity
import com.catetin.app.data.db.TransactionEntity
import com.catetin.app.data.repo.FinanceRepository
import com.catetin.app.util.CurrencyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReviewUiItem(
    val review: ReviewItemEntity,
    val transaction: TransactionEntity? = null,
    val candidate: TransactionEntity? = null,
    val source: SourceEventEntity? = null,
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val repository: FinanceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<List<ReviewUiItem>>(emptyList())
    val state: StateFlow<List<ReviewUiItem>> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.pendingReviews.collect { items ->
                _state.value = items.map { item ->
                    ReviewUiItem(
                        review = item,
                        transaction = item.transactionId?.let { repository.getTransaction(it) },
                        candidate = item.candidateTransactionId?.let { repository.getTransaction(it) },
                        source = item.sourceEventId?.let { repository.getSourceEvent(it) },
                    )
                }
            }
        }
    }

    fun merge(id: Long) = resolve(id, "MERGE")
    fun keepBoth(id: Long) = resolve(id, "KEPT_BOTH")
    fun dismiss(id: Long) = resolve(id, "DISMISSED")

    private fun resolve(id: Long, resolution: String) {
        viewModelScope.launch { repository.resolveReview(id, resolution) }
    }

    fun createFromUnparsed(
        item: ReviewUiItem,
        amountText: String,
        direction: String,
        merchant: String,
        category: String,
        onDone: () -> Unit,
    ) {
        val amount = CurrencyFormatter.parse(amountText) ?: return
        if (amount <= BigDecimal.ZERO) return
        val sourceId = item.review.sourceEventId ?: return
        viewModelScope.launch {
            repository.createTransactionFromUnparsed(sourceId, amount, direction, merchant, category)
            repository.resolveReview(item.review.id, "MANUAL_CREATED")
            onDone()
        }
    }
}
