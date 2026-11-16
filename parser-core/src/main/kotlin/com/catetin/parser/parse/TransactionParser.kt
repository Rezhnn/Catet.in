package com.catetin.parser.parse

import com.catetin.parser.model.ParseResult
import com.catetin.parser.model.RawArtifact

interface TransactionParser {
    val id: String
    fun canHandle(artifact: RawArtifact): Boolean
    fun parse(artifact: RawArtifact): ParseResult
}
