package com.example.myapplication.parsing

object SpokenNumberParser {
    data class ParsedNumber(val value: Int, val consumed: Int, val startIndex: Int)

    fun normalizePolish(input: String): String {
        return buildString(input.length) {
            for (char in input) {
                append(polishCharMap[char] ?: char)
            }
        }
    }

    fun parseNumber(tokens: List<String>, startIndex: Int): ParsedNumber? {
        val token = tokens.getOrNull(startIndex) ?: return null
        token.toIntOrNull()?.let { value ->
            if (value in 0..999) {
                return ParsedNumber(value, 1, startIndex)
            }
        }
        return parseSpokenNumber(tokens, startIndex)
    }

    fun parseSpokenNumber(tokens: List<String>, startIndex: Int): ParsedNumber? {
        val first = tokens[startIndex]
        var index = startIndex
        var value = 0

        hundredsMap[first]?.let { hundreds ->
            value += hundreds
            index += 1

            val tens = tokens.getOrNull(index)?.let { tensMap[it] }
            if (tens != null) {
                value += tens
                index += 1
                val unit = tokens.getOrNull(index)?.let { unitsMap[it] }
                if (unit != null && unit in 1..9) {
                    value += unit
                    index += 1
                }
                return ParsedNumber(value, index - startIndex, startIndex)
            }

            val unit = tokens.getOrNull(index)?.let { unitsMap[it] }
            if (unit != null) {
                value += unit
                index += 1
            }
            return ParsedNumber(value, index - startIndex, startIndex)
        }

        tensMap[first]?.let { tens ->
            value += tens
            index += 1
            val unit = tokens.getOrNull(index)?.let { unitsMap[it] }
            if (unit != null && unit in 1..9) {
                value += unit
                index += 1
            }
            return ParsedNumber(value, index - startIndex, startIndex)
        }

        unitsMap[first]?.let { unit ->
            return ParsedNumber(unit, 1, startIndex)
        }

        return null
    }

    private val unitsMap = mapOf(
        "zero" to 0,
        "jeden" to 1,
        "jedna" to 1,
        "jedno" to 1,
        "dwa" to 2,
        "dwie" to 2,
        "trzy" to 3,
        "cztery" to 4,
        "piec" to 5,
        "szesc" to 6,
        "siedem" to 7,
        "osiem" to 8,
        "dziewiec" to 9,
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

    private val polishCharMap = mapOf(
        'ą' to 'a',
        'ć' to 'c',
        'ę' to 'e',
        'ł' to 'l',
        'ń' to 'n',
        'ó' to 'o',
        'ś' to 's',
        'ż' to 'z',
        'ź' to 'z'
    )
}
