package com.openmausbot.companion.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.core.PromptCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

private val prettyJson = Json { prettyPrint = true }
private const val PREVIEW_LIMIT = 80_000

/** Full model inputs are private: only an admin-scoped pairing may open this sheet. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun PromptInspectorSheet(threadId: String, onDismiss: () -> Unit) {
    val session = LocalCompanion.current.session
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var records by remember(threadId) { mutableStateOf<List<PromptCapture>>(emptyList()) }
    var selected by remember(threadId) { mutableIntStateOf(0) }
    var view by remember(threadId) { mutableStateOf("input") }
    var search by remember(threadId) { mutableStateOf("") }
    var loading by remember(threadId) { mutableStateOf(true) }
    var error by remember(threadId) { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) scope.launch {
            try {
                val data = prettyJson.encodeToString(records)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(data.toByteArray(Charsets.UTF_8)) }
                        ?: kotlin.error("Could not open the selected file.")
                }
            } catch (failure: Exception) { error = failure.message ?: "Could not save the capture." }
        }
    }
    suspend fun load() {
        loading = true
        try {
            records = session.promptCaptures(threadId)
            selected = 0
            error = null
        } catch (failure: Exception) {
            error = failure.message ?: "Could not load captured requests."
        } finally { loading = false }
    }
    LaunchedEffect(threadId) { load() }
    val row = records.getOrNull(selected)
    val previous = row?.let { current ->
        records.drop(selected + 1).firstOrNull { it.kind == current.kind && it.provider == current.provider }
    }
    val value = when {
        row == null -> "No captured request yet."
        row.omitted && view != "diagnostics" -> "Request body exceeded the capture limit."
        view == "input" -> pretty(modelInput(row))
        view == "full" -> pretty(row.body)
        view == "changes" -> previous?.let { changedLines(pretty(modelInput(it)), pretty(modelInput(row))) }
            ?: "No previous capture of the same kind and provider."
        else -> diagnostics(row)
    }
    val preview = value.take(PREVIEW_LIMIT)
    val display = if (search.isBlank()) preview else {
        val index = preview.indexOf(search, ignoreCase = true)
        if (index < 0) "No match in preview.\n\n$preview"
        else "Match at character ${index + 1}:\n\n" + preview.drop((index - 300).coerceAtLeast(0))
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Prompt inspector", style = MaterialTheme.typography.titleLarge)
            Text("Captured requests may contain full private conversations. Keep exported files private.")
            if (loading) CircularProgressIndicator()
            if (records.isNotEmpty()) Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                records.forEachIndexed { index, capture ->
                    FilterChip(
                        selected = selected == index,
                        onClick = { selected = index },
                        label = { Text("${capture.provider} · ${capture.sentAt.takeLast(8)} · ${capture.status}") },
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
            }
            row?.let { capture ->
                val usage = capture.usage
                Text("Uncached: ${usage?.uncached ?: "—"}    Cached: ${usage?.cached ?: "—"}    Output: ${usage?.output ?: "—"}")
            }
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf("input" to "Model input", "full" to "Full request", "changes" to "Changes", "diagnostics" to "Diagnostics").forEach { (id, label) ->
                    FilterChip(selected = view == id, onClick = { view = id }, label = { Text(label) }, modifier = Modifier.padding(end = 6.dp))
                }
            }
            OutlinedTextField(search, { search = it }, label = { Text("Find in preview") }, modifier = Modifier.fillMaxWidth())
            Text(display, style = MaterialTheme.typography.bodySmall)
            if (value.length > PREVIEW_LIMIT) Text("Preview limited to 80,000 characters; copy the view or export the JSON for the full content.")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(onClick = { scope.launch { load() } }, enabled = !loading) { Text("Refresh") }
                TextButton(onClick = { clipboard.setText(AnnotatedString(value)) }, enabled = row != null) { Text("Copy view") }
                TextButton(onClick = { export.launch("prompt-inspector.json") }, enabled = row != null) { Text("Export JSON") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}

private fun pretty(value: JsonElement): String = prettyJson.encodeToString(JsonElement.serializer(), value)

private fun modelInput(record: PromptCapture): JsonElement {
    val body = record.body as? JsonObject ?: return record.body
    val fields = if (record.kind == "api-request") {
        setOf("instructions", "system", "tools", "messages", "input")
    } else setOf("system", "systemStable", "systemVolatile", "transcript", "text", "images", "integrations")
    return JsonObject(body.filterKeys { it in fields })
}

private fun diagnostics(record: PromptCapture): String = buildString {
    appendLine("Provider: ${record.provider}")
    appendLine("Kind: ${record.kind}")
    appendLine("Sent: ${record.sentAt}")
    appendLine("Status: ${record.status}")
    appendLine("Endpoint: ${record.endpoint ?: "—"}")
    appendLine("HTTP status: ${record.httpStatus ?: "—"}")
    appendLine("Duration: ${record.durationMs ?: "—"} ms")
    appendLine("Response headers: ${record.responseHeaders ?: emptyMap<String, String>()}")
    record.error?.let { appendLine("Error: $it") }
}

private fun changedLines(before: String, after: String): String {
    if (before == after) return "No changes in this view."
    val left = before.lines()
    val right = after.lines()
    var start = 0
    while (start < left.size && start < right.size && left[start] == right[start]) start++
    return buildString {
        appendLine("First changed line: ${start + 1}")
        left.drop(start).take(500).forEach { appendLine("- $it") }
        right.drop(start).take(500).forEach { appendLine("+ $it") }
        if (left.size - start > 500 || right.size - start > 500) appendLine("Diff preview limited to 500 lines per side.")
    }
}
