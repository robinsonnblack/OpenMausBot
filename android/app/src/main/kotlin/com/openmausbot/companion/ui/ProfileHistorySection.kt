package com.openmausbot.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.core.ProfileHistory
import com.openmausbot.companion.core.ProfileHistoryRow
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

/** Server-authored profile history; full standing-instruction bodies stay on the computer. */
@Composable
internal fun ProfileHistorySection(botId: String) {
    val session = LocalCompanion.current.session
    val scope = rememberCoroutineScope()
    var history by remember(botId) { mutableStateOf<ProfileHistory?>(null) }
    var loading by remember(botId) { mutableStateOf(true) }
    var error by remember(botId) { mutableStateOf<String?>(null) }
    var revision by remember(botId) { mutableIntStateOf(0) }
    var pendingUndo by remember(botId) { mutableStateOf<ProfileHistoryRow?>(null) }
    var undoing by remember(botId) { mutableStateOf(false) }

    LaunchedEffect(botId, revision) {
        loading = true
        try {
            history = session.profileHistory(botId)
            error = null
        } catch (failure: Exception) {
            error = failure.message ?: "Could not load history."
        } finally {
            loading = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (loading) CircularProgressIndicator()
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val rows = history?.rows.orEmpty()
        if (!loading && history != null && rows.isEmpty()) Text("No changes recorded yet.")
        rows.forEach { row ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    DateFormat.getDateTimeInstance().format(Date(row.at.toLong())) +
                        " · ${row.actor} via ${row.via}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(row.summary, style = MaterialTheme.typography.bodyMedium)
                if (row.field == "soul") {
                    if (row.canRestore == true) {
                        TextButton(onClick = { pendingUndo = row }, enabled = !undoing) {
                            Text("Undo this instruction change")
                        }
                    } else {
                        Text(row.restoreUnavailableReason ?: "Exact previous instructions unavailable.")
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { revision++ }, enabled = !loading && !undoing) { Text("Refresh") }
        }
    }

    pendingUndo?.let { row ->
        AlertDialog(
            onDismissRequest = { if (!undoing) pendingUndo = null },
            title = { Text("Undo instruction change?") },
            text = { Text("This restores the previous standing instructions. The current text will be kept in history.") },
            confirmButton = {
                TextButton(
                    enabled = !undoing,
                    onClick = {
                        val expected = history?.revision ?: return@TextButton
                        undoing = true
                        scope.launch {
                            try {
                                session.undoStandingInstructionChange(botId, row.id, expected)
                                pendingUndo = null
                                revision++
                            } catch (failure: Exception) {
                                error = failure.message ?: "Could not undo the change. Refresh history and try again."
                                pendingUndo = null
                            } finally {
                                undoing = false
                            }
                        }
                    },
                ) { Text("Undo") }
            },
            dismissButton = { TextButton(onClick = { pendingUndo = null }) { Text("Cancel") } },
        )
    }
}
