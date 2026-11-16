package com.catetin.app.ai.router

import kotlinx.serialization.Serializable

enum class IntentMetric { SPENDING, INCOME, BALANCE, LARGEST, COUNT, BREAKDOWN }

enum class IntentPeriod { TODAY, THIS_WEEK, LAST_WEEK, THIS_MONTH, LAST_MONTH, THIS_YEAR, LAST_7_DAYS, ALL }

@Serializable
data class QueryIntent(
    val metric: IntentMetric = IntentMetric.SPENDING,
    val period: IntentPeriod = IntentPeriod.THIS_MONTH,
    val category: String? = null,
    val direction: String? = null,
    val limit: Int = 0,
)
