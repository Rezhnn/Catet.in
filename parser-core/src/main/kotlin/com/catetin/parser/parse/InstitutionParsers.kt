package com.catetin.parser.parse

import com.catetin.parser.institution.Institution

class BcaParser(institution: Institution) : BaseTransactionParser(institution, "bca-notification-v1") {
    override val institutionKeywords = listOf("bca", "mybca", "halo bca")
}

class MandiriParser(institution: Institution) : BaseTransactionParser(institution, "mandiri-notification-v1") {
    override val institutionKeywords = listOf("mandiri", "livin")
}

class BriParser(institution: Institution) : BaseTransactionParser(institution, "bri-notification-v1") {
    override val institutionKeywords = listOf("bri", "brimo")
}

class BniParser(institution: Institution) : BaseTransactionParser(institution, "bni-notification-v1") {
    override val institutionKeywords = listOf("bni")
}

class SeabankParser(institution: Institution) : BaseTransactionParser(institution, "seabank-notification-v1") {
    override val institutionKeywords = listOf("seabank", "sea bank")
}

class JagoParser(institution: Institution) : BaseTransactionParser(institution, "jago-notification-v1") {
    override val institutionKeywords = listOf("jago", "bank jago")
}

class JeniusParser(institution: Institution) : BaseTransactionParser(institution, "jenius-notification-v1") {
    override val institutionKeywords = listOf("jenius", "btpn")
}

class GopayParser(institution: Institution) : BaseTransactionParser(institution, "gopay-notification-v1") {
    override val institutionKeywords = listOf("gopay", "gojek", "go-pay")
}

class OvoParser(institution: Institution) : BaseTransactionParser(institution, "ovo-notification-v1") {
    override val institutionKeywords = listOf("ovo")
}

class DanaParser(institution: Institution) : BaseTransactionParser(institution, "dana-notification-v1") {
    override val institutionKeywords = listOf("dana")
}

class ShopeePayParser(institution: Institution) : BaseTransactionParser(institution, "shopeepay-notification-v1") {
    override val institutionKeywords = listOf("shopeepay", "shopee")
}

class LinkAjaParser(institution: Institution) : BaseTransactionParser(institution, "linkaja-notification-v1") {
    override val institutionKeywords = listOf("linkaja")
}

class TokopediaParser(institution: Institution) : BaseTransactionParser(institution, "tokopedia-notification-v1") {
    override val institutionKeywords = listOf("tokopedia")
}

class GrabParser(institution: Institution) : BaseTransactionParser(institution, "grab-notification-v1") {
    override val institutionKeywords = listOf("grab")
}

/**
 * Last-resort parser for Indonesian financial text that does not name a known
 * institution. It only books when the direction and a Rupiah amount are clear.
 */
class GenericIndonesianParser : BaseTransactionParser(null, "generic-id-v1") {
    override val institutionKeywords: List<String> = emptyList()
    override val requiredAnyKeywords: List<String> = emptyList()
}
