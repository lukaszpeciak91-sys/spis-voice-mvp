package com.example.myapplication.parsing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        val plusResult = normalizer.normalize("a plus 1")
        assertEquals("A+1", plusResult.normalized)
    }

    @Test
    fun normalizesSlashAndHyphenVariantsInCodeMode() {
        val slashResult = normalizer.normalize("ceha cztery myślnik sto pięćdziesiąt slash bax")
        assertEquals("CH4-150/BAX", slashResult.normalized)
        val altSlashResult = normalizer.normalize("ch cztery pauza sto pięćdziesiąt ukośnik bax")
        assertEquals("CH4-150/BAX", altSlashResult.normalized)
        val minusResult = normalizer.normalize("A minus 12")
        assertEquals("A-12", minusResult.normalized)
    }

    @Test
    fun normalizesQAndVAliases() {
        val qResult = normalizer.normalize("ku 1")
        assertEquals("Q1", qResult.normalized)
        val vResult = normalizer.normalize("fał 2")
        assertEquals("V2", vResult.normalized)
    }

    @Test
    fun normalizesSlashAliasesInForcedCodeMode() {
        val zlLez = normalizer.normalize("A zł leż B", forceCodeMode = true)
        assertEquals("A/B", zlLez.normalized)

        val stres = normalizer.normalize("2 stres 4 stres 6", forceCodeMode = true)
        assertEquals("2/4/6", stres.normalized)

        val zLasu = normalizer.normalize("AB z lasu D3", forceCodeMode = true)
        assertEquals("AB/D3", zLasu.normalized)
    }

    @Test
    fun normalizesHyphenAliasesInForcedCodeMode() {
        val myslNic = normalizer.normalize("2 myśl nic 4 myśl nic 6", forceCodeMode = true)
        assertEquals("2-4-6", myslNic.normalized)

        val pauzaDeclensions = normalizer.normalize("dwa pauzę cztery pauzą sześć", forceCodeMode = true)
        assertEquals("2-4-6", pauzaDeclensions.normalized)

        val mySilnik = normalizer.normalize("A my silnik B", forceCodeMode = true)
        assertEquals("A-B", mySilnik.normalized)
    }

    @Test
    fun normalizesCommaAliasesInCodeMode() {
        val basic = normalizer.normalize("dwa przecinek cztery przecinek sześć", forceCodeMode = true)
        assertEquals("2,4,6", basic.normalized)

        val mixed = normalizer.normalize("2 przecinek 4 minus 6", forceCodeMode = true)
        assertEquals("2,4-6", mixed.normalized)
    }

    @Test
    fun keepsPrzecinekLiteralOutsideCodeMode() {
        val result = normalizer.normalize("dwa przecinek cztery")
        assertEquals("DWAPRZECINEKCZTERY", result.normalized)
    }

    @Test
    fun normalizesDotAliasesInForcedCodeMode() {
        val krupka = normalizer.normalize("2 krupka 4 krupka 6", forceCodeMode = true)
        assertEquals("2.4.6", krupka.normalized)

        val kropkaVariants = normalizer.normalize("2 kropkę 4 kropka 6", forceCodeMode = true)
        assertEquals("2.4.6", kropkaVariants.normalized)
    }

    @Test
    fun normalizesLetterAliasesInCodeContext() {
        val walu = normalizer.normalize("A wału B", forceCodeMode = true)
        assertEquals("AVB", walu.normalized)

        val jot = normalizer.normalize("jot 1", forceCodeMode = true)
        assertEquals("J1", jot.normalized)

        val iot = normalizer.normalize("iot 2", forceCodeMode = true)
        assertEquals("J2", iot.normalized)
    }

    @Test
    fun keepsAliasWordsAsPlainTextWithoutCodeModeOrNeighborhood() {
        val offMode = normalizer.normalize("to jest wału")
        assertEquals("TOJESTWALU", offMode.normalized)

        val noFuzzyNoForced = normalizer.normalize("to jest stres")
        assertEquals("TOJESTSTRES", noFuzzyNoForced.normalized)
    }

    @Test
    fun collapsesConsecutiveCanonicalSeparators() {
        val hyphen = normalizer.normalize("2 myśl nic myśl nic 4", forceCodeMode = true)
        assertEquals("2-4", hyphen.normalized)

        val slash = normalizer.normalize("2 stres stres 4", forceCodeMode = true)
        assertEquals("2/4", slash.normalized)

        val dot = normalizer.normalize("2 krupka krupka 4", forceCodeMode = true)
        assertEquals("2.4", dot.normalized)
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

    @Test
    fun normalizesFractionsAndHalves() {
        val cableResult = normalizer.normalize("igrek ka igrek trzy na jeden i pół")
        assertEquals("YKY3x1,5", cableResult.normalized)
        val halfResult = normalizer.normalize("półtora")
        assertEquals("1,5", halfResult.normalized)
        val mixedHalfResult = normalizer.normalize("dwa i jedna druga")
        assertEquals("2,5", mixedHalfResult.normalized)
        val mixedQuarterResult = normalizer.normalize("jeden i trzy czwarte")
        assertEquals("1,75", mixedQuarterResult.normalized)
        val halfFractionResult = normalizer.normalize("jedna druga")
        assertEquals("1/2", halfFractionResult.normalized)
        val quarterFractionResult = normalizer.normalize("trzy czwarte")
        assertEquals("3/4", quarterFractionResult.normalized)
        val eighthFractionResult = normalizer.normalize("trzy ósme")
        assertEquals("3/8", eighthFractionResult.normalized)
        val spokenSlashFractionResult = normalizer.normalize("trzy łamane przez osiem")
        assertEquals("3/8", spokenSlashFractionResult.normalized)
    }

    @Test
    fun keepsLetterISafety() {
        val letterResult = normalizer.normalize("i 150")
        assertEquals("I150", letterResult.normalized)
        val igrekResult = normalizer.normalize("igrek")
        assertEquals("Y", igrekResult.normalized)
        val halfResult = normalizer.normalize("dwa i pół")
        assertEquals("2,5", halfResult.normalized)
    }


    @Test
    fun normalizesCableManufacturerSuffixVariantsToNkt() {
        val spokenSuffix = normalizer.normalize("igrek ka igrek trzy na dwa i pół en ka te")
        assertEquals("YKY3x2,5NKT", spokenSuffix.normalized)

        val mkdSuffix = normalizer.normalize("igrek ka igrek trzy na dwa i pół mkd")
        assertEquals("YKY3x2,5NKT", mkdSuffix.normalized)

        val mkpSuffix = normalizer.normalize("igrek ka igrek trzy na dwa i pół mkp")
        assertEquals("YKY3x2,5NKT", mkpSuffix.normalized)

        val kateSuffix = normalizer.normalize("igrek ka igrek trzy na dwa i pół kate")
        assertEquals("YKY3x2,5NKT", kateSuffix.normalized)
    }

    @Test
    fun doesNotChangeNonCableCodesWithSimilarSuffix() {
        val result = normalizer.normalize("abc 123 mkp")
        assertEquals("ABC123MKP", result.normalized)
    }


    @Test
    fun preservesMidLetterDOnlyInDigitContext() {
        val doVariant = normalizer.normalize("jam 60 do 40 pauza 500")
        assertEquals("JAM60D40-500", doVariant.normalized)

        val deVariant = normalizer.normalize("jam 60 de 40 pauza 500")
        assertEquals("JAM60D40-500", deVariant.normalized)

        val nonContext = normalizer.normalize("jam do poprawki")
        assertEquals("JAMDOPRAWKI", nonContext.normalized)
    }

    @Test
    fun normalizesCableDimensionWithPoltorejAndSkipsMeterToken() {
        val result = normalizer.normalize("yky jeden na poltorej m kate")
        assertEquals("YKY1x1,5NKT", result.normalized)
    }

    @Test
    fun keepsRawCodeInputs() {
        val codeSample = normalizer.normalize("A.0204Z2035")
        assertEquals("A.0204Z2035", codeSample.normalized)
        val slashSample = normalizer.normalize("CH4-150/BAX")
        assertEquals("CH4-150/BAX", slashSample.normalized)
    }

    @Test
    fun classifiesAndAssemblesSpokenNumericCodeWithSeparators() {
        val result = normalizer.normalize("sto sześć pauza siedemdziesiąt osiem łamane przez dziewięć")
        assertEquals("106-78/9", result.normalized)
        assertEquals(CodeModeNormalizer.CodeModeClass.SPOKEN_NUMERIC_CODE, result.codeModeClass)
        assertTrue(result.assemblySteps.contains("hyphen:-"))
        assertTrue(result.assemblySteps.contains("slash:/"))
    }

    @Test
    fun assemblesMultiSegmentSpokenNumericCodeByConcatenation() {
        val result = normalizer.normalize("sto dwa osiemdziesiat piec siedemnascie")
        assertEquals("1028517", result.normalized)
        assertEquals(CodeModeNormalizer.CodeModeClass.SPOKEN_NUMERIC_CODE, result.codeModeClass)
    }

    @Test
    fun keepsAlphanumClassificationForMixedCode() {
        val result = normalizer.normalize("trzy es nr trzydzieści zero zero")
        assertEquals("3S300", result.normalized)
        assertEquals(CodeModeNormalizer.CodeModeClass.ALPHANUM_CODE, result.codeModeClass)
    }
}
