package com.catetin.app.ai.router

import java.util.Locale

/**
 * Fast deterministic Indonesian intent reader. It runs before the LLM (and
 * works when the model is not downloaded at all). It only returns an intent
 * when it recognises both a metric and a period/category cue, so greetings and
 * free-form conversation stay on the model path.
 */
object IndonesianKeywordIntentRouter {

    private val metricWords: Map<IntentMetric, List<String>> = mapOf(
        IntentMetric.BALANCE to listOf("saldo", "balance", "sisa uang"),
        IntentMetric.INCOME to listOf("pemasukan", "pendapatan", "penghasilan", "income", "gaji", "uang masuk"),
        IntentMetric.LARGEST to listOf("terbesar", "paling besar", "termahal", "paling mahal", "largest"),
        IntentMetric.COUNT to listOf("berapa transaksi", "jumlah transaksi", "banyak transaksi", "how many"),
        IntentMetric.BREAKDOWN to listOf("rincian", "per kategori", "breakdown", "kategori pengeluaran"),
        IntentMetric.SPENDING to listOf(
            "pengeluaran", "belanja", "jajan", "keluar", "habis", "spending", "biaya", "pengeluaran",
        ),
    )

    private val periodWords: List<Pair<IntentPeriod, List<String>>> = listOf(
        IntentPeriod.TODAY to listOf("hari ini", "today"),
        IntentPeriod.LAST_WEEK to listOf("minggu lalu", "pekan lalu", "last week"),
        IntentPeriod.THIS_WEEK to listOf("minggu ini", "pekan ini", "this week"),
        IntentPeriod.LAST_MONTH to listOf("bulan lalu", "last month"),
        IntentPeriod.THIS_MONTH to listOf("bulan ini", "this month"),
        IntentPeriod.THIS_YEAR to listOf("tahun ini", "this year"),
        IntentPeriod.LAST_7_DAYS to listOf("7 hari", "tujuh hari", "seminggu terakhir", "last 7 days"),
    )

    private val categoryWords: List<Pair<String, List<String>>> = listOf(
        "Makanan & Minuman" to listOf(
            "makan", "makanan", "jajan", "kopi", "cafe", "kafe", "restoran", "food", "minuman",
            "gofood", "grabfood", "shopeefood",
        ),
        "Transportasi" to listOf(
            "transport", "transportasi", "ojek", "gojek", "grab", "bensin", "tol", "parkir",
            "krl", "mrt", "lrt",
        ),
        "Belanja" to listOf("belanja", "shopping", "shopee", "tokopedia", "lazada", "marketplace"),
        "Tagihan" to listOf("tagihan", "listrik", "pln", "air", "pdam", "internet", "indihome", "bpjs"),
        "Pulsa & Data" to listOf("pulsa", "data", "kuota", "telkomsel", "indosat", "xl"),
        "Hiburan" to listOf("hiburan", "bioskop", "netflix", "spotify", "game", "konser"),
        "Kesehatan" to listOf("kesehatan", "apotek", "dokter", "klinik", "rumah sakit", "obat"),
        "Pendidikan" to listOf("pendidikan", "sekolah", "kuliah", "kursus", "buku"),
        "Transfer" to listOf("transfer", "kirim uang"),
    )

    fun match(question: String): QueryIntent? {
        val text = question.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9\\s&]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (text.length < 9) return null

        val metric = metricWords.entries.firstOrNull { entry ->
            entry.value.any { text.contains(it) }
        }?.key ?: return null

        val period = periodWords.firstOrNull { (_, words) -> words.any { text.contains(it) } }?.first
        val category = categoryWords.firstOrNull { (_, words) -> words.any { text.contains(it) } }?.first

        if (period == null && category == null && !text.contains("saldo")) return null
        if (metric == IntentMetric.BALANCE) {
            return QueryIntent(metric = IntentMetric.BALANCE, period = IntentPeriod.ALL)
        }

        val direction = when (metric) {
            IntentMetric.INCOME -> "INCOME"
            IntentMetric.SPENDING, IntentMetric.LARGEST, IntentMetric.BREAKDOWN -> "EXPENSE"
            else -> null
        }
        return QueryIntent(
            metric = metric,
            period = period ?: IntentPeriod.THIS_MONTH,
            category = category,
            direction = direction,
            limit = if (metric == IntentMetric.LARGEST) 1 else 0,
        )
    }

    fun examples(): List<String> = listOf(
        "Pengeluaran bulan ini",
        "Belanja makan minggu lalu",
        "Pemasukan bulan ini",
        "Total jajan hari ini",
        "Pengeluaran transportasi minggu ini",
        "Transaksi terbesar bulan ini",
        "Saldo saat ini",
    )
}
