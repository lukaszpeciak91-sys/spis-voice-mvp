package com.example.myapplication.parsing

import org.junit.Assert.assertEquals
import org.junit.Test

class CodeModeNormalizerTest {
    private val normalizer = CodeModeNormalizer()

    @Test
    fun normalizesSpokenDigitsAndLetters() {
        val result = normalizer.normalize("a d dziewięć osiem zet cztery")
        assertEquals("AD98Z4", result.normalized)
    }

    @Test
    fun keepsProvidedDigits() {
        val result = normalizer.normalize("a de 9 8 z 4")
        assertEquals("AD98Z4", result.normalized)
    }

    @Test
    fun normalizesDigitsSequence() {
        val result = normalizer.normalize("zet jeden dwa trzy")
        assertEquals("Z123", result.normalized)
    }

    @Test
    fun normalizesHundredsTensAndOnesSegment() {
        val result = normalizer.normalize("sto czterdziesci dwa")
        assertEquals("142", result.normalized)
    }

    @Test
    fun normalizesHundredsAndTeens() {
        val result = normalizer.normalize("dziewiecset dziesiec")
        assertEquals("910", result.normalized)
    }

    @Test
    fun concatenatesMultipleNumberSegments() {
        val result = normalizer.normalize("czternascie dwadziescia dziewiec sto")
        assertEquals("14209100", result.normalized)
    }

    @Test
    fun mixesLettersAndNumberSegments() {
        val result = normalizer.normalize("a d sto czterdziesci dwa z")
        assertEquals("AD142Z", result.normalized)
    }

    @Test
    fun preservesNumericTokens() {
        val result = normalizer.normalize("1429100")
        assertEquals("1429100", result.normalized)
    }

    @Test
    fun keepsZerosAsSegments() {
        val result = normalizer.normalize("dziesiec zero zero")
        assertEquals("1000", result.normalized)
    }

    @Test
    fun removesPunctuation() {
        val result = normalizer.normalize("a-b c")
        assertEquals("ABC", result.normalized)
    }

    @Test
    fun normalizesPolishDigitVariants() {
        val result = normalizer.normalize("pięć sześć siedem")
        assertEquals("567", result.normalized)
    }

    @Test
    fun normalizesPunctuationTokens() {
        val hyphenResult = normalizer.normalize("a myślnik 12")
        assertEquals("A-12", hyphenResult.normalized)
        val dotResult = normalizer.normalize("a kropka 1")
        assertEquals("A.1", dotResult.normalized)
        val slashResult = normalizer.normalize("a slesz 1")
        assertEquals("A/1", slashResult.normalized)
        val plusResult = normalizer.normalize("a plus 1")
        assertEquals("A+1", plusResult.normalized)
    }

    @Test
    fun normalizesQAndVAliases() {
        val qResult = normalizer.normalize("ku 1")
        assertEquals("Q1", qResult.normalized)
        val vResult = normalizer.normalize("fał 2")
        assertEquals("V2", vResult.normalized)
    }

    @Test
    fun normalizesFuzzyPrefixesInForcedCodeMode() {
        val hyphenResult = normalizer.normalize("faul mysl dwanascie", enableFuzzy = true)
        assertEquals("V-12", hyphenResult.normalized)
        val hyphenResultAlt = normalizer.normalize("falsz mysl nic dwanascie", enableFuzzy = true)
        assertEquals("V-12", hyphenResultAlt.normalized)
        val slashResult = normalizer.normalize("kup flesz trzy", enableFuzzy = true)
        assertEquals("Q/3", slashResult.normalized)
        val slashResultAlt = normalizer.normalize("kol ukosnie nic trzy", enableFuzzy = true)
        assertEquals("Q/3", slashResultAlt.normalized)
    }
}
