package com.catetin.parser.parse

import com.catetin.parser.institution.Institution
import com.catetin.parser.institution.InstitutionRegistry
import com.catetin.parser.model.ParseConfidence
import com.catetin.parser.model.ParseResult
import com.catetin.parser.model.ParsedTransaction
import com.catetin.parser.model.RawArtifact
import com.catetin.parser.model.SourceType
import com.catetin.parser.model.TransactionDirection
import com.catetin.parser.money.AmountParser
import com.catetin.parser.normalize.TextNormalizer
import java.math.BigDecimal
import java.util.Locale

abstract class BaseTransactionParser(
    protected val institution: Institution?,
    private val parserId: String,
) : TransactionParser {

    override val id: String = parserId

    protected open val institutionKeywords: List<String> = emptyList()

    protected open val requiredAnyKeywords: List<String>
        get() = institutionKeywords.ifEmpty { institution?.senderHints ?: emptyList() }

    protected open val supportsEmail: Boolean = true

    private val securityNegatives = listOf(
        "kode otp", "otp", "one time password", "kode verifikasi", "verification code",
        "kode login", "login berhasil", "berhasil login", "masuk ke akun", "new login",
        "reset password", "ubah password", "password berhasil", "pin berhasil diubah",
        "jangan berikan kode",
    )

    private val marketingNegatives = listOf(
        "promo", "voucher", "diskon", "cashback akan", "cashback sebesar", "poin",
        "undian", "hadiah", "berlangganan", "download aplikasi", "update aplikasi",
        "akan jatuh tempo", "segera bayar", "tagihan anda",
    )

    private val completedTransactionCues = listOf(
        "pembayaran berhasil", "pembayaran sukses", "transaksi berhasil", "transaksi sukses",
        "pembelian berhasil", "transfer berhasil", "transfer sukses", "top up berhasil",
        "topup berhasil", "isi saldo berhasil", "dana masuk", "dana diterima", "debit",
        "kredit", "payment successful", "purchase successful", "berhasil dibayar",
    )

    private val failureWords = listOf(
        "gagal", "failed", "dibatalkan", "cancelled", "canceled", "pending", "tertunda",
        "diproses", "processing", "tidak berhasil", "unsuccessful", "expired", "kedaluwarsa",
    )

    private val incomeCues = listOf(
        "dana masuk", "dana diterima", "anda menerima", "kamu menerima", "menerima transfer",
        "uang masuk", "saldo masuk", "kredit", "credit", "refund", "pengembalian dana",
        "dana dikembalikan", "cashback masuk", "bonus masuk", "gaji", "payroll", "received",
    )

    private val expenseCues = listOf(
        "debit", "pembayaran", "pembelian", "payment", "purchase", "transfer ke",
        "transfer berhasil", "tarik tunai", "penarikan", "withdrawal", "belanja",
        "bayar", "qris", "spent", "charged", "dipotong", "pemakaian",
        "transaksi berhasil", "transaksi debit", "pengeluaran", "transaction",
    )

    private val transferCues = listOf(
        "transfer ke", "transfer berhasil", "transfer antar", "top up", "topup",
        "isi saldo", "tarik tunai", "penarikan", "withdrawal", "kirim uang",
    )

    private val financialCues = incomeCues + expenseCues + listOf(
        "transaksi", "pembayaran", "saldo", "rekening", "akun", "tagihan", "invoice",
    )

    override fun canHandle(artifact: RawArtifact): Boolean {
        val lower = TextNormalizer.lowercase(artifact.text + " " + (artifact.title ?: "") + " " + (artifact.subject ?: ""))
        if (artifact.sourceType == SourceType.EMAIL && !supportsEmail) return false
        val packageMatch = institution?.packages?.any { it.equals(artifact.sourceApp, ignoreCase = true) } == true
        val senderMatch = institution?.emailDomains?.any { artifact.sourceApp.lowercase().contains(it) } == true
        val keywordMatch = requiredAnyKeywords.any { lower.contains(it.lowercase()) }
        return packageMatch || senderMatch || keywordMatch
    }

    override fun parse(artifact: RawArtifact): ParseResult {
        val normalized = TextNormalizer.normalize(
            buildString {
                artifact.title?.takeIf { it.isNotBlank() }?.let { append(it).append('\n') }
                append(artifact.text)
            },
        )
        val lower = normalized.lowercase(Locale.ROOT)

        if (securityNegatives.any { lower.contains(it) } && failureWords.none { lower.contains(it) }) {
            return ParseResult.Ignored("non-transactional content")
        }
        if (marketingNegatives.any { lower.contains(it) } &&
            completedTransactionCues.none { lower.contains(it) }
        ) {
            return ParseResult.Ignored("promotional content without a completed transaction")
        }
        if (failureWords.any { lower.contains(it) } && !lower.contains("berhasil")) {
            return ParseResult.Ignored("failed or pending transaction")
        }

        val amountMatch = AmountParser.findPrimary(normalized)
        if (amountMatch == null) {
            return if (financialCues.any { lower.contains(it) }) {
                ParseResult.Unparsed("amount not understood")
            } else {
                ParseResult.Ignored("no financial content")
            }
        }

        val direction = detectDirection(lower, normalized)
        if (direction == null) {
            val hasAction = expenseCues.any { lower.contains(it) } ||
                incomeCues.any { lower.contains(it) } ||
                transferCues.any { lower.contains(it) }
            if (!hasAction && AmountParser.findBalance(normalized) != null) {
                return ParseResult.Ignored("balance-only notification")
            }
            return ParseResult.Unparsed("direction ambiguous")
        }
        if (direction == TransactionDirection.INCOME && lower.contains("saldo") &&
            !incomeCues.any { lower.contains(it) }
        ) {
            return ParseResult.Ignored("balance-only notification")
        }

        val detectedInstitution = institution ?: InstitutionRegistry.detect(normalized, artifact.sourceApp)
        val institutionName = detectedInstitution?.displayName ?: displayNameFromSource(artifact.sourceApp)
        val merchant = extractMerchant(normalized, institutionName)
        val category = categoryFor(normalized, detectedInstitution, merchant)
        val balance = AmountParser.findBalance(normalized)
        val confidence = when {
            merchant == null -> ParseConfidence.MEDIUM
            else -> ParseConfidence.HIGH
        }

        return ParseResult.Parsed(
            ParsedTransaction(
                amount = amountMatch.amount,
                currency = amountMatch.currency,
                direction = direction,
                institution = institutionName,
                institutionId = detectedInstitution?.id ?: "unknown",
                merchant = merchant,
                category = category,
                accountHint = extractAccountHint(normalized),
                reference = extractReference(normalized),
                occurredAtMillis = artifact.timestampMillis,
                narration = normalized.take(400),
                balanceAfter = balance?.amount?.takeIf { balance.currency == amountMatch.currency },
                parserId = id,
                confidence = confidence,
            ),
        )
    }

    private fun detectDirection(lower: String, original: String): TransactionDirection? {
        if (incomeCues.any { lower.contains(it) }) return TransactionDirection.INCOME
        if (transferCues.any { lower.contains(it) }) return TransactionDirection.TRANSFER
        if (expenseCues.any { lower.contains(it) }) return TransactionDirection.EXPENSE
        if (lower.contains("saldo") && AmountParser.findBalance(original) != null) return null
        return null
    }

    private fun categoryFor(
        text: String,
        detected: Institution?,
        merchant: String?,
    ): String {
        val guessed = CategoryGuesser.guess(listOfNotNull(text, merchant).joinToString(" "))
        if (guessed != "Lainnya") return guessed
        return detected?.defaultCategory ?: "Lainnya"
    }

    private fun extractMerchant(text: String, institutionName: String): String? {
        val emailMerchant = Regex(
            """(?im)(?:nama\s+)?merchant\s*[:\-]\s*([^\n]{2,60})""",
        ).find(text)?.groupValues?.get(1)
        if (!emailMerchant.isNullOrBlank()) return cleanMerchant(emailMerchant, institutionName)

        val storeMerchant = Regex(
            """(?im)(?:di|at|kepada|to)\s+([A-Z0-9][A-Za-z0-9 .&'\-_/]{2,48})""",
        ).findAll(text).mapNotNull { cleanMerchant(it.groupValues[1], institutionName) }
            .firstOrNull { candidate -> candidate.any { ch -> ch.isLetter() } }
        if (storeMerchant != null) return storeMerchant

        val subjectMerchant = Regex(
            """(?im)(?:pembayaran|payment|purchase|transaksi)\s+(?:ke|di|at)\s+([^\n]{2,50})""",
        ).find(text)?.groupValues?.get(1)
        return subjectMerchant?.let { cleanMerchant(it, institutionName) }
    }

    private fun cleanMerchant(raw: String, institutionName: String): String? {
        var value = raw.lineSequence().first().trim()
        val stop = Regex(
            """(?i)\s+(?:berhasil|sukses|saldo|tgl|tanggal|pukul|jam|ref|no\.?|nomor|dengan|sebesar|senilai|pada|wib|west indonesia|melalui|via|dari|untuk)\b.*""",
        )
        value = value.replace(stop, "").trim(' ', '.', ',', ';', ':', '-', '|', '*')
        if (value.length < 2) return null
        if (value.equals(institutionName, ignoreCase = true)) return null
        if (value.all { it.isDigit() || it == '*' || it == 'x' || it == 'X' }) return null
        if (value.lowercase() in setOf("anda", "kamu", "rekening", "akun", "merchant", "toko")) return null
        return value.take(60)
    }

    private fun extractAccountHint(text: String): String? {
        val patterns = listOf(
            Regex("""(?i)(?:rekening|akun|kartu|card|account)[^0-9]{0,16}(?:\*+|[xX]+|\.{3})?\s*(\d{4})(?!\d)"""),
            Regex("""(?i)(?:\*{2,}|[xX]{2,}|•{2,})\s*(\d{4})(?!\d)"""),
        )
        return patterns.firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1) }
    }

    private fun extractReference(text: String): String? {
        val pattern = Regex(
            """(?i)\b(?:nomor\s+referensi|no\.?\s*ref|referensi|reference|ref|transaction\s*id|order\s*id|invoice)\b[\s:#-]*([A-Z0-9][A-Z0-9._-]{3,40})""",
        )
        return pattern.find(text)?.groupValues?.get(1)?.trimEnd('.', ',', ';')
    }

    private fun displayNameFromSource(source: String): String {
        return source.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
}
