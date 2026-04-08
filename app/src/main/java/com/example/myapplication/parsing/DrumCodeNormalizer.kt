package com.example.myapplication.parsing

class DrumCodeNormalizer {
    data class Result(
        val canonicalStem: String,
        val matchedFullCode: String?,
        val family: String,
        val resolvedPrefix: String?,
        val ambiguousMatches: List<String>,
        val debugSteps: String
    )

    fun normalize(rawText: String): Result {
        val tokens = rawText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) {
            return Result(
                canonicalStem = "",
                matchedFullCode = null,
                family = "EMPTY",
                resolvedPrefix = null,
                ambiguousMatches = emptyList(),
                debugSteps = "raw=\"$rawText\" | empty_tokens"
            )
        }

        val stream = buildSymbolStream(tokens)
        val familyResult = parseFamily(stream)
        val resolution = DrumStemResolver.resolve(familyResult.canonicalStem)
        return Result(
            canonicalStem = familyResult.canonicalStem,
            matchedFullCode = resolution.matchedFullCode,
            family = familyResult.family,
            resolvedPrefix = familyResult.resolvedPrefix,
            ambiguousMatches = resolution.ambiguousMatches,
            debugSteps = listOf(
                "raw=\"$rawText\"",
                "prefix=${familyResult.resolvedPrefix ?: "-"}",
                "family=${familyResult.family}",
                "stem=${familyResult.canonicalStem.ifBlank { "-" }}",
                "match=${resolution.matchedFullCode ?: "-"}",
                "ambiguity=${if (resolution.ambiguousMatches.isEmpty()) "none" else resolution.ambiguousMatches.joinToString(",") }"
            ).joinToString(" | ")
        )
    }

    private data class FamilyParseResult(val canonicalStem: String, val family: String, val resolvedPrefix: String?)

    private fun parseFamily(stream: String): FamilyParseResult {
        val normalized = stream.uppercase().replace(" ", "")
        if (normalized.isBlank()) {
            return FamilyParseResult("", "FALLBACK", null)
        }

        val kfr = Regex("KFR([A-Z0-9])(\\d{4})(\\d{3})").find(normalized)
        if (kfr != null) {
            return FamilyParseResult(
                canonicalStem = "KFR${kfr.groupValues[1]} ${kfr.groupValues[2]}-${kfr.groupValues[3]}",
                family = "KFR_TYPE_MAIN_TAIL",
                resolvedPrefix = "KFR"
            )
        }

        val plb = Regex("PLB([A-Z0-9])([A-Z])(\\d{5})").find(normalized)
        if (plb != null) {
            return FamilyParseResult(
                canonicalStem = "PLB${plb.groupValues[1]}-${plb.groupValues[2]}${plb.groupValues[3]}",
                family = "PLB_TYPE_SERIES_MAIN",
                resolvedPrefix = "PLB"
            )
        }

        val tFamily = Regex("T(\\d{2})(\\d{2})(\\d{3})").find(normalized)
        if (tFamily != null) {
            return FamilyParseResult(
                canonicalStem = "T-${tFamily.groupValues[1]}-${tFamily.groupValues[2]}-${tFamily.groupValues[3]}",
                family = "T_A_B_C",
                resolvedPrefix = "T"
            )
        }

        val plainType = Regex("(\\d{2})(\\d{4})(\\d{2})").find(normalized)
        if (plainType != null) {
            return FamilyParseResult(
                canonicalStem = "${plainType.groupValues[1]}-${plainType.groupValues[2]}-${plainType.groupValues[3]}",
                family = "TYPE_MAIN_YY",
                resolvedPrefix = null
            )
        }

        val slash25 = Regex("(\\d{2}[A-Z])25(\\d{3,})").find(normalized)
        if (slash25 != null) {
            return FamilyParseResult(
                canonicalStem = "${slash25.groupValues[1]}/25-${slash25.groupValues[2]}",
                family = "PREFIX_25_MAIN",
                resolvedPrefix = slash25.groupValues[1]
            )
        }

        return FamilyParseResult(normalized, "FALLBACK", resolveHardPrefix(normalized))
    }

    private fun buildSymbolStream(tokens: List<String>): String {
        val normalizedTokens = tokens.map { SpokenNumberParser.normalizePolish(it).lowercase() }
        val builder = StringBuilder()
        var index = 0
        while (index < normalizedTokens.size) {
            val token = normalizedTokens[index]
            if (token in separatorNoiseTokens) {
                index += 1
                continue
            }

            val parsed = SpokenNumberParser.parseSpokenNumber(normalizedTokens, index)
            if (parsed != null) {
                builder.append(parsed.value)
                index += parsed.consumed
                continue
            }

            val directPrefix = hardPrefixAliases[token]
            if (directPrefix != null) {
                builder.append(directPrefix)
                index += 1
                continue
            }

            if (token.all { it.isLetterOrDigit() }) {
                val chunk = token.uppercase().mapNotNull { char ->
                    when {
                        char.isDigit() -> char
                        char in 'A'..'Z' -> char
                        else -> null
                    }
                }.joinToString("")
                builder.append(chunk)
            } else {
                val digit = oneDigitWords[token]
                if (digit != null) {
                    builder.append(digit)
                }
                val letter = spokenLetterAliases[token]
                if (letter != null) {
                    builder.append(letter)
                }
            }
            index += 1
        }
        return builder.toString()
    }

    private fun resolveHardPrefix(stream: String): String? = when {
        stream.startsWith("KFR") -> "KFR"
        stream.startsWith("PLB") -> "PLB"
        stream.startsWith("T") -> "T"
        else -> null
    }

    private companion object {
        private val separatorNoiseTokens = setOf(
            "pauza", "minus", "myslnik", "my", "silnik", "slash", "ukosnik", "lamane", "przez", "kropka"
        )

        private val oneDigitWords = mapOf(
            "zero" to '0',
            "jeden" to '1',
            "dwa" to '2',
            "trzy" to '3',
            "cztery" to '4',
            "piec" to '5',
            "szesc" to '6',
            "siedem" to '7',
            "osiem" to '8',
            "dziewiec" to '9'
        )

        private val spokenLetterAliases = mapOf(
            "ka" to 'K',
            "ef" to 'F',
            "er" to 'R',
            "pe" to 'P',
            "el" to 'L',
            "be" to 'B',
            "te" to 'T',
            "a" to 'A'
        )

        private val hardPrefixAliases = mapOf(
            "kfr" to "KFR",
            "plb" to "PLB"
        )
    }
}

private object DrumStemResolver {
    data class Resolution(val matchedFullCode: String?, val ambiguousMatches: List<String>)

    private val fullCodes = listOf(
        "KFR8 0711-022/KWA",
        "PLB6-K10784/NKT",
        "T-20-10-940/TEC",
        "14-1011-24/TEC",
        "08A/25-298/ELS"
    )

    private val byStem: Map<String, List<String>> = fullCodes.groupBy { it.substringBeforeLast('/') }

    fun resolve(canonicalStem: String): Resolution {
        val candidates = byStem[canonicalStem].orEmpty()
        return when (candidates.size) {
            0 -> Resolution(null, emptyList())
            1 -> Resolution(candidates.first(), emptyList())
            else -> Resolution(null, candidates)
        }
    }
}
