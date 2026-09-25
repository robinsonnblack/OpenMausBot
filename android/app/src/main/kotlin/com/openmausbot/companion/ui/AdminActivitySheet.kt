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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.core.AdminActivityEntry
import com.openmausbot.companion.core.AdminActivityFilter
import com.openmausbot.companion.core.AdminActivityPage
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement

private val activityCategories = listOf(
    "all", "approvals", "decisions", "config", "people", "session", "webhook",
    "mcp", "engine", "bot", "budget", "visibility",
)

/** Date rules mirror the server: ISO dates, ordered, and a range no longer than a year. */
internal fun validActivityDates(from: String, to: String): Boolean {
    if (from.isBlank() && to.isBlank()) return true
    return try {
        val start = if (from.isBlank()) LocalDate.now().minusDays(29) else LocalDate.parse(from)
        val end = if (to.isBlank()) LocalDate.now() else LocalDate.parse(to)
        !start.isAfter(end) && ChronoUnit.DAYS.between(start, end) <= 366
    } catch (_: DateTimeParseException) { false }
}

/** Read-only view of the paired computer's admin audit and approval history. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdminActivitySheet(onDismiss: () -> Unit) {
    val session = LocalCompanion.current.session
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var who by remember { mutableStateOf("") }
    var what by remember { mutableStateOf("all") }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var page by remember { mutableStateOf<AdminActivityPage?>(null) }
    var appliedFilter by remember { mutableStateOf<AdminActivityFilter?>(null) }
    var loading by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingCsv by remember { mutableStateOf<ByteArray?>(null) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val data = pendingCsv
        pendingCsv = null
        if (uri != null && data != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(data) }
                        ?: kotlin.error("Could not open the selected file.")
                }
            } catch (failure: Exception) { error = failure.message ?: "Could not save activity export." }
        }
    }

    fun filter() = AdminActivityFilter(who, what, from, to)
    fun load() {
        if (!validActivityDates(from, to)) {
            error = "Use YYYY-MM-DD dates in order, no more than one year apart."
            return
        }
        loading = true
        error = null
        page = null
        appliedFilter = null
        val requested = filter()
        scope.launch {
            try {
                page = session.adminActivity(requested)
                appliedFilter = requested
            }
            catch (failure: Exception) { error = failure.message ?: "Could not load activity." }
            finally { loading = false }
        }
    }

    LaunchedEffect(Unit) { load() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Activity", style = MaterialTheme.typography.titleLarge)
            Text("Changes and approvals on the paired computer. Admin pairing required.",
                style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(who, onValueChange = { who = it.take(200) }, label = { Text("Who") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                activityCategories.forEach { category ->
                    FilterChip(selected = what == category, onClick = { what = category }, label = { Text(category) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(from, onValueChange = { from = it.take(10) }, label = { Text("From YYYY-MM-DD") },
                    modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(to, onValueChange = { to = it.take(10) }, label = { Text("To YYYY-MM-DD") },
                    modifier = Modifier.weight(1f), singleLine = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = !loading && !exporting, onClick = ::load) { Text("Apply filters") }
                TextButton(enabled = !loading && !exporting && page != null && appliedFilter == filter(), onClick = {
                    exporting = true
                    error = null
                    scope.launch {
                        try {
                            pendingCsv = session.adminActivityCsv(requireNotNull(appliedFilter))
                            export.launch("activity-${LocalDate.now()}.csv")
                        } catch (failure: Exception) {
                            error = failure.message ?: "Could not export activity."
                        } finally { exporting = false }
                    }
                }) { Text(if (exporting) "Exporting…" else "Export CSV") }
            }
            if (loading) CircularProgressIndicator()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            page?.let { result ->
                Text("${result.total} entries · retained ${result.retentionDays} days",
                    style = MaterialTheme.typography.bodySmall)
                if (!result.recording) Text("Admin changes are not being recorded for this one-person workspace. Approval history may still appear.",
                    style = MaterialTheme.typography.bodySmall)
                if (result.total > result.entries.size) Text("Showing the first ${result.entries.size} entries. Export CSV for the full range.",
                    style = MaterialTheme.typography.bodySmall)
                if (result.entries.isEmpty()) Text("No activity in this range.")
                result.entries.forEach { entry ->
                    HorizontalDivider()
                    ActivityEntryRow(entry)
                }
            }
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    }
}

@Composable
private fun ActivityEntryRow(entry: AdminActivityEntry) {
    var expanded by remember(entry) { mutableStateOf(false) }
    val description = if (entry.type == "approval") {
        listOfNotNull(entry.what, entry.tool, entry.bot).joinToString(" · ")
    } else {
        listOfNotNull(entry.action ?: entry.what, entry.target?.name ?: entry.target?.id).joinToString(" · ")
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(entry.at.replace('T', ' ').take(19), style = MaterialTheme.typography.labelSmall)
        Text(entry.who.ifBlank { "Unknown actor" }, style = MaterialTheme.typography.titleSmall)
        Text(description, style = MaterialTheme.typography.bodyMedium)
        entry.summary?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (entry.changed.isNotEmpty() || entry.before != null || entry.after != null) {
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide changes" else "Show changes") }
            if (expanded) {
                val keys = entry.changed.ifEmpty { (entry.before?.keys.orEmpty() + entry.after?.keys.orEmpty()).distinct() }
                keys.forEach { key ->
                    Text(key, style = MaterialTheme.typography.labelMedium)
                    Text("Before: ${activityValue(entry.before?.get(key))}", style = MaterialTheme.typography.bodySmall)
                    Text("After: ${activityValue(entry.after?.get(key))}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun activityValue(value: JsonElement?): String = value?.toString()?.take(160) ?: "—"
