package com.example.myapplication.parsing

import com.example.myapplication.ParseStatus
import com.example.myapplication.UnitType

class Normalizer {
    fun normalize(tokens: List<Token>, unknownWords: List<String>): ParseResult {
        if (tokens.isEmpty()) {
            return ParseResult(
                normalizedText = null,
                status = ParseStatus.FAIL,
                debug = listOf("Brak tokenów do parsowania.")
            )
        }

        val debug = mutableListOf<String>()
        if (unknownWords.isNotEmpty()) {
            debug.add("Nieznane słowa: ${unknownWords.joinToString(", ")}")
        }

        val extraction = extractQuantity(tokens)
        debug.addAll(extraction.debug)

        val cleanedTokens = mergeDecimals(extraction.remainingTokens)
            .let { mergeNumberTokens(it) }
            .filter { it.type != TokenType.CONNECTOR && it.type != TokenType.FRACTION }

        val normalizedText = buildNormalizedText(cleanedTokens).ifBlank { null }

        val status = when {
            normalizedText == null -> ParseStatus.FAIL
            unknownWords.isNotEmpty() -> ParseStatus.WARNING
            else -> ParseStatus.OK
        }

        return ParseResult(
            normalizedText = normalizedText,
            status = status,
            debug = debug,
            extractedQuantity = extraction.quantity,
            extractedUnit = extraction.unit
        )
    }

    private fun extractQuantity(tokens: List<Token>): QuantityExtraction {
        val debug = mutableListOf<String>()
        var quantity: Int? = null
        var unit: UnitType? = null
        val removeIndices = mutableSetOf<Int>()

        for (index in 0 until tokens.lastIndex) {
            val current = tokens[index]
            val next = tokens[index + 1]
            if (current.type == TokenType.NUMBER && current.numberValue != null && next.type == TokenType.UNIT) {
                quantity = current.numberValue
                unit = next.unitType
                removeIndices.add(index)
                removeIndices.add(index + 1)
                debug.add("Wykryto ilość: $quantity ${unit?.label}")
                break
            }
        }

        val remaining = tokens.filterIndexed { index, _ -> index !in removeIndices }
        return QuantityExtraction(quantity = quantity, unit = unit, remainingTokens = remaining, debug = debug)
    }

    private fun mergeDecimals(tokens: List<Token>): List<Token> {
        val result = mutableListOf<Token>()
        var index = 0
        while (index < tokens.size) {
            val current = tokens[index]
            if (
                current.type == TokenType.NUMBER &&
                index + 2 < tokens.size &&
                tokens[index + 1].type == TokenType.CONNECTOR &&
                tokens[index + 2].type == TokenType.FRACTION
            ) {
                val fraction = tokens[index + 2].value
                val decimalPart = fraction.substringAfter(",", fraction)
                val value = "${current.value},$decimalPart"
                result.add(
                    Token(
                        value = value,
                        type = TokenType.NUMBER,
                        numberValue = null,
                        fromProvider = current.fromProvider
                    )
                )
                index += 3
            } else {
                result.add(current)
                index += 1
            }
        }
        return result
    }

    private fun mergeNumberTokens(tokens: List<Token>): List<Token> {
        val result = mutableListOf<Token>()
        var pendingNumber: Int? = null
        var pendingFromProvider = false

        fun flushPending() {
            if (pendingNumber != null) {
                result.add(
                    Token(
                        value = pendingNumber.toString(),
                        type = TokenType.NUMBER,
                        numberValue = pendingNumber,
                        fromProvider = pendingFromProvider
                    )
                )
            }
            pendingNumber = null
            pendingFromProvider = false
        }

        for (token in tokens) {
            if (token.type == TokenType.NUMBER && token.numberValue != null) {
                if (pendingNumber == null) {
                    pendingNumber = token.numberValue
                    pendingFromProvider = token.fromProvider
                } else if (shouldCombineNumbers(pendingNumber ?: 0, token.numberValue)) {
                    pendingNumber = (pendingNumber ?: 0) + token.numberValue
                    pendingFromProvider = pendingFromProvider && token.fromProvider
                } else {
                    flushPending()
                    pendingNumber = token.numberValue
                    pendingFromProvider = token.fromProvider
                }
            } else {
                flushPending()
                result.add(token)
            }
        }

        flushPending()
        return result
    }

    private fun shouldCombineNumbers(left: Int, right: Int): Boolean {
        return (left >= 100 && right < 100) || (left in tensValues() && right in 1..9)
    }

    private fun tensValues(): Set<Int> = setOf(10, 20, 30, 40, 50, 60, 70, 80, 90)

    private fun buildNormalizedText(tokens: List<Token>): String {
        val builder = StringBuilder()
        for (index in tokens.indices) {
            val current = tokens[index]
            val prev = tokens.getOrNull(index - 1)
            val next = tokens.getOrNull(index + 1)

            val dimensionNumber =
                current.type == TokenType.NUMBER && next?.type == TokenType.SYMBOL && next.value == "x"

            val shouldSkipSpace = when {
                prev == null -> true
                current.type == TokenType.SYMBOL -> true
                prev.type == TokenType.SYMBOL -> true
                current.type in setOf(TokenType.LETTER, TokenType.NUMBER) &&
                    prev.type in setOf(TokenType.LETTER, TokenType.NUMBER) &&
                    !dimensionNumber -> true
                else -> false
            }

            if (!shouldSkipSpace) {
                builder.append(' ')
            }
            builder.append(current.value)
        }
        return builder.toString()
    }

    private data class QuantityExtraction(
        val quantity: Int?,
        val unit: UnitType?,
        val remainingTokens: List<Token>,
        val debug: List<String>
    )
}
