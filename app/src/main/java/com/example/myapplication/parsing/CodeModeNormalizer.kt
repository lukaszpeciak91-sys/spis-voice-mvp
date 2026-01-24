package com.example.myapplication.parsing

class CodeModeNormalizer {
    data class Result(val normalized: String)

    fun normalizeIfTriggered(rawText: String): Result? {
        val triggerMatch = triggerRegex.find(rawText) ?: return null
        val afterTrigger = rawText.substring(triggerMatch.range.last + 1).trim()
        if (afterTrigger.isBlank()) {
            return Result("")
        }

        val tokens = tokenize(afterTrigger)
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

            val parsedNumber = SpokenNumberParser.parseNumber(tokens, index)
            if (parsedNumber != null) {
                val nextIndex = index + parsedNumber.consumed
                if (tokens.getOrNull(nextIndex) == "i" && tokens.getOrNull(nextIndex + 1) == "pol") {
                    numericParts.add("${parsedNumber.value},5")
                    index = nextIndex + 2
                } else {
                    numericParts.add(parsedNumber.value.toString())
                    index = nextIndex
                }
                continue
            }

            if (token == "pol") {
                numericParts.add("0,5")
                index += 1
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

    private companion object {
        private val triggerRegex = Regex("\\bkod\\b:?", RegexOption.IGNORE_CASE)

        private val letterMap = mapOf(
            "igrek" to "Y",
            "ygrek" to "Y",
            "de" to "D",
            "ka" to "K",
            "be" to "B",
            "ce" to "C",
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
