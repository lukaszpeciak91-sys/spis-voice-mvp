package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugExportTest {

    @Test
    fun includesMarkerEntriesInDebugExport() {
        val itemRow = SpisRow(
            id = "item-1",
            type = RowType.ITEM,
            rawText = "Towar",
            voskRawText = "towar",
            quantity = 2,
            unit = UnitType.SZT,
            normalizedText = "Towar"
        )
        val markerRow = SpisRow(
            id = "marker-1",
            type = RowType.MARKER,
            rawText = "Sekcja A",
            voskRawText = "sekcja a",
            normalizedText = "Sekcja A"
        )

        val payload = buildAllDebugPayload(
            rows = listOf(itemRow, markerRow),
            debugCodeModeByRowId = emptyMap(),
            forceCodeModeNext = false
        )

        assertTrue(payload.contains("type: ITEM"))
        assertTrue(payload.contains("type: MARKER"))
        assertTrue(payload.contains("Entry #2 (id: marker-1)"))
        assertTrue(payload.contains("normalized_for_save: Sekcja A"))
        assertTrue(payload.contains("router_input_sanitized: sekcja a"))
    }

    @Test
    fun routerInputSanitizedCanDifferFromVoskRaw() {
        val row = SpisRow(
            id = "item-2",
            type = RowType.ITEM,
            rawText = "Towar",
            voskRawText = "  towar   ilosc   3  ",
            quantity = 3,
            unit = UnitType.SZT
        )

        val data = buildDebugViewData(row, codeMode = false)

        assertEquals("  towar   ilosc   3  ", data.voskRaw)
        assertEquals("towar ilosc 3", data.routerInputSanitized)
        assertTrue(data.routerInputSanitized != data.voskRaw)
    }
}
