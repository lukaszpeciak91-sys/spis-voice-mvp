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
}
