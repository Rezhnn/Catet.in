package com.catetin.parser.institution

enum class InstitutionKind { BANK, DIGITAL_BANK, WALLET, MARKETPLACE, OTHER }

data class Institution(
    val id: String,
    val displayName: String,
    val kind: InstitutionKind,
    val packages: List<String> = emptyList(),
    val senderHints: List<String> = emptyList(),
    val emailDomains: List<String> = emptyList(),
    val defaultCategory: String = "Lainnya",
)

object InstitutionRegistry {

    val all: List<Institution> = listOf(
        Institution(
            id = "bca",
            displayName = "BCA",
            kind = InstitutionKind.BANK,
            packages = listOf("com.bca", "com.bca.mybca.omni.android"),
            senderHints = listOf("BCA", "HALO BCA"),
            emailDomains = listOf("bca.co.id"),
        ),
        Institution(
            id = "mandiri",
            displayName = "Mandiri",
            kind = InstitutionKind.BANK,
            packages = listOf("id.bmri.livin"),
            senderHints = listOf("MANDIRI", "LIVIN"),
            emailDomains = listOf("bankmandiri.co.id", "mandiri.co.id"),
        ),
        Institution(
            id = "bri",
            displayName = "BRI",
            kind = InstitutionKind.BANK,
            packages = listOf("id.co.bri.brimo"),
            senderHints = listOf("BRI", "BRIMO"),
            emailDomains = listOf("bri.co.id"),
        ),
        Institution(
            id = "bni",
            displayName = "BNI",
            kind = InstitutionKind.BANK,
            packages = listOf("id.co.bni.mobilebanking"),
            senderHints = listOf("BNI"),
            emailDomains = listOf("bni.co.id"),
        ),
        Institution(
            id = "seabank",
            displayName = "SeaBank",
            kind = InstitutionKind.DIGITAL_BANK,
            packages = listOf("com.seabank.app", "id.co.seabank"),
            senderHints = listOf("SEABANK", "SEA BANK"),
            emailDomains = listOf("seabank.co.id"),
        ),
        Institution(
            id = "jago",
            displayName = "Jago",
            kind = InstitutionKind.DIGITAL_BANK,
            packages = listOf("com.jago.digitalbanking"),
            senderHints = listOf("JAGO", "BANK JAGO"),
            emailDomains = listOf("jago.com"),
        ),
        Institution(
            id = "jenius",
            displayName = "Jenius",
            kind = InstitutionKind.DIGITAL_BANK,
            packages = listOf("com.btpn.dc", "com.btpn.jenius"),
            senderHints = listOf("JENIUS", "BTN"),
            emailDomains = listOf("jenius.com", "btpn.com"),
        ),
        Institution(
            id = "gopay",
            displayName = "GoPay",
            kind = InstitutionKind.WALLET,
            packages = listOf("com.gojek.app", "com.gojek.gopay"),
            senderHints = listOf("GOPAY", "GOJEK", "GO-PAY"),
            emailDomains = listOf("gojek.com", "gopay.co.id"),
            defaultCategory = "Makanan & Minuman",
        ),
        Institution(
            id = "ovo",
            displayName = "OVO",
            kind = InstitutionKind.WALLET,
            packages = listOf("id.co.ovo"),
            senderHints = listOf("OVO"),
            emailDomains = listOf("ovo.id"),
        ),
        Institution(
            id = "dana",
            displayName = "DANA",
            kind = InstitutionKind.WALLET,
            packages = listOf("id.dana"),
            senderHints = listOf("DANA"),
            emailDomains = listOf("dana.id"),
        ),
        Institution(
            id = "shopeepay",
            displayName = "ShopeePay",
            kind = InstitutionKind.WALLET,
            packages = listOf("com.shopee.id", "com.shopeepay.id"),
            senderHints = listOf("SHOPEEPAY", "SHOPEE"),
            emailDomains = listOf("shopee.co.id"),
        ),
        Institution(
            id = "linkaja",
            displayName = "LinkAja",
            kind = InstitutionKind.WALLET,
            packages = listOf("id.co.linkaja"),
            senderHints = listOf("LINKAJA"),
            emailDomains = listOf("linkaja.id"),
        ),
        Institution(
            id = "tokopedia",
            displayName = "Tokopedia",
            kind = InstitutionKind.MARKETPLACE,
            packages = listOf("com.tokopedia.tkpd"),
            senderHints = listOf("TOKOPEDIA"),
            emailDomains = listOf("tokopedia.com"),
        ),
        Institution(
            id = "grab",
            displayName = "Grab",
            kind = InstitutionKind.WALLET,
            packages = listOf("com.grabtaxi.passenger"),
            senderHints = listOf("GRAB"),
            emailDomains = listOf("grab.com"),
        ),
    )

    private val byPackage = all.flatMap { inst -> inst.packages.map { it.lowercase() to inst } }.toMap()

    fun byPackage(packageName: String): Institution? =
        byPackage[packageName.lowercase()]

    fun bySender(sender: String): Institution? {
        val normalized = sender.lowercase()
        return all.firstOrNull { inst ->
            inst.emailDomains.any { normalized.contains(it) } ||
                inst.senderHints.any { hint -> normalized.contains(hint.lowercase()) }
        }
    }

    fun detect(text: String, sourceApp: String): Institution? {
        byPackage(sourceApp)?.let { return it }
        val lower = text.lowercase()
        return all.firstOrNull { inst ->
            inst.senderHints.any { hint -> lower.contains(hint.lowercase()) } ||
                inst.emailDomains.any { lower.contains(it) }
        }
    }

    fun defaultAllowlist(): List<Pair<String, String>> =
        all.flatMap { inst -> inst.packages.map { it to inst.displayName } }
            .distinctBy { it.first }
}
