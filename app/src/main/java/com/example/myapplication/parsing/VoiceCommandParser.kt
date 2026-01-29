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

        val markerResult = parseMarkerCommand(trimmed)
        if (markerResult != null) {
            return markerResult
        }

        val quantityResult = parseQuantityCommand(trimmed)
        if (quantityResult != null) {
            return quantityResult
        }

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

    fun parseMarkerCommand(text: String): VoiceCommandResult? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null
        val markerCommand = detectMarkerCommand(trimmed) ?: return null
        if (markerCommand.markerText.isBlank()) {
            val debug = listOf("VoiceCommand: ADD_MARKER ignored (empty marker text)")
            Log.i(VOICE_TAG, debug.first())
            return VoiceCommandResult.Ignored(
                reason = "Brak tekstu markera.",
                debug = debug
            )
        }
        val debug = listOf(
            "VoiceCommand: ADD_MARKER (alias='${markerCommand.alias}') -> \"${markerCommand.markerText}\""
        )
        Log.i(VOICE_TAG, debug.first())
        return VoiceCommandResult.AddMarker(
            name = markerCommand.markerText,
            alias = markerCommand.alias,
            debug = debug
        )
    }

    fun parseQuantityCommand(text: String): VoiceCommandResult.Item? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null
        val quantityMatch = quantityTriggerRegex.find(trimmed) ?: return null

        val beforeTrigger = trimmed.substring(0, quantityMatch.range.first).trim()
        val afterTrigger = trimmed.substring(quantityMatch.range.last + 1).trim()

        val quantityResult = parseQuantityAndUnit(afterTrigger)
        if (quantityResult.quantity == null) {
            Log.i(VOICE_TAG, quantityResult.debug.first())
            return VoiceCommandResult.Item(
                name = beforeTrigger,
                quantity = null,
                unit = null,
                parseStatus = quantityResult.parseStatus,
                debug = quantityResult.debug
            )
        }

        Log.i(VOICE_TAG, quantityResult.debug.first())
        return VoiceCommandResult.Item(
            name = beforeTrigger,
            quantity = quantityResult.quantity,
            unit = quantityResult.unit,
            parseStatus = quantityResult.parseStatus,
            debug = quantityResult.debug
        )
    }

    fun parseQuantityAndUnit(text: String): QuantityParseResult {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            return QuantityParseResult(
                quantity = null,
                unit = null,
                parseStatus = ParseStatus.WARNING,
                debug = listOf("VoiceCommand: quantity trigger without numeric value")
            )
        }

        val quantityParse = parseQuantity(trimmed)
        if (quantityParse == null) {
            return QuantityParseResult(
                quantity = null,
                unit = null,
                parseStatus = ParseStatus.WARNING,
                debug = listOf("VoiceCommand: quantity trigger without numeric value")
            )
        }

        val unit = parseUnit(trimmed, quantityParse)
        val debug = buildList {
            add("VoiceCommand: parsed quantity=${quantityParse.value} unit=${unit?.label ?: "none"}")
            if (unit == null) {
                add("VoiceCommand: no unit alias found")
            }
        }
        return QuantityParseResult(
            quantity = quantityParse.value,
            unit = unit,
            parseStatus = ParseStatus.OK,
            debug = debug
        )
    }

    private fun parseQuantity(text: String): ParsedNumber? {
        val words = tokenizeWords(text)
        if (words.isEmpty()) return null
        val tokens = words.map { SpokenNumberParser.normalizePolish(it) }
        if (tokens.isEmpty()) return null

        for (index in tokens.indices) {
            val value = tokens[index].toIntOrNull()
            if (value != null && value in 0..999) {
                return ParsedNumber(value, 1, index)
            }
        }

        for (index in tokens.indices) {
            val parsed = SpokenNumberParser.parseSpokenNumber(tokens, index)
            if (parsed != null) {
                return ParsedNumber(parsed.value, parsed.consumed, parsed.startIndex)
            }
        }

        return null
    }

    private fun parseUnit(text: String, quantityParse: ParsedNumber?): UnitType? {
        val words = tokenizeWords(text)
        if (words.isEmpty()) return null
        val remainingWords = if (quantityParse == null) {
            words
        } else {
            words.filterIndexed { index, _ ->
                index !in quantityParse.startIndex until (quantityParse.startIndex + quantityParse.consumed)
            }
        }
        return parseUnitFromWords(remainingWords)
    }

    private fun parseUnitFromWords(words: List<String>): UnitType? {
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

    private data class ParsedNumber(val value: Int, val consumed: Int, val startIndex: Int)

    private fun tokenizeWords(text: String): List<String> {
        return text.split(Regex("\\s+"))
            .map { it.trim(',', '.', ';', ':').lowercase() }
            .filter { it.isNotBlank() }
    }

    private fun detectMarkerCommand(text: String): MarkerCommandMatch? {
        val tokens = tokenizeCommandTokens(text)
        if (tokens.isEmpty()) return null
        val candidates = tokens.take(MAX_MARKER_TOKENS)
        for (alias in markerAliases) {
            if (alias.tokens.size <= candidates.size &&
                candidates.subList(0, alias.tokens.size) == alias.tokens
            ) {
                val markerText = extractMarkerText(text, alias.tokens.size)
                return MarkerCommandMatch(
                    alias = alias.prefix,
                    markerText = markerText
                )
            }
        }
        return null
    }

    private fun tokenizeCommandTokens(text: String): List<String> {
        return text.split(Regex("\\s+"))
            .map { it.trim(',', '.', ':').lowercase() }
            .map { SpokenNumberParser.normalizePolish(it) }
            .filter { it.isNotBlank() }
    }

    private fun extractMarkerText(text: String, prefixTokenCount: Int): String {
        if (prefixTokenCount <= 0) return text.trim()
        val trimmed = text.trim()
        val matches = Regex("\\S+").findAll(trimmed).toList()
        if (prefixTokenCount >= matches.size) {
            return ""
        }
        val startIndex = matches[prefixTokenCount].range.first
        return trimmed.substring(startIndex).trimStart(' ', ':', ',', '.')
    }

    private data class UnitAlias(val unit: UnitType, val tokens: List<String>)

    private data class MarkerAlias(val prefix: String, val tokens: List<String>)

    private data class MarkerCommandMatch(val alias: String, val markerText: String)

    data class QuantityParseResult(
        val quantity: Int?,
        val unit: UnitType?,
        val parseStatus: ParseStatus,
        val debug: List<String>
    )

    private companion object {
        private val quantityTriggerRegex =
            Regex("\\b(ilość|ilosc|ilości|ilosci)\\b", RegexOption.IGNORE_CASE)
        private const val MAX_MARKER_TOKENS = 6

        private val markerAliases = listOf(
            MarkerAlias("dodaj marker", listOf("dodaj", "marker")),
            MarkerAlias("dodaj markier", listOf("dodaj", "markier")),
            MarkerAlias("dodac marker", listOf("dodac", "marker")),
            MarkerAlias("dodac markier", listOf("dodac", "markier")),
            MarkerAlias("duda i marker", listOf("duda", "i", "marker")),
            MarkerAlias("duda i markier", listOf("duda", "i", "markier"))
        )

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
    data class AddMarker(val name: String, val alias: String, val debug: List<String>) : VoiceCommandResult()

    data class Ignored(val reason: String, val debug: List<String>) : VoiceCommandResult()

    data class Item(
        val name: String,
        val quantity: Int?,
        val unit: UnitType?,
        val parseStatus: ParseStatus,
        val debug: List<String>
    ) : VoiceCommandResult()
}
