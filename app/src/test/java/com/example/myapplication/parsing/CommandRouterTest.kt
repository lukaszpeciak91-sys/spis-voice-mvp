package com.example.myapplication.parsing

import com.example.myapplication.ParseStatus
import com.example.myapplication.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandRouterTest {
    private val router = CommandRouter()

    @Test
    fun routesQuantityInForcedCodeMode() {
        val routed = router.route(
            "igrek de igrek 3 na 3 ilosc piecdziesiat cztery metry",
            forceCodeMode = true
        )
        val item = routed.result as VoiceCommandResult.Item
        assertEquals(CommandRouter.Route.CODE, routed.route)
        assertTrue(item.name.contains("3x3"))
        assertEquals(54, item.quantity)
        assertEquals(UnitType.M, item.unit)
        assertEquals(ParseStatus.OK, item.parseStatus)
    }

    @Test
    fun routesQuantityWithMetrowInForcedCodeMode() {
        val routed = router.route(
            "igrek ka igrek 3 na 4 ilosc dwadziescia szesc metrow",
            forceCodeMode = true
        )
        val item = routed.result as VoiceCommandResult.Item
        assertEquals(CommandRouter.Route.CODE, routed.route)
        assertTrue(item.name.contains("3x4"))
        assertEquals(26, item.quantity)
        assertEquals(UnitType.M, item.unit)
        assertEquals(ParseStatus.OK, item.parseStatus)
    }

    @Test
    fun keepsForcedCodeModeRegressionSample() {
        val routed = router.route("a kropka 0204 zet 2035", forceCodeMode = true)
        val item = routed.result as VoiceCommandResult.Item
        assertEquals(CommandRouter.Route.CODE, routed.route)
        assertEquals("A.0204Z2035", item.name)
        assertEquals(ParseStatus.OK, item.parseStatus)
    }

    @Test
    fun keepsPartBNumericParsingUnchangedInForcedCodeMode() {
        val routed = router.route(
            "sto dwa osiemdziesiat piec ilosc dwadziescia szesc metrow",
            forceCodeMode = true
        )
        val item = routed.result as VoiceCommandResult.Item
        assertEquals(CommandRouter.Route.CODE, routed.route)
        assertEquals("10285", item.name)
        assertEquals(26, item.quantity)
        assertEquals(UnitType.M, item.unit)
    }

    @Test
    fun preservesMidCodeLetterInForcedCodeModeWithQuantitySplit() {
        val routed = router.route("JAM 60 do 40 pauza 500 ilosc 10 sztuk", forceCodeMode = true)
        val item = routed.result as VoiceCommandResult.Item
        assertEquals(CommandRouter.Route.CODE, routed.route)
        assertEquals("JAM60D40-500", item.name)
        assertEquals(10, item.quantity)
        assertEquals(ParseStatus.OK, item.parseStatus)
    }


    @Test
    fun normalizesCablePoltorejAndNktSuffixWithQuantitySplit() {
        val routed = router.route("YKY jeden na półtorej m kate ilosc 1 szt", forceCodeMode = true)
        val item = routed.result as VoiceCommandResult.Item
        assertEquals(CommandRouter.Route.CODE, routed.route)
        assertEquals("YKY1x1,5NKT", item.name)
        assertEquals(1, item.quantity)
        assertEquals(UnitType.SZT, item.unit)
        assertEquals(ParseStatus.OK, item.parseStatus)
    }

    @Test
    fun keepsOffModeUnchangedForPlainSpokenNumbers() {
        val routed = router.route("sto dwa osiemdziesiat piec")
        val item = routed.result as VoiceCommandResult.Item
        assertEquals(CommandRouter.Route.NONE, routed.route)
        assertEquals("sto dwa osiemdziesiat piec", item.name)
        assertEquals(ParseStatus.OK, item.parseStatus)
    }

    @Test
    fun offModeOrdinaryWordsAreNotTurnedIntoCodes() {
        val routed = router.route("to jest zwykly opis do poprawki", forceCodeMode = false)
        val item = routed.result as VoiceCommandResult.Item
        assertEquals(CommandRouter.Route.NONE, routed.route)
        assertEquals("TO JEST ZWYKLY OPIS DO POPRAWKI", item.name)
    }

    @Test
    fun forcedCodeModeLeavesNonCodeTextUntouchedWhenNoCodeNormalizationApplies() {
        val routed = router.route("to jest zwykly opis", forceCodeMode = true)
        val item = routed.result as VoiceCommandResult.Item
        assertEquals(CommandRouter.Route.CODE, routed.route)
        assertEquals("to jest zwykly opis", item.name)
    }
}
