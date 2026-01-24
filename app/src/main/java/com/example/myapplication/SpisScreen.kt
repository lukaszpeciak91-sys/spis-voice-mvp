package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.runtime.snapshotFlow
import com.example.myapplication.parsing.InventoryParser
import com.example.myapplication.parsing.VoiceCommandParser
import com.example.myapplication.parsing.VoiceCommandResult
import com.example.myapplication.vosk.TranscriptionStartResult
import com.example.myapplication.vosk.TranscriptionState
import com.example.myapplication.vosk.VoskTranscriptionManager
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(kotlinx.coroutines.FlowPreview::class)

private const val TAG = "SpisScreen"
private const val CSV_HEADER = "row_type;text;qty_counted;unit"

private data class CatalogImportResult(
    val metadata: CatalogMetadata?,
    val errorMessage: String?
)

private fun escapeCsv(value: String): String {
    val needsQuotes = value.contains(';') || value.contains('"') || value.contains('\n') || value.contains('\r')
    if (!needsQuotes) return value
    val escaped = value.replace("\"", "\"\"")
    return "\"$escaped\""
}

private fun buildRawCsv(rows: List<SpisRow>): String {
    return buildString {
        append(CSV_HEADER)
        append("\n")
        rows.forEach { row ->
            when (row.type) {
                RowType.ITEM -> {
                    val qty = row.quantity?.toString().orEmpty()
                    val unitLabel = row.unit?.label.orEmpty()
                    append(
                        listOf(
                            "ITEM",
                            row.rawText,
                            qty,
                            unitLabel
                        ).joinToString(";") { escapeCsv(it) }
                    )
                }

                RowType.MARKER -> {
                    append(
                        listOf(
                            "MARKER",
                            row.rawText,
                            "",
                            ""
                        ).joinToString(";") { escapeCsv(it) }
                    )
                }
            }
            append("\n")
        }
    }
}

private fun defaultExportFileName(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
    return "spis_export_RAW_${formatter.format(Date())}.csv"
}

private fun formatCatalogImportDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

private fun detectDelimiter(headerLine: String): Char {
    return when {
        headerLine.contains(';') -> ';'
        headerLine.contains(',') -> ','
        headerLine.contains('\t') -> '\t'
        else -> ';'
    }
}

private fun parseHeaders(headerLine: String): List<String> {
    val delimiter = detectDelimiter(headerLine)
    return headerLine.split(delimiter)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String {
    val resolver = context.contentResolver
    val cursor = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index != -1) {
                return it.getString(index)
            }
        }
    }
    return uri.lastPathSegment ?: "catalog.csv"
}

private fun readCatalogMetadata(context: android.content.Context, uri: Uri): CatalogImportResult {
    val resolver = context.contentResolver
    val filename = queryDisplayName(context, uri)
    val inputStream = resolver.openInputStream(uri)
        ?: return CatalogImportResult(null, "Nie udało się otworzyć pliku CSV.")
    return try {
        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
            val headerLine = reader.readLine()
                ?: return CatalogImportResult(null, "Plik CSV jest pusty.")
            val trimmedHeader = headerLine.trim()
            if (trimmedHeader.isEmpty()) {
                return CatalogImportResult(null, "Nagłówek CSV jest pusty.")
            }
            val headers = parseHeaders(trimmedHeader)
            if (headers.isEmpty()) {
                return CatalogImportResult(null, "Nie udało się odczytać nagłówków CSV.")
            }
            var rowCount = 0
            reader.forEachLine { line ->
                if (line.isNotBlank()) {
                    rowCount += 1
                }
            }
            CatalogImportResult(
                CatalogMetadata(
                    catalogUri = uri.toString(),
                    sourceFilename = filename,
                    rowCount = rowCount,
                    importedAt = System.currentTimeMillis(),
                    detectedHeaders = headers
                ),
                null
            )
        }
    } catch (ex: Exception) {
        CatalogImportResult(null, "Nie udało się odczytać CSV.")
    }
}

