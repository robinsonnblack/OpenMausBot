package com.openmausbot.companion.ui

import android.content.Context
import com.openmausbot.companion.R
import androidx.compose.ui.res.stringResource
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
                        ?: kotlin.error(context.getString(R.string.android_remaining_prompt_inspector_sheet_d412e6e8))
                }
            } catch (failure: Exception) { error = failure.message ?: context.getString(R.string.android_remaining_prompt_inspector_sheet_73a812c4) }
        }
    }
    suspend fun load() {
        loading = true
        try {
            records = session.promptCaptures(threadId)
            selected = 0
            error = null
        } catch (failure: Exception) {
            error = failure.message ?: context.getString(R.string.android_remaining_prompt_inspector_sheet_67871712)
        } finally { loading = false }
    }
    LaunchedEffect(threadId) { load() }
    val row = records.getOrNull(selected)
    val previous = row?.let { current ->
        records.drop(selected + 1).firstOrNull { it.kind == current.kind && it.provider == current.provider }
    }
    val value = when {
        row == null -> context.getString(R.string.android_remaining_prompt_inspector_sheet_60de3406)
        row.omitted && view != "diagnostics" -> context.getString(R.string.android_remaining_prompt_inspector_sheet_189bd6ee)
        view == "input" -> pretty(modelInput(row))
        view == "full" -> pretty(row.body)
        view == "changes" -> previous?.let { changedLines(context, pretty(modelInput(it)), pretty(modelInput(row))) }
            ?: context.getString(R.string.android_remaining_prompt_inspector_sheet_ffa4bc1e)
        else -> diagnostics(context, row)
    }
    val preview = value.take(PREVIEW_LIMIT)
    val display = if (search.isBlank()) preview else {
        val index = preview.indexOf(search, ignoreCase = true)
        if (index < 0) context.getString(R.string.android_inspector_no_match, preview)
        else context.getString(R.string.android_inspector_match_at, index + 1, preview.drop((index - 300).coerceAtLeast(0)))
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.ui_prompt_inspector_0885254), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.ui_captured_requests_may_contain_full_private_08a9427))
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
                Text(stringResource(R.string.ui_inspector_token_counts, usage?.uncached ?: "—", usage?.cached ?: "—", usage?.output ?: "—"))
            }
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf(
                    "input" to R.string.ui_inspector_model_input,
                    "full" to R.string.ui_inspector_full_request,
                    "changes" to R.string.ui_inspector_changes,
                    "diagnostics" to R.string.ui_inspector_diagnostics,
                ).forEach { (id, labelRes) ->
                    FilterChip(selected = view == id, onClick = { view = id }, label = { Text(stringResource(labelRes)) }, modifier = Modifier.padding(end = 6.dp))
                }
            }
            OutlinedTextField(search, { search = it }, label = { Text(stringResource(R.string.ui_find_in_preview_82cf60d)) }, modifier = Modifier.fillMaxWidth())
            Text(display, style = MaterialTheme.typography.bodySmall)
            if (value.length > PREVIEW_LIMIT) Text(stringResource(R.string.ui_preview_limited_to_80_000_characters_copy_2214d4b))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(onClick = { scope.launch { load() } }, enabled = !loading) { Text(stringResource(R.string.ui_refresh_56e3bad)) }
                TextButton(onClick = { clipboard.setText(AnnotatedString(value)) }, enabled = row != null) { Text(stringResource(R.string.ui_copy_view_eb8c972)) }
                TextButton(onClick = { export.launch("prompt-inspector.json") }, enabled = row != null) { Text(stringResource(R.string.ui_export_json_bc39905)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_close_bbfa773)) }
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

private fun diagnostics(context: Context, record: PromptCapture): String = buildString {
    appendLine(context.getString(R.string.android_inspector_provider, record.provider))
    appendLine(context.getString(R.string.android_inspector_kind, record.kind))
    appendLine(context.getString(R.string.android_inspector_sent, record.sentAt))
    appendLine(context.getString(R.string.android_inspector_status, record.status))
    appendLine(context.getString(R.string.android_inspector_endpoint, record.endpoint ?: "—"))
    appendLine(context.getString(R.string.android_inspector_http_status, record.httpStatus ?: "—"))
    appendLine(context.getString(R.string.android_inspector_duration, record.durationMs ?: "—"))
    appendLine(context.getString(R.string.android_inspector_response_headers, record.responseHeaders ?: emptyMap<String, String>()))
    record.error?.let { appendLine(context.getString(R.string.android_inspector_error, it)) }
}

private fun changedLines(context: Context, before: String, after: String): String {
    if (before == after) return context.getString(R.string.android_inspector_no_changes)
    val left = before.lines()
    val right = after.lines()
    var start = 0
    while (start < left.size && start < right.size && left[start] == right[start]) start++
    return buildString {
        appendLine(context.getString(R.string.android_inspector_first_changed, start + 1))
        left.drop(start).take(500).forEach { appendLine("- $it") }
        right.drop(start).take(500).forEach { appendLine("+ $it") }
        if (left.size - start > 500 || right.size - start > 500) appendLine(context.getString(R.string.android_inspector_diff_limit))
    }
}
