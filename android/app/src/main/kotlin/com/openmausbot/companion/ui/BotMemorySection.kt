package com.openmausbot.companion.ui

import com.openmausbot.companion.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.platform.LocalContext
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
    val l10n = LocalContext.current
    val session = LocalCompanion.current.session
    val context = LocalContext.current
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
            error = failure.message ?: context.getString(R.string.ui_memory_load_failed)
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
            error = failure.message ?: context.getString(R.string.ui_memory_file_load_failed)
        } finally {
            loading = false
        }
    }
    LaunchedEffect(botId, showJournal) {
        if (showJournal) {
            try {
                journal = session.memoryJournal(botId).entries
            } catch (failure: Exception) {
                error = failure.message ?: context.getString(R.string.ui_memory_history_load_failed)
            }
        }
    }

    val topicValid = Regex("^[A-Za-z0-9_][A-Za-z0-9_ .-]{0,199}\\.md$").matches(topicName.trim())
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.ui_memory_lives_on_the_paired_computer_edits_44cf2d0))
        overview?.let { listed ->
            Text(stringResource(R.string.ui_files_6ce6c51), style = MaterialTheme.typography.titleSmall)
            (listOf("MEMORY.md") + listed.topics.map { it.path } + listed.logs.map { it.path }).forEach { candidate ->
                TextButton(onClick = { path = candidate }, enabled = !saving) {
                    Text(if (candidate == path) "✓ $candidate" else candidate)
                }
            }
            Text(stringResource(R.string.ui_dynamic_memory_md_1_s_2_s_bytes_loaded_into_co_6640726, listed.index.loadedBytes, listed.index.maxBytes))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = topicName,
                onValueChange = { topicName = it },
                label = { Text(stringResource(R.string.ui_new_topic_name_md_91647f0)) },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { path = "memory/${topicName.trim()}"; topicName = "" },
                enabled = topicValid && !saving,
            ) { Text(stringResource(R.string.ui_open_cf9b770)) }
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
            Text(stringResource(R.string.ui_dynamic_1_s_bytes_fb518b0, draft.toByteArray(Charsets.UTF_8).size))
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
                                    error = context.getString(R.string.ui_memory_file_changed_before_save)
                                } else error = failure.message ?: context.getString(R.string.ui_memory_save_failed)
                            } finally {
                                saving = false
                            }
                        }
                    },
                ) { Text(stringResource(R.string.ui_save_efc007a)) }
                if (opened.path != "MEMORY.md" && opened.exists) {
                    TextButton(onClick = { deletePending = true }, enabled = !saving) { Text(stringResource(R.string.ui_delete_file_b9ea4b3)) }
                }
            }
        }
        conflict?.let { latest ->
            Text(stringResource(R.string.ui_current_computer_version_546fd4b), style = MaterialTheme.typography.titleSmall)
            Text(latest.text, style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = { doc = latest; draft = latest.text; conflict = null; error = null }) {
                    Text(stringResource(R.string.ui_use_computer_version_a350e20))
                }
                TextButton(onClick = { doc = latest; conflict = null; error = null }) {
                    Text(stringResource(R.string.ui_keep_my_draft_review_and_save_again_41cff23))
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = { showJournal = !showJournal }) {
            Text(if (showJournal) stringResource(R.string.ui_hide_memory_history) else stringResource(R.string.ui_show_memory_history))
        }
        if (showJournal) {
            journal.orEmpty().forEach { entry ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("${entry.path} · ${entry.kind} · ${DateFormat.getDateTimeInstance().format(Date(entry.at.toLong()))}")
                    Text(entry.diff, style = MaterialTheme.typography.bodySmall)
                    if (entry.canRevert) {
                        TextButton(onClick = { revertPending = entry }, enabled = !saving) { Text(stringResource(R.string.ui_revert_272607a)) }
                    } else entry.revertUnavailableReason?.let { Text(it) }
                }
            }
        }
    }

    if (deletePending) AlertDialog(
        onDismissRequest = { deletePending = false },
        title = { Text(stringResource(R.string.ui_delete_memory_file_cf52d4c)) },
        text = { Text(stringResource(R.string.ui_the_change_is_recorded_in_memory_history_10d1ea6)) },
        confirmButton = { TextButton(onClick = {
            deletePending = false
            saving = true
            scope.launch {
                try {
                    val latest = session.memoryDoc(botId, path)
                    if (latest.hash != doc?.hash) {
                        conflict = latest
                        error = context.getString(R.string.ui_memory_file_changed_before_delete)
                    } else {
                        overview = session.deleteMemoryDoc(botId, path).overview
                        path = "MEMORY.md"
                        if (showJournal) journal = session.memoryJournal(botId).entries
                        error = null
                    }
                } catch (failure: Exception) {
                    error = failure.message ?: context.getString(R.string.ui_memory_delete_failed)
                } finally { saving = false }
            }
        }) { Text(stringResource(R.string.ui_delete_f6fdbe4)) } },
        dismissButton = { TextButton(onClick = { deletePending = false }) { Text(stringResource(R.string.ui_cancel_77dfd21)) } },
    )
    revertPending?.let { entry ->
        AlertDialog(
            onDismissRequest = { revertPending = null },
            title = { Text(stringResource(R.string.ui_revert_memory_change_8d64326)) },
            text = { Text(stringResource(R.string.ui_the_paired_computer_checks_whether_this_ch_773ecfc)) },
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
                        error = failure.message ?: l10n.getString(R.string.android_remaining_bot_memory_section_4d5b235a)
                    } finally { saving = false }
                }
            }) { Text(stringResource(R.string.ui_revert_272607a)) } },
            dismissButton = { TextButton(onClick = { revertPending = null }) { Text(stringResource(R.string.ui_cancel_77dfd21)) } },
        )
    }
}