private fun hasCatalogAccess(context: android.content.Context, uri: Uri): Boolean {
    val resolver = context.contentResolver
    val hasPermission = resolver.persistedUriPermissions.any { permission ->
        permission.uri == uri && permission.isReadPermission
    }
    if (!hasPermission) return false
    return try {
        resolver.openInputStream(uri)?.close()
        true
    } catch (_: Exception) {
        false
    }
}

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
@OptIn(ExperimentalLayoutApi::class)
fun SpisScreen() {

    var inputText by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf(UnitType.SZT) }
    var expanded by remember { mutableStateOf(false) }

    val rows = remember { mutableStateListOf<SpisRow>() }
    var editingId by remember { mutableStateOf<String?>(null) }
    var parseDialogRow by remember { mutableStateOf<SpisRow?>(null) }
    var showCsvDialog by remember { mutableStateOf(false) }

    var showMarkerDialog by remember { mutableStateOf(false) }
    var markerText by remember { mutableStateOf("") }

    var editingMarkerId by remember { mutableStateOf<String?>(null) }
    val textFocusRequester = remember { FocusRequester() }

    val context = LocalContext.current
    val recorder = remember { AudioRecorder(context) }
    val parser = remember { InventoryParser() }
    val voiceCommandParser = remember { VoiceCommandParser() }
    var pendingExportCsv by remember { mutableStateOf<String?>(null) }
    var catalogMetadata by remember { mutableStateOf<CatalogMetadata?>(null) }
    var catalogError by remember { mutableStateOf<String?>(null) }

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

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        val csv = pendingExportCsv
        if (uri == null) {
            pendingExportCsv = null
            Toast.makeText(context, "Eksport anulowany.", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        if (csv == null) {
            Toast.makeText(context, "Brak danych do zapisu.", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(csv)
                }
            }
            Toast.makeText(context, "Zapisano plik: $uri", Toast.LENGTH_SHORT).show()
        } catch (ex: Exception) {
            Log.e(TAG, "CSV export failed", ex)
            Toast.makeText(context, "Nie udało się zapisać CSV.", Toast.LENGTH_SHORT).show()
        } finally {
            pendingExportCsv = null
        }
    }

    val importCatalogLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) {
            Toast.makeText(context, "Import anulowany.", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (ex: Exception) {
            Log.e(TAG, "Persistable permission failed", ex)
        }
        val result = readCatalogMetadata(context, uri)
        val errorMessage = result.errorMessage
        if (errorMessage != null) {
            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        val metadata = result.metadata
        if (metadata == null) {
            Toast.makeText(context, "Nie udało się zapisać metadanych katalogu.", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        CatalogStorage.save(context, metadata)
        catalogMetadata = metadata
        catalogError = null
        Toast.makeText(context, "Zaimportowano katalog: ${metadata.sourceFilename}", Toast.LENGTH_SHORT).show()
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
        val loadedCatalog = CatalogStorage.load(context)
        if (loadedCatalog != null) {
            catalogMetadata = loadedCatalog
            val uri = Uri.parse(loadedCatalog.catalogUri)
            if (!hasCatalogAccess(context, uri)) {
                catalogError = "Catalog file is no longer accessible."
            }
        }
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

    fun handleTranscriptionResult(
        jobId: String,
        audioPath: String,
        resultText: String?,
        errorMessage: String?
    ) {
        val index = rows.indexOfFirst { it.transcriptionJobId == jobId }
        if (index == -1) return
        val audioFile = File(audioPath)
        val currentRow = rows[index]
        val trimmed = resultText?.trim().orEmpty()
        if (trimmed.isNotEmpty()) {
            when (val voiceResult = voiceCommandParser.parse(trimmed)) {
                is VoiceCommandResult.AddMarker -> {
                    rows[index] = SpisRow(
                        id = currentRow.id,
                        type = RowType.MARKER,
                        rawText = voiceResult.name
                    )
                    Log.i(TAG, "VoiceCommand: ADD_MARKER -> \"${voiceResult.name}\"")
                }

                is VoiceCommandResult.Item -> {
                    val resolvedQuantity = voiceResult.quantity ?: currentRow.quantity ?: 1
                    val resolvedUnit = voiceResult.unit ?: currentRow.unit ?: UnitType.SZT
                    rows[index] = currentRow.copy(
                        rawText = voiceResult.name,
                        quantity = resolvedQuantity,
                        unit = resolvedUnit,
                        normalizedText = voiceResult.name.ifBlank { null },
                        parseStatus = voiceResult.parseStatus,
                        parseDebug = voiceResult.debug,
                        transcriptionJobId = null
                    )
                    Log.i(
                        TAG,
                        "VoiceCommand: ITEM -> \"${voiceResult.name}\" qty=${voiceResult.quantity} unit=${voiceResult.unit?.label}"
                    )
                }
            }
            audioFile.delete()
            Log.i(TAG, "Audio cleanup success: ${audioFile.name}")
        } else {
            val failureMessage = errorMessage ?: "Transcription failed."
            rows[index] = currentRow.copy(
                rawText = "[AUDIO] ${audioFile.name} (${failureMessage})",
                normalizedText = null,
                parseStatus = ParseStatus.FAIL,
                parseDebug = listOf(failureMessage),
                transcriptionJobId = null
            )
            Log.i(TAG, "Audio cleanup skipped (failure): ${audioFile.name}")
        }
    }

    LaunchedEffect(Unit) {
        VoskTranscriptionManager.transcriptionState.collect { state ->
            when (state) {
                is TranscriptionState.Success -> handleTranscriptionResult(
                    jobId = state.jobId,
                    audioPath = state.audioPath,
                    resultText = state.text,
                    errorMessage = null
                )

                is TranscriptionState.Error -> handleTranscriptionResult(
                    jobId = state.jobId,
                    audioPath = state.audioPath,
                    resultText = null,
                    errorMessage = state.message
                )

                is TranscriptionState.Cancelled -> handleTranscriptionResult(
                    jobId = state.jobId,
                    audioPath = state.audioPath,
                    resultText = null,
                    errorMessage = "Transcription cancelled."
                )

                else -> Unit
            }
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

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = {
                val dir = context.externalCacheDir ?: context.cacheDir
                dir.listFiles()?.forEach { it.delete() }
            }) {
                Text("Wyczyść audio cache")
            }

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

            OutlinedButton(onClick = { showCsvDialog = true }) {
                Text("CSV")
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
                    when (val startResult = VoskTranscriptionManager.startTranscription(context, file)) {
                        is TranscriptionStartResult.Started -> {
                            val audioRow = SpisRow(
                                type = RowType.ITEM,
                                rawText = "[AUDIO] ${file.name} (transcribing...)",
                                quantity = 1,
                                unit = UnitType.SZT,
                                parseStatus = ParseStatus.WARNING,
                                parseDebug = listOf("Transcribing audio..."),
                                transcriptionJobId = startResult.jobId
                            )
                            rows.add(audioRow)
                        }

                        is TranscriptionStartResult.Busy -> {
                            val failureMessage = startResult.message
                            val audioRow = SpisRow(
                                type = RowType.ITEM,
                                rawText = "[AUDIO] ${file.name} (${failureMessage})",
                                quantity = 1,
                                unit = UnitType.SZT,
                                parseStatus = ParseStatus.FAIL,
                                parseDebug = listOf(failureMessage)
                            )
                            rows.add(audioRow)
                            Log.i(TAG, "Audio cleanup skipped (failure): ${file.name}")
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

    if (showCsvDialog) {
        AlertDialog(
            onDismissRequest = { showCsvDialog = false },
            title = { Text("CSV") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (rows.isEmpty()) {
                                Toast.makeText(context, "Brak pozycji do eksportu.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val csv = buildRawCsv(rows.toList())
                            pendingExportCsv = csv
                            exportLauncher.launch(defaultExportFileName())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Export CSV")
                    }

                    OutlinedButton(
                        onClick = {
                            importCatalogLauncher.launch(
                                arrayOf(
                                    "text/csv",
                                    "text/comma-separated-values",
                                    "text/plain",
                                    "application/csv",
                                    "application/vnd.ms-excel"
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Import catalog CSV")
                    }

                    val metadata = catalogMetadata
                    if (metadata != null) {
                        Text(
                            "Catalog loaded: ${metadata.rowCount} rows | imported ${formatCatalogImportDate(metadata.importedAt)}"
                        )
                        Text("File: ${metadata.sourceFilename}")
                        if (catalogError != null) {
                            Text("Catalog file is not accessible.")
                        }
                        OutlinedButton(
                            onClick = {
                                val uri = Uri.parse(metadata.catalogUri)
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "text/csv")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (ex: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Nie udało się otworzyć CSV w zewnętrznej aplikacji.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = catalogError == null
                        ) {
                            Text("Open catalog CSV")
                        }

                        OutlinedButton(
                            onClick = {
                                CatalogStorage.clear(context)
                                catalogMetadata = null
                                catalogError = null
                                Toast.makeText(context, "Usunięto katalog.", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Delete catalog")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCsvDialog = false }) {
                    Text("Zamknij")
                }
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
