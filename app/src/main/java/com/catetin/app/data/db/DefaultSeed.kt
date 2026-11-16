package com.catetin.app.data.db

import com.catetin.parser.institution.InstitutionRegistry
import com.catetin.parser.parse.CategoryGuesser

object DefaultSeed {
    private val colors = listOf(
        "#D95F4B", "#3C7DA6", "#E0A23A", "#4E8B57", "#7D64A8",
        "#C46A8A", "#4C8C8C", "#8B7355", "#5B7C99", "#7A8B47",
        "#A86B3C", "#5D6B67",
    )

    val categories: List<CategoryEntity> = CategoryGuesser.defaults.mapIndexed { index, (name, icon) ->
        CategoryEntity(
            name = name,
            icon = icon,
            color = colors[index % colors.size],
            isDefault = true,
            isIncome = name == "Gaji",
            sortOrder = index,
        )
    }

    val allowlist: List<AllowlistEntity> = InstitutionRegistry.defaultAllowlist().map { (packageName, label) ->
        AllowlistEntity(packageName = packageName, label = label, enabled = true, addedBy = "DEFAULT")
    }

    val gmailSenders: List<GmailSenderEntity> = listOf(
        "no-reply@bca.co.id" to "BCA",
        "noreply@bca.co.id" to "BCA",
        "no-reply@bankmandiri.co.id" to "Mandiri",
        "noreply@bri.co.id" to "BRI",
        "no-reply@bni.co.id" to "BNI",
        "no-reply@gojek.com" to "Gojek / GoPay",
        "no-reply@ovo.id" to "OVO",
        "no-reply@dana.id" to "DANA",
        "receipts@shopee.co.id" to "Shopee",
        "no-reply@tokopedia.com" to "Tokopedia",
        "receipts@starbucks.co.id" to "Starbucks",
    ).map { (pattern, label) ->
        GmailSenderEntity(pattern = pattern, label = label, enabled = true)
    }
}
