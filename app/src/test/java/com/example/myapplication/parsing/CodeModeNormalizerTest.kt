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
    fun normalizesTensAndOnes() {
        val result = normalizer.normalize("dwadziescia jeden")
        assertEquals("21", result.normalized)
    }

    @Test
    fun normalizesTensAndOnesVariants() {
        val thirtySix = normalizer.normalize("trzydziesci szesc")
        assertEquals("36", thirtySix.normalized)
        val fiftyNine = normalizer.normalize("piecdziesiat dziewiec")
        assertEquals("59", fiftyNine.normalized)
        val ninetyNine = normalizer.normalize("dziewiecdziesiat dziewiec")
        assertEquals("99", ninetyNine.normalized)
    }

    @Test
    fun keepsFullTensSegment() {
        val result = normalizer.normalize("trzydziesci")
        assertEquals("30", result.normalized)
    }

    @Test
    fun keepsHundredsTensAndOnesRegression() {
        val result = normalizer.normalize("piecset trzydziesci cztery")
        assertEquals("534", result.normalized)
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
    fun normalizesNaAndRazyAsX() {
        val naResult = normalizer.normalize("3 na 4")
        assertEquals("3x4", naResult.normalized)
        val razyResult = normalizer.normalize("3 razy 4")
        assertEquals("3x4", razyResult.normalized)
    }

    @Test
    fun normalizesFuzzyYAliases() {
        val greckaResult = normalizer.normalize("grecka")
        assertEquals("Y", greckaResult.normalized)
        val igregResult = normalizer.normalize("igreg")
        assertEquals("Y", igregResult.normalized)
        val gryResult = normalizer.normalize("gry")
        assertEquals("Y", gryResult.normalized)
    }

    @Test
    fun keepsCodeModeRegressionSample() {
        val result = normalizer.normalize("a kropka 0204 zet 2035")
        assertEquals("A.0204Z2035", result.normalized)
    }
}
