package com.example.myapplication

private data class DebugQuantitySplit(val partA: String, val partB: String)

internal data class DebugViewData(
    val id: String,
    val type: RowType,
    val voskRaw: String?,
    val routerInputSanitized: String,
    val partA: String,
    val partB: String,
    val codeMode: Boolean,
    val normalizedForSave: String,
    val qtyUnit: String,
    val savedDisplay: String,
    val savedRawText: String,
    val savedNormalizedText: String,
    val savedQtyUnit: String
)

internal fun sanitizeRouterInput(text: String): String {
    return text.trim().replace(Regex("\\s+"), " ")
}

private fun normalizePolishToken(token: String): String {
    return token.lowercase()
        .replace('ą', 'a')
        .replace('ć', 'c')
        .replace('ę', 'e')
        .replace('ł', 'l')
        .replace('ń', 'n')
        .replace('ó', 'o')
        .replace('ś', 's')
        .replace('ż', 'z')
        .replace('ź', 'z')
}

private fun splitByQuantityMarkerDebug(text: String): DebugQuantitySplit? {
    val matches = Regex("\\S+").findAll(text)
    for (match in matches) {
        val token = match.value.trim(',', '.', ':', ';')
        if (token.isBlank()) continue
        val normalized = normalizePolishToken(token)
        if (normalized == "ilosc") {
            val partA = text.substring(0, match.range.first).trim()
            val partB = text.substring(match.range.last + 1).trim()
            return DebugQuantitySplit(partA = partA, partB = partB)
        }
    }
    return null
}

internal fun buildDebugViewData(row: SpisRow, codeMode: Boolean): DebugViewData {
    val voskRawText = row.voskRawText?.ifBlank { null }
    val routerInputSource = voskRawText ?: row.rawText
    val routerInputSanitized = sanitizeRouterInput(routerInputSource)
    val split = splitByQuantityMarkerDebug(routerInputSanitized)
    val partA = split?.partA ?: routerInputSanitized
    val partB = split?.partB.orEmpty()
    val normalizedForSave = row.normalizedText ?: row.rawText
    val qtyUnit = "${row.quantity} ${row.unit?.label.orEmpty()}".trim()
    val savedDisplay = "${row.rawText} | ${row.quantity} ${row.unit?.label}"
    val savedQtyUnit = "${row.quantity} ${row.unit?.label.orEmpty()}".trim()
    return DebugViewData(
        id = row.id,
        type = row.type,
        voskRaw = voskRawText,
        routerInputSanitized = routerInputSanitized,
        partA = partA,
        partB = partB,
        codeMode = codeMode,
        normalizedForSave = normalizedForSave,
        qtyUnit = qtyUnit,
        savedDisplay = savedDisplay,
        savedRawText = row.rawText,
        savedNormalizedText = row.normalizedText ?: "null",
        savedQtyUnit = savedQtyUnit
    )
}

internal fun buildDebugPayload(row: SpisRow, index: Int, codeMode: Boolean): String {
    val data = buildDebugViewData(row, codeMode)
    return buildString {
        append("Entry #")
        append(index + 1)
        append(" (id: ")
        append(data.id)
        append(")\n")
        append("type: ")
        append(data.type)
        append("\n")
        append("vosk_raw: ")
        append(data.voskRaw ?: "-")
        append("\n")
        append("router_input: ")
        append(data.routerInputSanitized)
        append("\n")
        append("router_input_sanitized: ")
        append(data.routerInputSanitized)
        append("\n")
        append("partA: ")
        append(data.partA)
        append("\n")
        append("partB: ")
        append(data.partB)
        append("\n")
        append("codeMode: ")
        append(if (data.codeMode) "ON" else "OFF")
        append("\n")
        append("normalizedA: ")
        append(data.normalizedForSave)
        append("\n")
        append("qty/unit: ")
        append(data.qtyUnit)
        append("\n")
        append("saved_display: ")
        append(data.savedDisplay)
        append("\n")
        append("saved_rawText: ")
        append(data.savedRawText)
        append("\n")
        append("saved_normalizedText: ")
        append(data.savedNormalizedText)
        append("\n")
        append("saved_qty/unit: ")
        append(data.savedQtyUnit)
    }
}

internal fun buildAllDebugPayload(
    rows: List<SpisRow>,
    debugCodeModeByRowId: Map<String, Boolean>,
    forceCodeModeNext: Boolean
): String {
    val entries = rows.filter { it.type == RowType.ITEM || it.type == RowType.MARKER }
    if (entries.isEmpty()) return ""
    return entries.mapIndexed { index, row ->
        val codeMode =
            debugCodeModeByRowId[row.id]
                ?: row.parseDebug?.any { it.contains("code mode", ignoreCase = true) }
                ?: forceCodeModeNext
        buildDebugPayload(row, index, codeMode)
    }.joinToString(separator = "\n---\n")
}
