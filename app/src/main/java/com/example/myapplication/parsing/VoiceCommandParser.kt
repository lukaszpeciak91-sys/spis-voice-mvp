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
        val tokens = tokenizeQuantity(text)
        if (tokens.isEmpty()) return null

        tokens.firstNotNullOfOrNull { token ->
            token.toIntOrNull()?.takeIf { it in 0..999 }
        }?.let { return it }

        for (index in tokens.indices) {
            val parsed = parseSpokenNumber(tokens, index)
            if (parsed != null) {
                return parsed.value
            }
        }

        return null
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

    private data class ParsedNumber(val value: Int, val consumed: Int)

    private fun tokenizeQuantity(text: String): List<String> {
        return text
            .lowercase()
            .split(Regex("\\s+"))
            .map { it.trim(',', '.', ';', ':') }
            .filter { it.isNotBlank() }
            .map { normalizePolish(it) }
    }

    private fun normalizePolish(input: String): String {
        return buildString(input.length) {
            for (char in input) {
                append(polishCharMap[char] ?: char)
            }
        }
    }

    private fun parseSpokenNumber(tokens: List<String>, startIndex: Int): ParsedNumber? {
        val first = tokens[startIndex]
        var index = startIndex
        var value = 0

        hundredsMap[first]?.let { hundreds ->
            value += hundreds
            index += 1

            val tens = tokens.getOrNull(index)?.let { tensMap[it] }
            if (tens != null) {
                value += tens
                index += 1
                val unit = tokens.getOrNull(index)?.let { unitsMap[it] }
                if (unit != null && unit in 1..9) {
                    value += unit
                    index += 1
                }
                return ParsedNumber(value, index - startIndex)
            }

            val unit = tokens.getOrNull(index)?.let { unitsMap[it] }
            if (unit != null) {
                value += unit
                index += 1
            }
            return ParsedNumber(value, index - startIndex)
        }

        tensMap[first]?.let { tens ->
            value += tens
            index += 1
            val unit = tokens.getOrNull(index)?.let { unitsMap[it] }
            if (unit != null && unit in 1..9) {
                value += unit
                index += 1
            }
            return ParsedNumber(value, index - startIndex)
        }

        unitsMap[first]?.let { unit ->
            return ParsedNumber(unit, 1)
        }

        return null
    }

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

        private val unitsMap = mapOf(
            "zero" to 0,
            "jeden" to 1,
            "jedna" to 1,
            "jedno" to 1,
            "dwa" to 2,
            "dwie" to 2,
            "trzy" to 3,
            "cztery" to 4,
            "piec" to 5,
            "szesc" to 6,
            "siedem" to 7,
            "osiem" to 8,
            "dziewiec" to 9,
            "dziesiec" to 10,
            "jedenascie" to 11,
            "dwanascie" to 12,
            "trzynascie" to 13,
            "czternascie" to 14,
            "pietnascie" to 15,
            "szesnascie" to 16,
            "siedemnascie" to 17,
            "osiemnascie" to 18,
            "dziewietnascie" to 19
        )

        private val tensMap = mapOf(
            "dwadziescia" to 20,
            "trzydziesci" to 30,
            "czterdziesci" to 40,
            "piecdziesiat" to 50,
            "szescdziesiat" to 60,
            "siedemdziesiat" to 70,
            "osiemdziesiat" to 80,
            "dziewiecdziesiat" to 90
        )

        private val hundredsMap = mapOf(
            "sto" to 100,
            "dwiescie" to 200,
            "trzysta" to 300,
            "czterysta" to 400,
            "piecset" to 500,
            "szescset" to 600,
            "siedemset" to 700,
            "osiemset" to 800,
            "dziewiecset" to 900
        )

        private val polishCharMap = mapOf(
            'ą' to 'a',
            'ć' to 'c',
            'ę' to 'e',
            'ł' to 'l',
            'ń' to 'n',
            'ó' to 'o',
            'ś' to 's',
            'ż' to 'z',
            'ź' to 'z'
        )
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
