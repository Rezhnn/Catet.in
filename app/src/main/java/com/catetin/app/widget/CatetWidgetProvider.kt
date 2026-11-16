package com.catetin.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.catetin.app.MainActivity
import com.catetin.app.R
import com.catetin.app.data.db.TransactionEntity
import com.catetin.app.data.repo.FinanceRepository
import com.catetin.app.util.CurrencyFormatter
import com.catetin.app.util.Dates
import com.catetin.app.util.PeriodPreset
import dagger.hilt.android.AndroidEntryPoint
import java.math.BigDecimal
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CatetWidgetProvider : AppWidgetProvider() {
    @Inject lateinit var repository: FinanceRepository

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val transactions = repository.observeTransactions(PeriodPreset.THIS_MONTH).first()
                val budgets = repository.budgets.first()
                val expense = transactions
                    .filter { it.direction == "EXPENSE" && it.currency == "IDR" }
                    .sumAmount()
                val openIntent = PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                appWidgetIds.forEach { id ->
                    val views = RemoteViews(context.packageName, R.layout.widget_catet).apply {
                        setTextViewText(R.id.widget_title, "Catet.in · " + Dates.monthLabel())
                        setTextViewText(R.id.widget_amount, CurrencyFormatter.format(expense, "IDR"))
                        setTextViewText(
                            R.id.widget_caption,
                            transactions.count { it.direction == "EXPENSE" }.toString() + " pengeluaran dicatat",
                        )
                        val budget = budgets.firstOrNull { it.currency == "IDR" }
                        if (budget != null) {
                            val limit = budget.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                            val spent = transactions.filter {
                                it.direction == "EXPENSE" &&
                                    it.currency == budget.currency &&
                                    (budget.category == null || it.category == budget.category)
                            }.sumAmount()
                            val percent = if (limit > BigDecimal.ZERO) {
                                (spent.multiply(BigDecimal(100)).divide(limit, 0, java.math.RoundingMode.HALF_UP)).toInt()
                            } else 0
                            setProgressBar(R.id.widget_progress, 100, percent.coerceIn(0, 100), false)
                            setTextViewText(
                                R.id.widget_budget,
                                budget.name + " " + percent + "% · " + CurrencyFormatter.format(spent, budget.currency),
                            )
                        } else {
                            setViewVisibility(R.id.widget_progress, android.view.View.GONE)
                            setTextViewText(R.id.widget_budget, "Ketuk untuk membuka Catet.in")
                        }
                        setOnClickPendingIntent(R.id.widget_amount, openIntent)
                        setOnClickPendingIntent(R.id.widget_budget, openIntent)
                    }
                    manager.updateAppWidget(id, views)
                }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CatetWidgetProvider::class.java))
            if (ids.isNotEmpty()) onUpdate(context, manager, ids)
        }
    }

    companion object {
        private const val ACTION_REFRESH = "com.catetin.app.action.REFRESH_WIDGET"

        fun refresh(context: Context) {
            context.sendBroadcast(
                Intent(context, CatetWidgetProvider::class.java).setAction(ACTION_REFRESH),
            )
        }
    }
}

private fun List<TransactionEntity>.sumAmount(): BigDecimal =
    fold(BigDecimal.ZERO) { total, item -> total + (item.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO) }
