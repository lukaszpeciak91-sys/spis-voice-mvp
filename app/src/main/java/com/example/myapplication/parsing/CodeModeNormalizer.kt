package com.example.myapplication.parsing

import android.util.Log

class CodeModeNormalizer {
    data class Result(val normalized: String, val tokens: List<String>)

    fun normalize(rawText: String, enableFuzzy: Boolean = false): Result {
        val trimmed = rawText.trim()
        if (trimmed.isNotEmpty() && trimmed.any { it.isDigit() } && trimmed.none { it.isWhitespace() }) {
            val normalized = trimmed.uppercase().replace("X", "x")
            return Result(normalized, emptyList())
        }

        val tokens = normalizeFractions(tokenize(rawText))
        if (tokens.isEmpty()) {
            return Result("", emptyList())
        }

        val builder = StringBuilder()
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
        return Result(normalized, tokens)
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
            if (token == "poltora") {
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

}
