package com.example.myapplication.parsing

import android.util.Log
import com.example.myapplication.ParseStatus
import com.example.myapplication.UnitType

private const val VOICE_TAG = "VoiceCommandParser"

class VoiceCommandParser(
    private val tokenizer: Tokenizer = Tokenizer(BasicTokenProvider())
) {
    fun parse(rawText: String): VoiceCommandResult {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) {
            Log.i(VOICE_TAG, "VoiceCommand: empty input")
            return VoiceCommandResult.Item(
                name = "",
                quantity = null,
                unit = null,
                parseStatus = ParseStatus.FAIL,
                debug = listOf("VoiceCommand: empty input")
            )
        }

        val markerMatch = markerRegex.find(trimmed)
        if (markerMatch != null) {
            val name = trimmed.substring(markerMatch.range.last + 1).trim().ifBlank { "MARKER" }
            val debug = listOf("VoiceCommand: ADD_MARKER -> \"$name\"")
            Log.i(VOICE_TAG, debug.first())
            return VoiceCommandResult.AddMarker(name = name, debug = debug)
        }

        val quantityMatch = quantityTriggerRegex.find(trimmed)
        if (quantityMatch == null) {
            val debug = listOf("VoiceCommand: no quantity trigger")
            Log.i(VOICE_TAG, debug.first())
            return VoiceCommandResult.Item(
                name = trimmed,
                quantity = null,
                unit = null,
                parseStatus = ParseStatus.OK,
                debug = debug
            )
        }

        val beforeTrigger = trimmed.substring(0, quantityMatch.range.first).trim()
        val afterTrigger = trimmed.substring(quantityMatch.range.last + 1).trim()

        val quantity = parseQuantity(afterTrigger)
        if (quantity == null) {
            val debug = listOf("VoiceCommand: quantity trigger without numeric value")
            Log.i(VOICE_TAG, debug.first())
            return VoiceCommandResult.Item(
                name = trimmed,
                quantity = null,
                unit = null,
                parseStatus = ParseStatus.WARNING,
                debug = debug
            )
        }

        val unit = parseUnit(afterTrigger)
        val debug = buildList {
            add("VoiceCommand: parsed quantity=$quantity unit=${unit?.label ?: "none"}")
            if (unit == null) {
                add("VoiceCommand: no unit alias found")
            }
        }
        Log.i(VOICE_TAG, debug.first())
        return VoiceCommandResult.Item(
            name = beforeTrigger,
            quantity = quantity,
            unit = unit,
            parseStatus = ParseStatus.OK,
            debug = debug
        )
    }

    private fun parseQuantity(text: String): Int? {
        val tokenization = tokenizer.tokenize(text)
        val merged = mergeNumberTokens(tokenization.tokens)
        return merged.firstOrNull { token ->
            token.type == TokenType.NUMBER && token.numberValue != null
        }?.numberValue
    }

    private fun parseUnit(text: String): UnitType? {
        val words = text.split(Regex("\\s+"))
            .map { it.trim(',', '.', ';', ':').lowercase() }
            .filter { it.isNotBlank() }
        if (words.isEmpty()) return null

        for (index in words.indices) {
            for (alias in sortedAliases) {
                val endIndex = index + alias.tokens.size
                if (endIndex <= words.size && words.subList(index, endIndex) == alias.tokens) {
                    return alias.unit
                }
            }
        }
        return null
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

    private data class UnitAlias(val unit: UnitType, val tokens: List<String>)

    private companion object {
        private val markerRegex = Regex("^\\s*dodaj\\s+marker\\b[:\\s]*", RegexOption.IGNORE_CASE)
        private val quantityTriggerRegex =
            Regex("\\b(ilość|ilosc|ilości|ilosci)\\b", RegexOption.IGNORE_CASE)

        private val aliases = listOf(
            UnitAlias(UnitType.KG, listOf("kg")),
            UnitAlias(UnitType.KG, listOf("kilo")),
            UnitAlias(UnitType.KG, listOf("kilogram")),
            UnitAlias(UnitType.KG, listOf("kilograma")),
            UnitAlias(UnitType.KG, listOf("kilogramy")),
            UnitAlias(UnitType.KG, listOf("kilogramów")),
            UnitAlias(UnitType.KG, listOf("ka", "gie")),
            UnitAlias(UnitType.KG, listOf("ka", "g")),
            UnitAlias(UnitType.M, listOf("m")),
            UnitAlias(UnitType.M, listOf("metr")),
            UnitAlias(UnitType.M, listOf("metry")),
            UnitAlias(UnitType.M, listOf("metra")),
            UnitAlias(UnitType.M, listOf("metrów")),
            UnitAlias(UnitType.CM, listOf("cm")),
            UnitAlias(UnitType.CM, listOf("centymetr")),
            UnitAlias(UnitType.CM, listOf("centymetry")),
            UnitAlias(UnitType.CM, listOf("centymetra")),
            UnitAlias(UnitType.CM, listOf("centymetrów")),
            UnitAlias(UnitType.SZT, listOf("szt")),
            UnitAlias(UnitType.SZT, listOf("sztuka")),
            UnitAlias(UnitType.SZT, listOf("sztuki")),
            UnitAlias(UnitType.SZT, listOf("sztuk")),
            UnitAlias(UnitType.OP, listOf("op")),
            UnitAlias(UnitType.OP, listOf("opakowanie")),
            UnitAlias(UnitType.OP, listOf("opakowania")),
            UnitAlias(UnitType.OP, listOf("opakowań")),
            UnitAlias(UnitType.ROLKA, listOf("rolka")),
            UnitAlias(UnitType.ROLKA, listOf("rolki")),
            UnitAlias(UnitType.ROLKA, listOf("rolek")),
            UnitAlias(UnitType.KPL, listOf("kpl")),
            UnitAlias(UnitType.KPL, listOf("komplet")),
            UnitAlias(UnitType.KPL, listOf("komplety")),
            UnitAlias(UnitType.KPL, listOf("kompletów"))
        )

        private val sortedAliases = aliases.sortedByDescending { it.tokens.size }
    }
}

sealed class VoiceCommandResult {
    data class AddMarker(val name: String, val debug: List<String>) : VoiceCommandResult()

    data class Item(
        val name: String,
        val quantity: Int?,
        val unit: UnitType?,
        val parseStatus: ParseStatus,
        val debug: List<String>
    ) : VoiceCommandResult()
}
