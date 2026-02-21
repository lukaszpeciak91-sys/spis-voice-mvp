package com.example.myapplication.parsing

import android.util.Log

class CodeModeNormalizer {
    enum class CodeModeClass {
        ALPHANUM_CODE,
        SPOKEN_NUMERIC_CODE,
        FREE_TEXT
    }

    data class Result(
        val normalized: String,
        val tokens: List<String>,
        val codeModeClass: CodeModeClass,
        val assemblySteps: String
    )

    fun normalize(rawText: String, enableFuzzy: Boolean = false, forceCodeMode: Boolean = false): Result {
        val trimmed = rawText.trim()
        if (trimmed.isNotEmpty() && trimmed.any { it.isDigit() } && trimmed.none { it.isWhitespace() }) {
            val normalized = trimmed.uppercase().replace("X", "x")
            return Result(
                normalized = normalized,
                tokens = emptyList(),
                codeModeClass = CodeModeClass.ALPHANUM_CODE,
                assemblySteps = "raw_compact_code"
            )
        }

        val tokenized = tokenize(rawText)
        val aliasNormalized = normalizeCodeAliases(tokenized, enableFuzzy, forceCodeMode)
        val tokens = normalizeFractions(aliasNormalized)
        if (tokens.isEmpty()) {
            return Result(
                normalized = "",
                tokens = emptyList(),
                codeModeClass = CodeModeClass.FREE_TEXT,
                assemblySteps = "empty_tokens"
            )
        }

        if (isSpokenNumericCode(tokens)) {
            val spokenResult = assembleSpokenNumericCode(tokens)
            val patched = normalizeCableManufacturerSuffix(spokenResult.normalized)
            return spokenResult.copy(normalized = patched)
        }

        val builder = StringBuilder()
        val codeLikeInput = isCodeLike(tokens)
        var segment = 0
        var hasSegment = false
        var hasHundreds = false
        var hasTens = false
        var hasTeens = false

        fun flushSegment() {
            if (hasSegment) {
                builder.append(segment)
            }
            segment = 0
            hasSegment = false
            hasHundreds = false
            hasTens = false
            hasTeens = false
        }

        var index = 0
        while (index < tokens.size) {
            val gluedToken = findGluedToken(tokens, index)
            if (gluedToken != null) {
                flushSegment()
                builder.append(letterMap.getValue(gluedToken.value))
                index += gluedToken.length
                continue
            }

            val normalizedToken = tokens[index].lowercase()
            val slashMatch = matchSlashToken(tokens, index)
            if (slashMatch != null) {
                flushSegment()
                builder.append("/")
                index += slashMatch
                continue
            }
            if (normalizedToken == "-") {
                flushSegment()
                builder.append("-")
                index += 1
                continue
            }
            if (normalizedToken == ".") {
                flushSegment()
                builder.append(".")
                index += 1
                continue
            }
            if (hyphenTokens.contains(normalizedToken)) {
                flushSegment()
                builder.append("-")
                index += 1
                continue
            }
            if (normalizedToken.any { it == ',' || it == '/' }) {
                flushSegment()
                builder.append(normalizedToken)
                index += 1
                continue
            }
            if (normalizedToken.all { it.isDigit() }) {
                flushSegment()
                builder.append(normalizedToken)
                index += 1
                continue
            }

            if (isContextualMiddleD(tokens, index)) {
                flushSegment()
                builder.append("D")
                index += 1
                continue
            }

            if (normalizedToken == "zero") {
                if (hasSegment && segment > 0) {
                    flushSegment()
                }
                builder.append("0")
                index += 1
                continue
            }

            val hundreds = hundredsMap[normalizedToken]
            if (hundreds != null) {
                if (hasSegment && (hasHundreds || hasTens || hasTeens)) {
                    flushSegment()
                }
                segment += hundreds
                hasSegment = true
                hasHundreds = true
                index += 1
                continue
            }

            val teens = teensMap[normalizedToken]
            if (teens != null) {
                if (hasSegment && (hasTens || hasTeens)) {
                    flushSegment()
                }
                segment += teens
                hasSegment = true
                hasTeens = true
                index += 1
                continue
            }

            val tens = tensMap[normalizedToken]
            if (tens != null) {
                if (hasSegment && (hasTens || hasTeens)) {
                    flushSegment()
                }
                segment += tens
                hasSegment = true
                hasTens = true
                index += 1
                continue
            }

            val ones = onesMap[normalizedToken]
            if (ones != null) {
                if (!hasSegment) {
                    segment += ones
                    hasSegment = true
                    index += 1
                    continue
                }
                if (hasHundreds) {
                    segment += ones
                    index += 1
                    continue
                }
                if (hasTens) {
                    segment += ones
                    index += 1
                    continue
                }
                if (hasTeens) {
                    flushSegment()
                    segment += ones
                    hasSegment = true
                    index += 1
                    continue
                }
                flushSegment()
                segment += ones
                hasSegment = true
                index += 1
                continue
            }

            flushSegment()
            val letter = letterMap[normalizedToken] ?: singleLetter(normalizedToken)
            if (letter != null) {
                builder.append(letter)
                index += 1
                continue
            }

            if (shouldPreserveLiteralToken(tokens, index, codeLikeInput)) {
                builder.append(normalizedToken.uppercase())
                index += 1
                continue
            }

            val fuzzyLetter = fuzzyYMap(normalizedToken)
            if (fuzzyLetter != null) {
                Log.i(CODE_MODE_TAG, "fuzzyYMap: $normalizedToken -> Y")
                builder.append(fuzzyLetter)
                index += 1
                continue
            }

            index += 1
        }

        flushSegment()

        val normalized = builder.toString()
            .uppercase()
            .replace("X", "x")
            .filter {
                it in 'A'..'Z' ||
                    it in '0'..'9' ||
                    it == '.' ||
                    it == ',' ||
                    it == '/' ||
                    it == '+' ||
                    it == '-' ||
                    it == 'x'
            }
        val patched = normalizeCableManufacturerSuffix(normalized)
        return Result(
            normalized = patched,
            tokens = tokens,
            codeModeClass = if (patched.isBlank()) CodeModeClass.FREE_TEXT else CodeModeClass.ALPHANUM_CODE,
            assemblySteps = "default_assembly(tokens=${tokens.size})"
        )
    }

