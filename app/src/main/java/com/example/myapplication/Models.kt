package com.example.myapplication

import java.util.UUID

enum class RowType {
    ITEM,
    MARKER
}

enum class UnitType(val label: String) {
    SZT("szt"),
    M("m"),
    OP("op"),
    ROLKA("rolka"),
    KPL("kpl"),
    KG("kg"),
    CM("cm")
}

data class SpisRow(
    val id: String = UUID.randomUUID().toString(),
    val type: RowType,
    val rawText: String = "",
    val quantity: Int? = null,
    val unit: UnitType? = null
)
data class ProjectState(
    val inputText: String = "",
    val quantityText: String = "1",
    val unit: UnitType = UnitType.SZT,
    val rows: List<SpisRow> = emptyList()
)

