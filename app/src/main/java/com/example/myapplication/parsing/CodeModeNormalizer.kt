package com.example.myapplication.parsing

class CodeModeNormalizer {
    data class Result(val normalized: String)

    fun normalize(rawText: String): Result {
        val tokens = tokenize(rawText)
        if (tokens.isEmpty()) {
            return Result("")
        }

        val letters = mutableListOf<String>()
        val numericParts = mutableListOf<String>()
        var index = 0
        var parsingLetters = true

        while (index < tokens.size) {
            val token = tokens[index]

            val letter = letterMap[token]
            if (parsingLetters && letter != null) {
                letters.add(letter)
                index += 1
                continue
            }

            parsingLetters = false

            val parsedNumber = parseNumericToken(tokens, index)
            if (parsedNumber != null) {
                numericParts.add(parsedNumber.value)
                index += parsedNumber.consumed
                continue
            }

            val operator = operatorMap[token]
            if (operator != null) {
                numericParts.add(operator)
                index += 1
                continue
            }

            index += 1
        }

        val letterBlock = letters.joinToString(separator = "")
        val numericBlock = numericParts.joinToString(separator = "")

        val normalized = when {
            letterBlock.isNotBlank() && numericBlock.isNotBlank() -> "${letterBlock} ${numericBlock}"
            letterBlock.isNotBlank() -> letterBlock
            numericBlock.isNotBlank() -> numericBlock
            else -> ""
        }

        return Result(normalized)
    }

    private fun tokenize(input: String): List<String> {
        return input.split(Regex("[\\s:,.]+"))
            .map { it.trim().lowercase() }
            .map { SpokenNumberParser.normalizePolish(it) }
            .filter { it.isNotBlank() }
    }

    private data class ParsedNumeric(val value: String, val consumed: Int)

    private fun parseNumericToken(tokens: List<String>, startIndex: Int): ParsedNumeric? {
        val token = tokens.getOrNull(startIndex) ?: return null
        if (token == "pol") {
            return ParsedNumeric("0,5", 1)
        }
        if (token.all { it.isDigit() }) {
            val nextIndex = startIndex + 1
            if (tokens.getOrNull(nextIndex) == "i" && tokens.getOrNull(nextIndex + 1) == "pol") {
                return ParsedNumeric("${token},5", 3)
            }
            return ParsedNumeric(token, 1)
        }

        val parsed = SpokenNumberParser.parseSpokenNumber(tokens, startIndex) ?: return null
        val nextIndex = startIndex + parsed.consumed
        if (tokens.getOrNull(nextIndex) == "i" && tokens.getOrNull(nextIndex + 1) == "pol") {
            return ParsedNumeric("${parsed.value},5", parsed.consumed + 2)
        }
        return ParsedNumeric(parsed.value.toString(), parsed.consumed)
    }

    private companion object {
        private val letterMap = mapOf(
            "igrek" to "Y",
            "ygrek" to "Y",
            "igreg" to "Y",
            "igrekg" to "Y",
            "greg" to "Y",
            "de" to "D",
            "ka" to "K",
            "be" to "B",
            "ce" to "C",
            "zet" to "Z",
            "a" to "A",
            "e" to "E"
        )

        private val operatorMap = mapOf(
            "na" to "x",
            "razy" to "x",
            "iks" to "x",
            "przez" to "x"
        )
    }
}
