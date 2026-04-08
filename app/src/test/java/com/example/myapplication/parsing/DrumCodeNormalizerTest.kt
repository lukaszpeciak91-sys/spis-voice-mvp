package com.example.myapplication.parsing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrumCodeNormalizerTest {
    private val normalizer = DrumCodeNormalizer()

    @Test
    fun parsesKfrFamilyAndResolvesFullCode() {
        val result = normalizer.normalize("ka ef er osiem zero siedem jeden jeden zero dwa dwa")
        assertEquals("KFR8 0711-022", result.canonicalStem)
        assertEquals("KFR", result.resolvedPrefix)
        assertEquals("KFR8 0711-022/KWA", result.matchedFullCode)
    }

    @Test
    fun parsesPlbFamilyAndResolvesFullCode() {
        val result = normalizer.normalize("pe el be 6 ka 1 0 7 8 4")
        assertEquals("PLB6-K10784", result.canonicalStem)
        assertEquals("PLB6-K10784/NKT", result.matchedFullCode)
    }

    @Test
    fun parsesTFamilyWithGeneratedSeparators() {
        val result = normalizer.normalize("te 20 minus 10 pauza 940")
        assertEquals("T-20-10-940", result.canonicalStem)
        assertEquals("T-20-10-940/TEC", result.matchedFullCode)
    }

    @Test
    fun parsesTypeMainYyFamilyWithLeadingZeros() {
        val result = normalizer.normalize("14 1011 24")
        assertEquals("14-1011-24", result.canonicalStem)

        val leadingZeros = normalizer.normalize("zero osiem a 25 298")
        assertEquals("08A/25-298", leadingZeros.canonicalStem)
    }

    @Test
    fun keepsSafeFallbackWhenFamilyIsUnknown() {
        val result = normalizer.normalize("dziwny kod 1 2 3")
        assertEquals("DZIWNYKOD123", result.canonicalStem)
        assertEquals("FALLBACK", result.family)
        assertNull(result.matchedFullCode)
        assertTrue(result.debugSteps.contains("raw="))
    }
}