    private fun assembleSpokenNumericCode(tokens: List<String>): Result {
        val builder = StringBuilder()
        val steps = mutableListOf<String>()
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            val slashMatch = matchSlashToken(tokens, index)
            if (slashMatch != null) {
                builder.append("/")
                steps.add("slash:/")
                index += slashMatch
                continue
            }
            if (token == "/") {
                builder.append("/")
                steps.add("slash:/")
                index += 1
                continue
            }
            if (token == ".") {
                builder.append(".")
                steps.add("dot:.")
                index += 1
                continue
            }
            if (token == ",") {
                builder.append(",")
                steps.add("comma:,")
                index += 1
                continue
            }
            if (token == "-") {
                builder.append("-")
                steps.add("hyphen:-")
                index += 1
                continue
            }
            if (token == "kropka") {
                builder.append(".")
                steps.add("dot:.")
                index += 1
                continue
            }
            if (token in hyphenTokens) {
                builder.append("-")
                steps.add("hyphen:-")
                index += 1
                continue
            }
            if (token.all { it.isDigit() }) {
                builder.append(token)
                steps.add("digits:$token")
                index += 1
                continue
            }
            val parsed = SpokenNumberParser.parseSpokenNumber(tokens, index)
            if (parsed != null) {
                builder.append(parsed.value)
                steps.add("num:${tokens.subList(index, index + parsed.consumed).joinToString("+")}=${parsed.value}")
                index += parsed.consumed
                continue
            }
            steps.add("skip:$token")
            index += 1
        }
        val assembled = builder.toString()
        return Result(
            normalized = assembled,
            tokens = tokens,
            codeModeClass = CodeModeClass.SPOKEN_NUMERIC_CODE,
            assemblySteps = summarizeAssemblySteps(steps)
        )
    }

    private fun summarizeAssemblySteps(steps: List<String>, maxLength: Int = 280): String {
        val summary = steps.joinToString(" | ")
        if (summary.length <= maxLength) return summary
        return summary.take(maxLength - 3) + "..."
    }

    private fun isSpokenNumericCode(tokens: List<String>): Boolean {
        return tokens.isNotEmpty() && tokens.all { token ->
            token.all { it.isDigit() } ||
                numberWords.contains(token) ||
                token == "kropka" ||
                token in dotTokens ||
                token == "." ||
                token == "," ||
                token == "-" ||
                token == "/" ||
                token in hyphenTokens ||
                token in slashTokens ||
                token == "przez"
        }
    }

    private fun normalizeCodeAliases(tokens: List<String>, enableFuzzy: Boolean, forceCodeMode: Boolean): List<String> {
        if (!enableFuzzy && !forceCodeMode && !isCodeLike(tokens)) {
            return tokens
        }
        val spokenNumericContext = isLikelySpokenNumericCode(tokens)
        val normalized = mutableListOf<String>()
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            val nextToken = tokens.getOrNull(index + 1)

            val replacement = when {
                token in slashAliasSingles -> "/"
                token in dotTokens -> "."
                token in commaTokens -> ","
                token in hyphenAliasTokens -> "-"
                token == "walu" -> "V"
                token == "jot" || token == "iot" -> "J"
                else -> null
            }

            if (replacement != null && shouldApplyCodeAlias(tokens, index, spokenNumericContext, forceCodeMode)) {
                normalized.add(replacement)
                index += 1
                continue
            }

            if (nextToken != null && shouldApplyCodeAlias(tokens, index, spokenNumericContext, forceCodeMode)) {
                if (token in slashAliasFirstTokens && nextToken in slashAliasSecondTokens) {
                    normalized.add("/")
                    index += 2
                    continue
                }
                if (token == "z" && nextToken == "lasu") {
                    normalized.add("/")
                    index += 2
                    continue
                }
                if ((token == "mysl" || token == "mysli") && nextToken == "nic") {
                    normalized.add("-")
                    index += 2
                    continue
                }
                if (token == "my" && nextToken == "silnik") {
                    normalized.add("-")
                    index += 2
                    continue
                }
            }

            normalized.add(token)
            index += 1
        }

        return collapseConsecutiveSeparators(normalized)
    }

    private fun isLikelySpokenNumericCode(tokens: List<String>): Boolean {
        return tokens.isNotEmpty() && tokens.all { token ->
            token.all { it.isDigit() } ||
                numberWords.contains(token) ||
                token == "kropka" ||
                token in dotTokens ||
                token in hyphenTokens ||
                token in slashTokens ||
                token in commaTokens ||
                token in hyphenAliasTokens ||
                token == "przez" ||
                token in slashAliasSingles ||
                (token in slashAliasFirstTokens) ||
                token in slashAliasSecondTokens ||
                token == "mysl" ||
                token == "mysli" ||
                token == "nic" ||
                token == "my" ||
                token == "silnik"
        }
    }

    private fun shouldApplyCodeAlias(
        tokens: List<String>,
        index: Int,
        spokenNumericContext: Boolean,
        forceCodeMode: Boolean
    ): Boolean {
        if (forceCodeMode || spokenNumericContext) {
            return true
        }
        val previous = tokens.getOrNull(index - 1)
        val next = tokens.getOrNull(index + 1)
        return isCodeLikeNeighborhoodToken(previous) || isCodeLikeNeighborhoodToken(next)
    }

    private fun isCodeLikeNeighborhoodToken(token: String?): Boolean {
        if (token == null) return false
        if (token.all { it.isDigit() }) return true
        if (singleLetter(token) != null) return true
        if (token in canonicalSeparators || token in hyphenTokens || token in slashTokens || token in dotTokens) {
            return true
        }
        return false
    }

    private fun collapseConsecutiveSeparators(tokens: List<String>): List<String> {
        val normalized = mutableListOf<String>()
        for (token in tokens) {
            val prev = normalized.lastOrNull()
            if (token in canonicalSeparators && token == prev) {
                continue
            }
            normalized.add(token)
        }
        return normalized
    }

    internal fun normalizeCableManufacturerSuffix(code: String): String {
        if (!looksLikeCableCode(code)) {
            return code
        }
        val suffixMatch = cableSuffixVariantRegex.find(code) ?: return code
        return code.replaceRange(suffixMatch.range, suffixMatch.groupValues[1] + "NKT")
    }

    private fun looksLikeCableCode(code: String): Boolean {
        val hasCablePrefix = cableCodePrefixes.any { code.startsWith(it) }
        if (!hasCablePrefix) {
            return false
        }
        return cableDimensionRegex.containsMatchIn(code)
    }

    private fun matchSlashToken(tokens: List<String>, index: Int): Int? {
        val token = tokens.getOrNull(index) ?: return null
        val nextToken = tokens.getOrNull(index + 1) ?: return null
        if (token == "lamane" && nextToken == "przez") {
            return 2
        }
        if (slashTokens.contains(token)) {
            return 1
        }
        if (token == "/") {
            return 1
        }
        return null
    }

    private fun tokenize(input: String): List<String> {
        return input.split(Regex("[\\s\\p{Punct}]+"))
            .map { it.trim() }
            .map { SpokenNumberParser.normalizePolish(it.lowercase()) }
            .filter { it.isNotBlank() }
    }

    private fun normalizeFractions(tokens: List<String>): List<String> {
        val normalized = mutableListOf<String>()
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            if (token in halfTokens) {
                normalized.add("1,5")
                index += 1
                continue
            }

            val number = parseNumberUpTo99(tokens, index)
            if (number != null) {
                val afterNumber = index + number.consumed
                if (tokens.getOrNull(afterNumber) == "i") {
                    val afterI = afterNumber + 1
                    if (tokens.getOrNull(afterI) == "pol") {
                        normalized.add("${number.value},5")
                        index = afterI + 1
                        continue
                    }
                    val fraction = parseOrdinalFraction(tokens, afterI)
                    if (fraction != null) {
                        val decimalPart = when {
                            fraction.numerator == 1 && fraction.denominator == 2 -> "5"
                            fraction.numerator == 3 && fraction.denominator == 4 -> "75"
                            else -> null
                        }
                        if (decimalPart != null) {
                            normalized.add("${number.value},$decimalPart")
                            index = afterI + fraction.consumed
                            continue
                        }
                    }
                }

                if (tokens.getOrNull(afterNumber) == "lamane") {
                    var denominatorIndex = afterNumber + 1
                    if (tokens.getOrNull(denominatorIndex) == "przez") {
                        denominatorIndex += 1
                    }
                    val denominator = parseNumberUpTo99(tokens, denominatorIndex)
                    if (denominator != null) {
                        normalized.add("${number.value}/${denominator.value}")
                        index = denominatorIndex + denominator.consumed
                        continue
                    }
                }

                val fraction = parseOrdinalFraction(tokens, afterNumber)
                if (fraction != null) {
                    normalized.add("${number.value}/${fraction.denominator}")
                    index = afterNumber + fraction.consumed
                    continue
                }
            }

            normalized.add(token)
            index += 1
        }
        return normalized
    }

    private fun parseNumberUpTo99(tokens: List<String>, startIndex: Int): SpokenNumberParser.ParsedNumber? {
        val parsed = SpokenNumberParser.parseNumber(tokens, startIndex) ?: return null
        if (parsed.value in 1..99) {
            return parsed
        }
        return null
    }

    private data class FractionParse(val numerator: Int, val denominator: Int, val consumed: Int)

    private fun parseOrdinalFraction(tokens: List<String>, startIndex: Int): FractionParse? {
        val numerator = parseNumberUpTo99(tokens, startIndex) ?: return null
        val denominatorToken = tokens.getOrNull(startIndex + numerator.consumed) ?: return null
        val denominator = ordinalDenominatorMap[denominatorToken] ?: return null
        if (denominator in 1..99) {
            return FractionParse(numerator.value, denominator, numerator.consumed + 1)
        }
        return null
    }

    private fun findGluedToken(tokens: List<String>, startIndex: Int): GluedToken? {
        val maxWindow = minOf(3, tokens.size - startIndex)
        for (windowSize in maxWindow downTo 1) {
            val joined = tokens.subList(startIndex, startIndex + windowSize).joinToString("")
            if (letterMap.containsKey(joined)) {
                return GluedToken(value = joined, length = windowSize)
            }
        }
        return null
    }

    private data class GluedToken(val value: String, val length: Int)

    private companion object {
        private const val CODE_MODE_TAG = "CodeModeNormalizer"
        private val letterMap = mapOf(
            "igrek" to "Y",
            "ygrek" to "Y",
            "igreg" to "Y",
            "igrekg" to "Y",
            "greg" to "Y",
            "na" to "x",
            "razy" to "x",
            "de" to "D",
            "ka" to "K",
            "be" to "B",
            "ce" to "C",
            "ceha" to "CH",
            "zet" to "Z",
            "a" to "A",
            "ch" to "CH",
            "e" to "E",
            "ef" to "F",
            "gie" to "G",
            "ha" to "H",
            "i" to "I",
            "jot" to "J",
            "el" to "L",
            "em" to "M",
            "en" to "N",
            "o" to "O",
            "pe" to "P",
            "ku" to "Q",
            "kiu" to "Q",
            "q" to "Q",
            "er" to "R",
            "es" to "S",
            "te" to "T",
            "u" to "U",
            "fal" to "V",
            "v" to "V",
            "wu" to "W",
            "iks" to "X",
            "kropka" to ".",
            "plus" to "+"
        )
        private val slashTokens = setOf(
            "slash",
            "slesh",
            "ukosnik",
            "lamane",
            "lamaneprzez"
        )
        private val hyphenTokens = setOf(
            "myslnik",
            "minus",
            "pauza",
            "kreska"
        )
        private val hyphenAliasTokens = setOf(
            "pauza",
            "pauze",
            "pauzo"
        )
        private val dotTokens = setOf(
            "kropka",
            "kropke",
            "kropce",
            "krupka",
            "krupke"
        )
        private val commaTokens = setOf(
            "przecinek",
            "przecinku",
            "przecinkiem",
            "przecinka",
            "przecinki"
        )
        private val slashAliasSingles = setOf("zlez", "zlasu", "stres")
        private val slashAliasFirstTokens = setOf("zl")
        private val slashAliasSecondTokens = setOf("lez")
        private val canonicalSeparators = setOf("/", "-", ".", ",")
        private val fuzzyPrefixMap = mapOf(
            "mysl" to "-",
            "fal" to "V",
            "fau" to "V",
            "fals" to "V",
            "ku" to "Q",
            "kiu" to "Q",
            "kol" to "Q"
        )

        private val onesMap = mapOf(
            "zero" to 0,
            "jeden" to 1,
            "dwa" to 2,
            "trzy" to 3,
            "cztery" to 4,
            "piec" to 5,
            "pienc" to 5,
            "szesc" to 6,
            "szezdz" to 6,
            "siedem" to 7,
            "osiem" to 8,
            "dziewiec" to 9,
            "dziewienc" to 9
        )

        private val teensMap = mapOf(
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

        private val ordinalDenominatorMap = mapOf(
            "drugi" to 2,
            "druga" to 2,
            "drugie" to 2,
            "trzeci" to 3,
            "trzecia" to 3,
            "trzecie" to 3,
            "czwarty" to 4,
            "czwarta" to 4,
            "czwarte" to 4,
            "piaty" to 5,
            "piata" to 5,
            "piate" to 5,
            "szosty" to 6,
            "szosta" to 6,
            "szoste" to 6,
            "siodmy" to 7,
            "siodma" to 7,
            "siodme" to 7,
            "osmy" to 8,
            "osma" to 8,
            "osme" to 8,
            "dziewiaty" to 9,
            "dziewiata" to 9,
            "dziewiate" to 9,
            "dziesiaty" to 10,
            "dziesiata" to 10,
            "dziesiate" to 10
        )

        private val numberWords = buildSet {
            addAll(onesMap.keys)
            addAll(teensMap.keys)
            addAll(tensMap.keys)
            addAll(hundredsMap.keys)
        }
        private val halfTokens = setOf("poltora", "poltorej")
        private val cableCodePrefixes = listOf("YKY", "YDYP", "YDY", "OWY")
        private val cableDimensionRegex = Regex("\\d+x\\d")
        private val cableSuffixVariantRegex = Regex("([-/]?)(MKD|MKP|KATE|ENKATE|MKATE)$")
    }

    private fun fuzzyYMap(token: String): String? {
        return when {
            token.startsWith("igr") -> "Y"
            token == "gry" || token.startsWith("gry") -> "Y"
            token.startsWith("grec") || token.startsWith("grek") -> "Y"
            else -> null
        }
    }

    private fun singleLetter(token: String): String? {
        if (token.length == 1 && token[0] in 'a'..'z') {
            return token.uppercase()
        }
        return null
    }

    private fun shouldPreserveLiteralToken(tokens: List<String>, index: Int, codeLikeInput: Boolean): Boolean {
        val token = tokens[index]
        if (!token.all { it in 'a'..'z' }) {
            return false
        }
        if (token.length == 1) {
            val previousIsDigits = tokens.getOrNull(index - 1)?.all { it.isDigit() } == true
            val nextIsDigits = tokens.getOrNull(index + 1)?.all { it.isDigit() } == true
            return (previousIsDigits && nextIsDigits) || codeLikeInput
        }
        return codeLikeInput
    }

    private fun isCodeLike(tokens: List<String>): Boolean {
        val hasDigits = tokens.any { token -> token.any { it.isDigit() } }
        val hasLetters = tokens.any { token -> token.any { it in 'a'..'z' } }
        return hasDigits && hasLetters
    }

    private fun isContextualMiddleD(tokens: List<String>, index: Int): Boolean {
        val token = tokens.getOrNull(index)?.lowercase() ?: return false
        if (token != "do" && token != "de") {
            return false
        }
        val previous = tokens.getOrNull(index - 1) ?: return false
        val next = tokens.getOrNull(index + 1) ?: return false
        return isNumericContextToken(previous) && isNumericContextToken(next)
    }

    private fun isNumericContextToken(token: String): Boolean {
        return token.all { it.isDigit() } || token in numberWords
    }

}
