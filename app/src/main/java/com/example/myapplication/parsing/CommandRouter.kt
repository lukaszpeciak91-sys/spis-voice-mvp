package com.example.myapplication.parsing

import com.example.myapplication.ParseStatus

class CommandRouter(
    private val voiceCommandParser: VoiceCommandParser = VoiceCommandParser(),
    private val codeModeNormalizer: CodeModeNormalizer = CodeModeNormalizer()
) {
    fun route(rawText: String, forceCodeMode: Boolean = false): RoutedCommand {
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

        if (forceCodeMode) {
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
                codeModeTokens = normalizedResult.tokens
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
            val normalized = codeModeNormalizer.normalize(codeTrigger.afterTrigger).normalized
            val item = VoiceCommandResult.Item(
                name = normalized,
                quantity = null,
                unit = null,
                parseStatus = ParseStatus.OK,
                debug = listOf("VoiceCommand: code mode")
            )
            return RoutedCommand(route = Route.CODE, result = item, alias = codeTrigger.alias)
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
        val codeModeTokens: List<String> = emptyList()
    )

    enum class Route {
        MARKER,
        ILOSC,
        CODE,
        NONE
    }

    private data class CodeTrigger(val alias: String, val afterTrigger: String)

    private companion object {
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
