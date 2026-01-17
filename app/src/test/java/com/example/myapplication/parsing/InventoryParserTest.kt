package com.example.myapplication.parsing

import com.example.myapplication.ParseStatus
import com.example.myapplication.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class InventoryParserTest {
    private val parser = InventoryParser()

    @Test
    fun parsesCableDimensions() {
        val result = parser.parse("igrek de igrek trzy na dwa i pol")
        assertEquals(ParseStatus.OK, result.status)
        assertEquals("YDY 3x2,5", result.normalizedText)
    }

    @Test
    fun parsesCodeWithSeparators() {
        val result = parser.parse("ce ha cztery myslnik sto piecdziesiat slash bax")
        assertEquals(ParseStatus.OK, result.status)
        assertEquals("CH4-150/BAX", result.normalizedText)
    }

    @Test
    fun extractsQuantityAndUnit() {
        val result = parser.parse("hager b szesnascie 12 sztuk")
        assertEquals(ParseStatus.OK, result.status)
        assertEquals("HAGER B16", result.normalizedText)
        assertEquals(12, result.extractedQuantity)
        assertEquals(UnitType.SZT, result.extractedUnit)
    }

    @Test
    fun handlesDigitsWithQuantity() {
        val result = parser.parse("hager b 16 5 sztuk")
        assertEquals(ParseStatus.OK, result.status)
        assertEquals("HAGER B16", result.normalizedText)
        assertEquals(5, result.extractedQuantity)
        assertEquals(UnitType.SZT, result.extractedUnit)
    }

    @Test
    fun normalizesNamedCable() {
        val result = parser.parse("kabel trzy na dwa i pol")
        assertEquals(ParseStatus.OK, result.status)
        assertEquals("KABEL 3x2,5", result.normalizedText)
    }

    @Test
    fun normalizesAnotherNamedCable() {
        val result = parser.parse("przewod dwa na dwa i pol")
        assertEquals(ParseStatus.OK, result.status)
        assertEquals("PRZEWOD 2x2,5", result.normalizedText)
    }

    @Test
    fun keepsQuantityWhenPresentInText() {
        val result = parser.parse("igrek de igrek trzy na dwa i pol 15 sztuk")
        assertEquals(ParseStatus.OK, result.status)
        assertEquals("YDY 3x2,5", result.normalizedText)
        assertEquals(15, result.extractedQuantity)
        assertEquals(UnitType.SZT, result.extractedUnit)
    }

    @Test
    fun warnsOnUnknownWords() {
        val result = parser.parse("nieznane slowo 5 sztuk")
        assertEquals(ParseStatus.WARNING, result.status)
        assertEquals("NIEZNANE SLOWO", result.normalizedText)
        assertEquals(5, result.extractedQuantity)
        assertEquals(UnitType.SZT, result.extractedUnit)
    }

    @Test
    fun failsOnBlankInput() {
        val result = parser.parse(" ")
        assertEquals(ParseStatus.FAIL, result.status)
        assertNull(result.normalizedText)
    }

    @Test
    fun handlesCodeWithoutSuffix() {
        val result = parser.parse("ce ha cztery myslnik sto piecdziesiat")
        assertEquals(ParseStatus.OK, result.status)
        assertEquals("CH4-150", result.normalizedText)
    }

    @Test
    fun normalizesBreakerCode() {
        val result = parser.parse("b szesnascie")
        assertEquals(ParseStatus.OK, result.status)
        assertEquals("B16", result.normalizedText)
    }

    @Test
    fun retainsQuantityEvenWhenNoText() {
        val result = parser.parse("20 sztuk")
        assertEquals(ParseStatus.FAIL, result.status)
        assertNotNull(result.extractedQuantity)
        assertEquals(20, result.extractedQuantity)
        assertEquals(UnitType.SZT, result.extractedUnit)
    }
}
