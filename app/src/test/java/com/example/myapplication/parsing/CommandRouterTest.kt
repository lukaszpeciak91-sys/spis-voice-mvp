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
}
