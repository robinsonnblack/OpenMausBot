package com.openmausbot.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.core.MemoryDoc
import com.openmausbot.companion.core.MemoryJournalRow
import com.openmausbot.companion.core.MemoryOverview
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

/** Files remain on the paired computer; every save checks the hash that was read. */
@Composable
internal fun BotMemorySection(botId: String) {
    val session = LocalCompanion.current.session
    val scope = rememberCoroutineScope()
    var overview by remember(botId) { mutableStateOf<MemoryOverview?>(null) }
    var path by remember(botId) { mutableStateOf("MEMORY.md") }
    var doc by remember(botId) { mutableStateOf<MemoryDoc?>(null) }
    var draft by remember(botId) { mutableStateOf("") }
    var conflict by remember(botId) { mutableStateOf<MemoryDoc?>(null) }
    var error by remember(botId) { mutableStateOf<String?>(null) }
    var loading by remember(botId) { mutableStateOf(true) }
    var saving by remember(botId) { mutableStateOf(false) }
    var topicName by remember(botId) { mutableStateOf("") }
    var journal by remember(botId) { mutableStateOf<List<MemoryJournalRow>?>(null) }
    var showJournal by remember(botId) { mutableStateOf(false) }
    var deletePending by remember(botId) { mutableStateOf(false) }
    var revertPending by remember(botId) { mutableStateOf<MemoryJournalRow?>(null) }

    LaunchedEffect(botId) {
        try {
            overview = session.memoryOverview(botId)
        } catch (failure: Exception) {
            error = failure.message ?: "Could not load memory."
        }
    }
    LaunchedEffect(botId, path) {
        loading = true
        conflict = null
        try {
            val fresh = session.memoryDoc(botId, path)
            doc = fresh
            draft = fresh.text
            error = null
        } catch (failure: Exception) {
            doc = null
            error = failure.message ?: "Could not load memory file."
        } finally {
            loading = false
        }
    }
    LaunchedEffect(botId, showJournal) {
        if (showJournal) {
            try {
                journal = session.memoryJournal(botId).entries
            } catch (failure: Exception) {
                error = failure.message ?: "Could not load memory history."
            }
        }
    }

    val topicValid = Regex("^[A-Za-z0-9_][A-Za-z0-9_ .-]{0,199}\\.md$").matches(topicName.trim())
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Memory lives on the paired computer. Edits here are saved to its Markdown files.")
        overview?.let { listed ->
            Text("Files", style = MaterialTheme.typography.titleSmall)
            (listOf("MEMORY.md") + listed.topics.map { it.path } + listed.logs.map { it.path }).forEach { candidate ->
                TextButton(onClick = { path = candidate }, enabled = !saving) {
                    Text(if (candidate == path) "✓ $candidate" else candidate)
                }
            }
            Text("MEMORY.md: ${listed.index.loadedBytes} / ${listed.index.maxBytes} bytes loaded into context")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = topicName,
                onValueChange = { topicName = it },
                label = { Text("New topic (name.md)") },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { path = "memory/${topicName.trim()}"; topicName = "" },
                enabled = topicValid && !saving,
            ) { Text("Open") }
        }
        if (loading) CircularProgressIndicator()
        doc?.let { opened ->
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text(opened.path) },
                minLines = 8,
                maxLines = 18,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("${draft.toByteArray(Charsets.UTF_8).size} bytes")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = !saving && conflict == null && draft != opened.text,
                    onClick = {
                        saving = true
                        scope.launch {
                            try {
                                val saved = session.saveMemoryDoc(botId, path, draft, opened.hash)
                                doc = saved.doc()
                                draft = saved.text
                                overview = saved.overview
                                error = null
                                if (showJournal) journal = session.memoryJournal(botId).entries
                            } catch (failure: Exception) {
                                val latest = runCatching { session.memoryDoc(botId, path) }.getOrNull()
                                if (latest != null && latest.hash != opened.hash) {
                                    conflict = latest
                                    error = "This file changed on the computer. Review both versions before saving."
                                } else error = failure.message ?: "Could not save memory."
                            } finally {
                                saving = false
                            }
                        }
                    },
                ) { Text("Save") }
                if (opened.path != "MEMORY.md" && opened.exists) {
                    TextButton(onClick = { deletePending = true }, enabled = !saving) { Text("Delete file") }
                }
            }
        }
        conflict?.let { latest ->
            Text("Current computer version:", style = MaterialTheme.typography.titleSmall)
            Text(latest.text, style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = { doc = latest; draft = latest.text; conflict = null; error = null }) {
                    Text("Use computer version")
                }
                TextButton(onClick = { doc = latest; conflict = null; error = null }) {
                    Text("Keep my draft; review and save again")
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = { showJournal = !showJournal }) {
            Text(if (showJournal) "Hide memory history" else "Show memory history")
        }
        if (showJournal) {
            journal.orEmpty().forEach { entry ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("${entry.path} · ${entry.kind} · ${DateFormat.getDateTimeInstance().format(Date(entry.at.toLong()))}")
                    Text(entry.diff, style = MaterialTheme.typography.bodySmall)
                    if (entry.canRevert) {
                        TextButton(onClick = { revertPending = entry }, enabled = !saving) { Text("Revert") }
                    } else entry.revertUnavailableReason?.let { Text(it) }
                }
            }
        }
    }

    if (deletePending) AlertDialog(
        onDismissRequest = { deletePending = false },
        title = { Text("Delete memory file?") },
        text = { Text("The change is recorded in memory history.") },
        confirmButton = { TextButton(onClick = {
            deletePending = false
            saving = true
            scope.launch {
                try {
                    val latest = session.memoryDoc(botId, path)
                    if (latest.hash != doc?.hash) {
                        conflict = latest
                        error = "This file changed on the computer. Review both versions before deleting it."
                    } else {
                        overview = session.deleteMemoryDoc(botId, path).overview
                        path = "MEMORY.md"
                        if (showJournal) journal = session.memoryJournal(botId).entries
                        error = null
                    }
                } catch (failure: Exception) {
                    error = failure.message ?: "Could not delete memory file."
                } finally { saving = false }
            }
        }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deletePending = false }) { Text("Cancel") } },
    )
    revertPending?.let { entry ->
        AlertDialog(
            onDismissRequest = { revertPending = null },
            title = { Text("Revert memory change?") },
            text = { Text("The paired computer checks whether this change can still be safely reverted.") },
            confirmButton = { TextButton(onClick = {
                revertPending = null
                saving = true
                scope.launch {
                    try {
                        val reverted = session.revertMemoryChange(botId, entry.id)
                        overview = reverted.overview
                        journal = session.memoryJournal(botId).entries
                        if (path == reverted.path) { doc = reverted.doc(); draft = reverted.text }
                        error = null
                    } catch (failure: Exception) {
                        error = failure.message ?: "Could not revert this change."
                    } finally { saving = false }
                }
            }) { Text("Revert") } },
            dismissButton = { TextButton(onClick = { revertPending = null }) { Text("Cancel") } },
        )
    }
}
