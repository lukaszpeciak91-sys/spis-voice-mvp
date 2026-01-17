package com.example.myapplication.parsing

class Tokenizer(private val provider: TokenProvider) {
    fun tokenize(text: String): TokenizationResult {
        val tokens = mutableListOf<Token>()
        val unknownWords = mutableListOf<String>()
        val words = text
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        for (rawWord in words) {
            val cleaned = rawWord.trim().lowercase()
            val lookup = cleaned.trim(',', '.', ';', ':')
            val provided = provider.tokenFor(lookup)
            if (provided != null) {
                tokens.add(
                    provided.copy(
                        fromProvider = true,
                        source = rawWord
                    )
                )
                continue
            }

            val numericCandidate = lookup.replace(',', '.')
            if (numericCandidate.matches(Regex("\\d+"))) {
                val intValue = numericCandidate.toIntOrNull()
                tokens.add(
                    Token(
                        value = lookup.replace('.', ','),
                        type = TokenType.NUMBER,
                        numberValue = intValue,
                        source = rawWord
                    )
                )
                continue
            }

            val isWord = lookup.matches(Regex("[\\p{L}]+"))
            if (isWord) {
                unknownWords.add(lookup)
            }

            tokens.add(
                Token(
                    value = rawWord.uppercase(),
                    type = TokenType.WORD,
                    source = rawWord
                )
            )
        }

        return TokenizationResult(tokens = tokens, unknownWords = unknownWords)
    }
}
