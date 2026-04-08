package com.example.myapplication.parsing

import android.util.Log
import com.example.myapplication.ParseStatus

class CommandRouter(
    private val voiceCommandParser: VoiceCommandParser = VoiceCommandParser(),
    private val codeModeNormalizer: CodeModeNormalizer = CodeModeNormalizer(),
    private val drumCodeNormalizer: DrumCodeNormalizer = DrumCodeNormalizer()
) {
    enum class CodeProfile {
        GENERIC,
        DRUM
    }

    fun route(rawText: String, forceCodeMode: Boolean = false, codeProfile: CodeProfile = CodeProfile.GENERIC): RoutedCommand {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) {
            return RoutedCommand(
                route = Route.NONE,
                result = VoiceCommandResult.Item(
                    name = "",
                    quantity = null,
                    unit = null,
                    parseStatus = ParseStatus.FAIL,
                    debug = listOf("VoiceCommand: empty input")
                )
            )
        }

        if (forceCodeMode && codeProfile == CodeProfile.DRUM) {
            val split = splitByQuantityMarker(trimmed)
            if (split != null) {
                val normalizedResult = drumCodeNormalizer.normalize(split.partA)
                val finalText = normalizedResult.matchedFullCode ?: normalizedResult.canonicalStem.ifBlank { split.partA }
                val quantityResult = voiceCommandParser.parseQuantityAndUnit(split.partB)
                val item = VoiceCommandResult.Item(
                    name = finalText,
                    quantity = quantityResult.quantity,
                    unit = quantityResult.unit,
                    parseStatus = quantityResult.parseStatus,
                    debug = listOf("VoiceCommand: drum code mode") + quantityResult.debug
                )
                return RoutedCommand(
                    route = Route.CODE,
                    result = item,
                    forced = true,
                    codeModeRaw = split.partA,
                    codeModeNormalized = normalizedResult.canonicalStem,
                    codeModeFinal = finalText,
                    codeModeClass = "DRUM_CODE",
                    assemblySteps = normalizedResult.debugSteps,
                    drumFamily = normalizedResult.family,
                    drumResolvedPrefix = normalizedResult.resolvedPrefix,
                    drumMatchedFullCode = normalizedResult.matchedFullCode,
                    drumAmbiguousMatches = normalizedResult.ambiguousMatches
                )
            }

            val normalizedResult = drumCodeNormalizer.normalize(trimmed)
            val finalText = normalizedResult.matchedFullCode ?: normalizedResult.canonicalStem.ifBlank { trimmed }
            val item = VoiceCommandResult.Item(
                name = finalText,
                quantity = null,
                unit = null,
                parseStatus = ParseStatus.OK,
                debug = listOf("VoiceCommand: drum code mode")
            )
            return RoutedCommand(
                route = Route.CODE,
                result = item,
                forced = true,
                codeModeRaw = trimmed,
                codeModeNormalized = normalizedResult.canonicalStem,
                codeModeFinal = finalText,
                codeModeClass = "DRUM_CODE",
                assemblySteps = normalizedResult.debugSteps,
                drumFamily = normalizedResult.family,
                drumResolvedPrefix = normalizedResult.resolvedPrefix,
                drumMatchedFullCode = normalizedResult.matchedFullCode,
                drumAmbiguousMatches = normalizedResult.ambiguousMatches
            )
        }

        if (forceCodeMode) {
            val split = splitByQuantityMarker(trimmed)
            if (split != null) {
                val normalizedResult = codeModeNormalizer.normalize(split.partA, enableFuzzy = true)
                val normalized = normalizedResult.normalized
                val finalText = if (normalized.isBlank()) split.partA else normalized
                val quantityResult = voiceCommandParser.parseQuantityAndUnit(split.partB)
                Log.i(
                    ROUTER_TAG,
                    "VoiceCommand: quantity split partARaw=\"${split.partA}\" partBRaw=\"${split.partB}\""
                )
                Log.i(
                    ROUTER_TAG,
                    "VoiceCommand: code mode split finalNameOrCode=\"$finalText\" " +
                        "parsedQty=${quantityResult.quantity} parsedUnit=${quantityResult.unit?.label}"
                )
                val item = VoiceCommandResult.Item(
                    name = finalText,
                    quantity = quantityResult.quantity,
                    unit = quantityResult.unit,
                    parseStatus = quantityResult.parseStatus,
                    debug = listOf("VoiceCommand: code mode") + quantityResult.debug
                )
                return RoutedCommand(
                    route = Route.CODE,
                    result = item,
                    forced = true,
                    codeModeRaw = split.partA,
                    codeModeNormalized = normalized,
                    codeModeFinal = finalText,
                    codeModeTokens = normalizedResult.tokens,
                    codeModeClass = normalizedResult.codeModeClass.name,
                    assemblySteps = normalizedResult.assemblySteps
                )
            }

            val normalizedResult = codeModeNormalizer.normalize(trimmed, enableFuzzy = true)
            val normalized = normalizedResult.normalized
            val finalText = if (normalized.isBlank()) trimmed else normalized
            val item = VoiceCommandResult.Item(
                name = finalText,
                quantity = null,
                unit = null,
                parseStatus = ParseStatus.OK,
                debug = listOf("VoiceCommand: code mode")
            )
            return RoutedCommand(
                route = Route.CODE,
                result = item,
                forced = true,
                codeModeRaw = trimmed,
                codeModeNormalized = normalized,
                codeModeFinal = finalText,
                codeModeTokens = normalizedResult.tokens,
                codeModeClass = normalizedResult.codeModeClass.name,
                assemblySteps = normalizedResult.assemblySteps
            )
        }

        val markerResult = voiceCommandParser.parseMarkerCommand(trimmed)
        if (markerResult != null) {
            return RoutedCommand(route = Route.MARKER, result = markerResult)
        }

        val quantityResult = voiceCommandParser.parseQuantityCommand(trimmed)
        if (quantityResult != null) {
            return RoutedCommand(route = Route.ILOSC, result = quantityResult)
        }

        val codeTrigger = detectCodeTrigger(trimmed)
        if (codeTrigger != null) {
            val split = splitByQuantityMarker(codeTrigger.afterTrigger)
            if (split != null) {
                val normalizedResult = codeModeNormalizer.normalize(split.partA)
                val normalized = normalizedResult.normalized
                val finalText = if (normalized.isBlank()) split.partA else normalized
                val quantityResult = voiceCommandParser.parseQuantityAndUnit(split.partB)
                Log.i(
                    ROUTER_TAG,
                    "VoiceCommand: quantity split partARaw=\"${split.partA}\" partBRaw=\"${split.partB}\""
                )
                Log.i(
                    ROUTER_TAG,
                    "VoiceCommand: code mode split finalNameOrCode=\"$finalText\" " +
                        "parsedQty=${quantityResult.quantity} parsedUnit=${quantityResult.unit?.label}"
                )
                val item = VoiceCommandResult.Item(
                    name = finalText,
                    quantity = quantityResult.quantity,
                    unit = quantityResult.unit,
                    parseStatus = quantityResult.parseStatus,
                    debug = listOf("VoiceCommand: code mode") + quantityResult.debug
                )
                return RoutedCommand(
                    route = Route.CODE,
                    result = item,
                    alias = codeTrigger.alias,
                    codeModeRaw = split.partA,
                    codeModeNormalized = normalized,
                    codeModeFinal = finalText,
                    codeModeTokens = normalizedResult.tokens,
                    codeModeClass = normalizedResult.codeModeClass.name,
                    assemblySteps = normalizedResult.assemblySteps
                )
            }

            val normalizedResult = codeModeNormalizer.normalize(codeTrigger.afterTrigger)
            val normalized = normalizedResult.normalized
            val item = VoiceCommandResult.Item(
                name = normalized,
                quantity = null,
                unit = null,
                parseStatus = ParseStatus.OK,
                debug = listOf("VoiceCommand: code mode")
            )
            return RoutedCommand(
                route = Route.CODE,
                result = item,
                alias = codeTrigger.alias,
                codeModeRaw = codeTrigger.afterTrigger,
                codeModeNormalized = normalized,
                codeModeFinal = normalized,
                codeModeTokens = normalizedResult.tokens,
                codeModeClass = normalizedResult.codeModeClass.name,
                assemblySteps = normalizedResult.assemblySteps
            )
        }

        return RoutedCommand(route = Route.NONE, result = voiceCommandParser.parse(trimmed))
    }

    private fun detectCodeTrigger(text: String): CodeTrigger? {
        val tokens = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null
        val limit = minOf(tokens.size, CODE_TRIGGER_TOKEN_LIMIT)
        for (index in 0 until limit) {
            val token = tokens[index]
            val trimmedToken = token.trim(':', ',', '.', ';')
            if (trimmedToken.isBlank()) continue
            val lower = trimmedToken.lowercase()
            val normalized = SpokenNumberParser.normalizePolish(lower)
            val matchedAlias = when {
                codeAliases.contains(lower) -> lower
                codeAliasesNormalized.contains(normalized) -> lower
                else -> null
            }
            if (matchedAlias != null) {
                val remainingTokens = tokens.toMutableList()
                remainingTokens.removeAt(index)
                val afterTrigger = remainingTokens.joinToString(" ").trim()
                return CodeTrigger(alias = matchedAlias, afterTrigger = afterTrigger)
            }
        }
        return null
    }

    data class RoutedCommand(
        val route: Route,
        val result: VoiceCommandResult,
        val alias: String? = null,
        val forced: Boolean = false,
        val codeModeRaw: String? = null,
        val codeModeNormalized: String? = null,
        val codeModeFinal: String? = null,
        val codeModeTokens: List<String> = emptyList(),
        val codeModeClass: String? = null,
        val assemblySteps: String? = null,
        val drumFamily: String? = null,
        val drumResolvedPrefix: String? = null,
        val drumMatchedFullCode: String? = null,
        val drumAmbiguousMatches: List<String> = emptyList()
    )

    enum class Route {
        MARKER,
        ILOSC,
        CODE,
        NONE
    }

    private data class CodeTrigger(val alias: String, val afterTrigger: String)

    private data class QuantitySplit(val partA: String, val partB: String)

    private fun splitByQuantityMarker(text: String): QuantitySplit? {
        val matches = Regex("\\S+").findAll(text)
        for (match in matches) {
            val token = match.value.trim(',', '.', ':', ';')
            if (token.isBlank()) continue
            val normalized = SpokenNumberParser.normalizePolish(token.lowercase())
            if (normalized == "ilosc") {
                val partA = text.substring(0, match.range.first).trim()
                val partB = text.substring(match.range.last + 1).trim()
                return QuantitySplit(partA = partA, partB = partB)
            }
        }
        return null
    }

    private companion object {
        private const val ROUTER_TAG = "CommandRouter"
        private val codeAliases = setOf(
            "kod",
            "kot",
            "kat",
            "kąt"
        )
        private val codeAliasesNormalized = codeAliases.map { SpokenNumberParser.normalizePolish(it) }.toSet()
        private const val CODE_TRIGGER_TOKEN_LIMIT = 3
    }
}
