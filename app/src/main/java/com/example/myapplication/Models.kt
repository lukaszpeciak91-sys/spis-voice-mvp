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

enum class ParseStatus {
    OK,
    WARNING,
    FAIL
}

data class SpisRow(
    val id: String = UUID.randomUUID().toString(),
    val type: RowType,
    val rawText: String = "",
    val quantity: Int? = null,
    val unit: UnitType? = null,
    val normalizedText: String? = null,
    val parseStatus: ParseStatus? = null,
    val parseDebug: List<String>? = null,
    val transcriptionJobId: String? = null
)
data class ProjectState(
    val inputText: String = "",
    val quantityText: String = "1",
    val unit: UnitType = UnitType.SZT,
    val rows: List<SpisRow> = emptyList()
)
