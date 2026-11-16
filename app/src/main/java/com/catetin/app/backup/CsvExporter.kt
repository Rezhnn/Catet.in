package com.catetin.app.backup

import com.catetin.app.data.db.TransactionDao
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CsvExporter @Inject constructor(
    private val transactionDao: TransactionDao,
) {
    suspend fun createCsv(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)
            .withZone(ZoneId.systemDefault())
        val builder = StringBuilder("\uFEFF")
        builder.append("tanggal,jenis,nominal,mata_uang,kategori,merchant,institusi,status,catatan,referensi\r\n")
        transactionDao.getAll().forEach { t ->
            listOf(
                formatter.format(Instant.ofEpochMilli(t.occurredAt)),
                when (t.direction) {
                    "INCOME" -> "Pemasukan"
                    "TRANSFER" -> "Transfer"
                    else -> "Pengeluaran"
                },
                t.amount,
                t.currency,
                t.category,
                t.merchant.orEmpty(),
                t.institution.orEmpty(),
                when (t.status) {
                    "NEEDS_REVIEW" -> "Perlu ditinjau"
                    else -> "Terkonfirmasi"
                },
                t.narration.orEmpty(),
                t.reference.orEmpty(),
            ).joinToString(",") { escape(it) }.also { builder.append(it).append("\r\n") }
        }
        return builder.toString()
    }

    private fun escape(value: String): String {
        val safe = value.replace("\r", " ").replace("\n", " ").take(2_000)
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }
}
