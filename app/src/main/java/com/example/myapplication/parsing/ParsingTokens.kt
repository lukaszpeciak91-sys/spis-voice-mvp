package com.example.myapplication.parsing

import com.example.myapplication.UnitType

enum class TokenType {
    WORD,
    LETTER,
    NUMBER,
    SYMBOL,
    UNIT,
    CONNECTOR,
    FRACTION
}

data class Token(
    val value: String,
    val type: TokenType,
    val numberValue: Int? = null,
    val unitType: UnitType? = null,
    val fromProvider: Boolean = false,
    val source: String? = null
)

data class TokenizationResult(
    val tokens: List<Token>,
    val unknownWords: List<String>
)

interface TokenProvider {
    fun tokenFor(word: String): Token?
}
