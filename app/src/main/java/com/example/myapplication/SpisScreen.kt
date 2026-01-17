package com.example.myapplication

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
import com.example.myapplication.parsing.InventoryParser
import com.example.myapplication.whisper.WhisperTranscriber
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.FlowPreview::class)

private fun applyParsing(
    parser: InventoryParser,
    row: SpisRow,
    rawText: String,
    quantity: Int?,
    unit: UnitType?,
    allowPrefillQuantity: Boolean,
    allowPrefillUnit: Boolean
): SpisRow {
    val result = parser.parse(rawText)
    val resolvedQuantity = if (allowPrefillQuantity && result.extractedQuantity != null) {
        result.extractedQuantity
    } else {
        quantity
    }
    val resolvedUnit = if (allowPrefillUnit && result.extractedUnit != null) {
        result.extractedUnit
    } else {
        unit
    }
    return row.copy(
        rawText = rawText,
        quantity = resolvedQuantity,
        unit = resolvedUnit,
        normalizedText = result.normalizedText,
        parseStatus = result.status,
        parseDebug = result.debug
    )
}

@Composable
fun SpisScreen() {

    var inputText by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf(UnitType.SZT) }
    var expanded by remember { mutableStateOf(false) }

    val rows = remember { mutableStateListOf<SpisRow>() }
    var editingId by remember { mutableStateOf<String?>(null) }
    var parseDialogRow by remember { mutableStateOf<SpisRow?>(null) }

    var showMarkerDialog by remember { mutableStateOf(false) }
    var markerText by remember { mutableStateOf("") }

    var editingMarkerId by remember { mutableStateOf<String?>(null) }
    val textFocusRequester = remember { FocusRequester() }

    val context = LocalContext.current
    val recorder = remember { AudioRecorder(context) }
    val parser = remember { InventoryParser() }
    val transcriber = remember { WhisperTranscriber(context) }
    val coroutineScope = rememberCoroutineScope()

    var isRecording by remember { mutableStateOf(false) }
    var lastAudioPath by remember { mutableStateOf<String?>(null) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = recorder.start()
            isRecording = true
            lastAudioPath = file.name
        }
    }

    LaunchedEffect(Unit) {
        val loaded = ProjectStorage.load(context)
        if (loaded != null) {
            inputText = loaded.inputText
            quantity = loaded.quantityText
            unit = loaded.unit
            rows.clear()
            rows.addAll(
                loaded.rows.map { row ->
                    if (row.type == RowType.ITEM && row.parseStatus == null) {
                        applyParsing(
                            parser = parser,
                            row = row,
                            rawText = row.rawText,
                            quantity = row.quantity,
                            unit = row.unit,
                            allowPrefillQuantity = false,
                            allowPrefillUnit = false
                        )
                    } else {
                        row
                    }
                }
            )
        }
        textFocusRequester.requestFocus()
    }

    LaunchedEffect(Unit) {
        snapshotFlow {
            ProjectState(
                inputText = inputText,
                quantityText = quantity,
                unit = unit,
                rows = rows.toList()
            )
        }
            .debounce(500)
            .distinctUntilChanged()
            .collect { state ->
                ProjectStorage.save(context, state)
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("Kod / EAN / nazwa") },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(textFocusRequester)
        )

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = quantity,
                onValueChange = { quantity = it },
                label = { Text("Ilość") },
                modifier = Modifier.width(100.dp)
            )

            Spacer(Modifier.width(8.dp))

            Box {
                Button(onClick = { expanded = true }) {
                    Text(unit.label)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    UnitType.values().forEach {
                        DropdownMenuItem(
                            text = { Text(it.label) },
                            onClick = {
                                unit = it
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row {
            Button(onClick = {
                val quantityValue = quantity.toIntOrNull() ?: 1
                val allowPrefillQuantity = quantity.trim() == "1"
                val allowPrefillUnit = unit == UnitType.SZT
                val newRow = applyParsing(
                    parser = parser,
                    row = SpisRow(type = RowType.ITEM),
                    rawText = inputText,
                    quantity = quantityValue,
                    unit = unit,
                    allowPrefillQuantity = allowPrefillQuantity,
                    allowPrefillUnit = allowPrefillUnit
                )
                rows.add(newRow)
                inputText = ""
                quantity = "1"
                textFocusRequester.requestFocus()
            }) {
                Text("Dodaj ITEM")
            }

            Spacer(Modifier.width(8.dp))

            OutlinedButton(onClick = {
                markerText = ""
                showMarkerDialog = true
                editingId = null
                editingMarkerId = null
            }) {
                Text("Dodaj MARKER")
            }
        }

        Spacer(Modifier.height(8.dp))

        Row {
            OutlinedButton(onClick = {
                val dir = context.externalCacheDir ?: context.cacheDir
                dir.listFiles()?.forEach { it.delete() }
            }) {
                Text("Wyczyść audio cache")
            }

            Spacer(Modifier.width(8.dp))

            OutlinedButton(onClick = {
                rows.clear()
                inputText = ""
                quantity = "1"
                unit = UnitType.SZT
                ProjectStorage.clear(context)
                textFocusRequester.requestFocus()
            }) {
                Text("Wyczyść spis")
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(onClick = {
            if (!isRecording) {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                val file = recorder.stop()
                isRecording = false
                if (file != null) {
                    val audioRow = SpisRow(
                        type = RowType.ITEM,
                        rawText = "[AUDIO] ${file.name} (transcribing...)",
                        quantity = 1,
                        unit = UnitType.SZT,
                        parseStatus = ParseStatus.WARNING,
                        parseDebug = listOf("Transcribing audio...")
                    )
                    rows.add(audioRow)
                    coroutineScope.launch {
                        val result = transcriber.transcribe(file)
                        val index = rows.indexOfFirst { it.id == audioRow.id }
                        if (index == -1) return@launch

                        val trimmed = result.getOrNull()?.trim().orEmpty()
                        if (result.isSuccess && trimmed.isNotEmpty()) {
                            val currentRow = rows[index]
                            val updated = applyParsing(
                                parser = parser,
                                row = currentRow,
                                rawText = trimmed,
                                quantity = currentRow.quantity ?: 1,
                                unit = currentRow.unit ?: UnitType.SZT,
                                allowPrefillQuantity = currentRow.quantity == 1,
                                allowPrefillUnit = currentRow.unit == UnitType.SZT
                            )
                            rows[index] = updated
                        } else {
                            rows[index] = rows[index].copy(
                                rawText = "[AUDIO] ${file.name} (transcription failed)",
                                normalizedText = null,
                                parseStatus = ParseStatus.FAIL,
                                parseDebug = listOf("Transcription failed.")
                            )
                        }
                    }
                }
                textFocusRequester.requestFocus()
            }
        }) {
            Text(if (!isRecording) "🎙️ Nagraj" else "⏹ Stop")
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn {
            items(rows, key = { it.id }) { row ->

                if (row.type == RowType.MARKER) {
                    val isEditingMarker = editingMarkerId == row.id

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.LightGray)
                            .clickable {
                                editingMarkerId = row.id
                                editingId = null
                            }
                            .padding(8.dp)
                    ) {
                        if (isEditingMarker) {
                            var editMarkerText by remember { mutableStateOf(row.rawText) }

                            OutlinedTextField(
                                value = editMarkerText,
                                onValueChange = { editMarkerText = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Marker") }
                            )

                            Row {
                                TextButton(onClick = {
                                    val index = rows.indexOfFirst { it.id == row.id }
                                    if (index != -1) {
                                        rows[index] = row.copy(
                                            rawText = editMarkerText.trim().ifBlank { "MARKER" }
                                        )
                                    }
                                    editingMarkerId = null
                                }) { Text("Zapisz") }

                                TextButton(onClick = { editingMarkerId = null }) { Text("Anuluj") }

                                TextButton(onClick = {
                                    rows.remove(row)
                                    editingMarkerId = null
                                }) { Text("Usuń") }
                            }
                        } else {
                            Text(
                                text = row.rawText,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {

                    val isEditing = editingId == row.id

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                editingId = row.id
                                editingMarkerId = null
                            }
                            .padding(8.dp)
                    ) {
                        if (isEditing) {
                            var editText by remember { mutableStateOf(row.rawText) }
                            var editQty by remember { mutableStateOf(row.quantity.toString()) }

                            OutlinedTextField(
                                value = editText,
                                onValueChange = { editText = it },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(4.dp))

                            OutlinedTextField(
                                value = editQty,
                                onValueChange = { editQty = it },
                                modifier = Modifier.width(100.dp)
                            )

                            Row {
                                TextButton(onClick = {
                                    val index = rows.indexOfFirst { it.id == row.id }
                                    if (index != -1) {
                                        val editedQuantity = editQty.toIntOrNull()
                                        val quantityManualChange =
                                            editedQuantity != null && editedQuantity != row.quantity
                                        val resolvedQuantity = editedQuantity ?: row.quantity
                                        rows[index] = applyParsing(
                                            parser = parser,
                                            row = row,
                                            rawText = editText,
                                            quantity = resolvedQuantity,
                                            unit = row.unit,
                                            allowPrefillQuantity = !quantityManualChange,
                                            allowPrefillUnit = row.unit == UnitType.SZT
                                        )
                                    }
                                    editingId = null
                                }) {
                                    Text("Zapisz")
                                }

                                TextButton(onClick = { editingId = null }) {
                                    Text("Anuluj")
                                }

                                TextButton(onClick = {
                                    rows.remove(row)
                                }) {
                                    Text("Usuń")
                                }
                            }
                        } else {
                            val status = row.parseStatus ?: ParseStatus.WARNING
                            val statusIcon = when (status) {
                                ParseStatus.OK -> "✅"
                                ParseStatus.WARNING -> "⚠️"
                                ParseStatus.FAIL -> "❌"
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = statusIcon,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .clickable(enabled = status != ParseStatus.OK) {
                                            parseDialogRow = row
                                        }
                                )
                                Text("${row.rawText} | ${row.quantity} ${row.unit?.label}")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMarkerDialog) {
        AlertDialog(
            onDismissRequest = { showMarkerDialog = false },
            title = { Text("Nazwa markera") },
            text = {
                OutlinedTextField(
                    value = markerText,
                    onValueChange = { markerText = it },
                    label = { Text("np. REGAŁ II / KABLE") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = markerText.trim().ifBlank { "MARKER" }
                    rows.add(
                        SpisRow(
                            type = RowType.MARKER,
                            rawText = name
                        )
                    )
                    showMarkerDialog = false
                    inputText = ""
                    quantity = "1"
                    textFocusRequester.requestFocus()
                }) { Text("Dodaj") }
            },
            dismissButton = {
                TextButton(onClick = { showMarkerDialog = false }) { Text("Anuluj") }
            }
        )
    }

    parseDialogRow?.let { row ->
        val normalizedText = row.normalizedText ?: "-"
        val debugLines = row.parseDebug?.takeIf { it.isNotEmpty() } ?: listOf("Brak szczegółów.")
        val status = row.parseStatus ?: ParseStatus.WARNING
        AlertDialog(
            onDismissRequest = { parseDialogRow = null },
            title = { Text("Szczegóły parsowania") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Wejście: ${row.rawText}")
                    Text("Znormalizowane: $normalizedText")
                    Text("Status: $status")
                    debugLines.forEach { Text("• $it") }
                }
            },
            confirmButton = {
                TextButton(onClick = { parseDialogRow = null }) { Text("Zamknij") }
            }
        )
    }
}
