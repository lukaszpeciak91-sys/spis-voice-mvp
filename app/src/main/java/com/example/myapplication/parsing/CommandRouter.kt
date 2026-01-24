package com.example.myapplication.parsing

import com.example.myapplication.ParseStatus

class CommandRouter(
    private val voiceCommandParser: VoiceCommandParser = VoiceCommandParser(),
    private val codeModeNormalizer: CodeModeNormalizer = CodeModeNormalizer()
) {
    fun route(rawText: String): RoutedCommand {
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
        val match = Regex("^\\S+").find(text) ?: return null
        val rawToken = match.value
        val lower = rawToken.lowercase()
        val normalized = SpokenNumberParser.normalizePolish(lower)
        val matchedAlias = when {
            codeAliases.contains(lower) -> lower
            codeAliasesNormalized.contains(normalized) -> normalized
            else -> null
        } ?: return null

        val afterTrigger = text.substring(match.range.last + 1).trim()
        return CodeTrigger(alias = matchedAlias.trimEnd(':'), afterTrigger = afterTrigger)
    }

    data class RoutedCommand(
        val route: Route,
        val result: VoiceCommandResult,
        val alias: String? = null
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
            "kod:",
            "kot",
            "kot:",
            "kat",
            "kat:",
            "kąt",
            "kąt:"
        )
        private val codeAliasesNormalized = codeAliases.map { SpokenNumberParser.normalizePolish(it) }.toSet()
    }
}
