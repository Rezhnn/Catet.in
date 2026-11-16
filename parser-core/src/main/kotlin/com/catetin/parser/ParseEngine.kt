package com.catetin.parser

import com.catetin.parser.institution.Institution
import com.catetin.parser.institution.InstitutionRegistry
import com.catetin.parser.model.ParseResult
import com.catetin.parser.model.RawArtifact
import com.catetin.parser.parse.BcaParser
import com.catetin.parser.parse.BniParser
import com.catetin.parser.parse.BriParser
import com.catetin.parser.parse.DanaParser
import com.catetin.parser.parse.GenericIndonesianParser
import com.catetin.parser.parse.GopayParser
import com.catetin.parser.parse.GrabParser
import com.catetin.parser.parse.JagoParser
import com.catetin.parser.parse.JeniusParser
import com.catetin.parser.parse.LinkAjaParser
import com.catetin.parser.parse.MandiriParser
import com.catetin.parser.parse.OvoParser
import com.catetin.parser.parse.SeabankParser
import com.catetin.parser.parse.ShopeePayParser
import com.catetin.parser.parse.TokopediaParser
import com.catetin.parser.parse.TransactionParser

object ParseEngine {

    private val parsers: List<TransactionParser> = InstitutionRegistry.all.mapNotNull { institution ->
        when (institution.id) {
            "bca" -> BcaParser(institution)
            "mandiri" -> MandiriParser(institution)
            "bri" -> BriParser(institution)
            "bni" -> BniParser(institution)
            "seabank" -> SeabankParser(institution)
            "jago" -> JagoParser(institution)
            "jenius" -> JeniusParser(institution)
            "gopay" -> GopayParser(institution)
            "ovo" -> OvoParser(institution)
            "dana" -> DanaParser(institution)
            "shopeepay" -> ShopeePayParser(institution)
            "linkaja" -> LinkAjaParser(institution)
            "tokopedia" -> TokopediaParser(institution)
            "grab" -> GrabParser(institution)
            else -> null
        }
    } + GenericIndonesianParser()

    fun parserIds(): List<String> = parsers.map { it.id }

    fun parse(artifact: RawArtifact): ParseResult {
        val candidates = parsers.filter { it.canHandle(artifact) }
        val ordered = if (candidates.isEmpty()) listOf(GenericIndonesianParser()) else candidates
        var firstUnparsed: ParseResult.Unparsed? = null
        var firstIgnored: ParseResult.Ignored? = null
        for (parser in ordered) {
            when (val result = parser.parse(artifact)) {
                is ParseResult.Parsed -> return result
                is ParseResult.Unparsed -> if (firstUnparsed == null) firstUnparsed = result
                is ParseResult.Ignored -> if (firstIgnored == null) firstIgnored = result
            }
        }
        return firstUnparsed ?: firstIgnored ?: ParseResult.Unparsed("no parser produced a result")
    }

    fun institutionFor(sourceApp: String, text: String = ""): Institution? =
        InstitutionRegistry.detect(text, sourceApp)
}
