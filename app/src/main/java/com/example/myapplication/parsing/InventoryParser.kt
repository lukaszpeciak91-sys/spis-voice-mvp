package com.example.myapplication.parsing

import com.example.myapplication.ParseStatus
import com.example.myapplication.UnitType

class InventoryParser(
    private val tokenizer: Tokenizer = Tokenizer(BasicTokenProvider()),
    private val normalizer: Normalizer = Normalizer()
) {
    fun parse(rawText: String): ParseResult {
        if (rawText.isBlank()) {
            return ParseResult(
                normalizedText = null,
                status = ParseStatus.FAIL,
                debug = listOf("Puste pole wejściowe."),
                extractedQuantity = null,
                extractedUnit = null
            )
        }

        val tokenization = tokenizer.tokenize(rawText)
        return normalizer.normalize(tokenization.tokens, tokenization.unknownWords)
    }
}

data class ParseResult(
    val normalizedText: String?,
    val status: ParseStatus,
    val debug: List<String> = emptyList(),
    val extractedQuantity: Int? = null,
    val extractedUnit: UnitType? = null
)
