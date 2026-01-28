package com.example.myapplication.parsing

import android.util.Log

class CodeModeNormalizer {
    data class Result(val normalized: String, val tokens: List<String>)

    fun normalize(rawText: String): Result {
        val tokens = tokenize(rawText)
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

        for (token in tokens) {
            val normalizedToken = token.lowercase()
            if (normalizedToken.all { it.isDigit() }) {
                flushSegment()
                builder.append(normalizedToken)
                continue
            }

            if (normalizedToken == "zero") {
                if (hasSegment && segment > 0) {
                    flushSegment()
                }
                builder.append("0")
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
                continue
            }

            val ones = onesMap[normalizedToken]
            if (ones != null) {
                if (!hasSegment) {
                    segment += ones
                    hasSegment = true
                    continue
                }
                if (hasHundreds) {
                    segment += ones
                    continue
                }
                if (hasTens) {
                    segment += ones
                    continue
                }
                if (hasTeens) {
                    flushSegment()
                    segment += ones
                    hasSegment = true
                    continue
                }
                flushSegment()
                segment += ones
                hasSegment = true
                continue
            }

            flushSegment()
            val letter = letterMap[normalizedToken] ?: singleLetter(normalizedToken)
            if (letter != null) {
                builder.append(letter)
                continue
            }
            val fuzzyLetter = fuzzyYMap(normalizedToken)
            if (fuzzyLetter != null) {
                Log.i(CODE_MODE_TAG, "fuzzyYMap: $normalizedToken -> Y")
                builder.append(fuzzyLetter)
                continue
            }
        }

        flushSegment()

        val normalized = builder.toString()
            .uppercase()
            .replace("X", "x")
            .filter {
                it in 'A'..'Z' ||
                    it in '0'..'9' ||
                    it == '.' ||
                    it == '/' ||
                    it == '+' ||
                    it == '-' ||
                    it == 'x'
            }
        return Result(normalized, tokens)
    }

    private fun tokenize(input: String): List<String> {
        return input.split(Regex("[\\s\\p{Punct}]+"))
            .map { it.trim() }
            .map { SpokenNumberParser.normalizePolish(it.lowercase()) }
            .filter { it.isNotBlank() }
    }

    private companion object {
        const val CODE_MODE_TAG = "CodeModeNormalizer"

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
            "zet" to "Z",
            "a" to "A",
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
            "myslnik" to "-",
            "minus" to "-",
            "kropka" to ".",
            "slash" to "/",
            "slesz" to "/",
            "ukosnik" to "/",
            "plus" to "+"
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
