package com.openmausbot.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.openmausbot.companion.core.Message
import com.openmausbot.companion.core.MessageDeletionSelection
import kotlinx.coroutines.launch

/** Only the workspace owner's admin-scoped pairing may use the server's deletion API. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun MessageDeletionSheet(
    threadId: String,
    messages: List<Message>,
    onDismiss: () -> Unit,
) {
    val session = LocalCompanion.current.session
    val scope = rememberCoroutineScope()
    var selection by remember(threadId) { mutableStateOf<MessageDeletionSelection?>(null) }
    var selected by remember(threadId) { mutableStateOf(emptySet<String>()) }
    var error by remember(threadId) { mutableStateOf<String?>(null) }
    var deleting by remember(threadId) { mutableStateOf(false) }
    var confirm by remember(threadId) { mutableStateOf(false) }

    LaunchedEffect(threadId) {
        try { selection = session.messageDeletionSelection(threadId) }
        catch (failure: Exception) { error = failure.message ?: "Could not load messages." }
    }
    val labels = remember(messages) { messages.associateBy(Message::id) }

    ModalBottomSheet(onDismissRequest = { if (!deleting) onDismiss() }) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Delete messages", style = MaterialTheme.typography.titleLarge)
            Text("Select messages to permanently remove. Older edited and retried versions are included.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = selection != null && !deleting, onClick = {
                    selected = selection?.allIds.orEmpty().toSet()
                }) { Text("Select all") }
                TextButton(enabled = selected.isNotEmpty() && !deleting, onClick = { selected = emptySet() }) {
                    Text("Clear")
                }
                Text("${selected.size} selected", modifier = Modifier.padding(top = 12.dp))
            }
            if (selection == null && error == null) CircularProgressIndicator()
            if (selection?.allIds?.isEmpty() == true) Text("No messages in this conversation.")
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                items(selection?.allIds.orEmpty(), key = { it }) { id ->
                    val message = labels[id]
                    val label = if (message == null) "Older version · $id" else {
                        "${if (message.role == Message.Role.USER) "You" else "Bot"}: ${message.text?.take(100) ?: message.kind.name.lowercase()}"
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Checkbox(
                            checked = id in selected,
                            enabled = !deleting,
                            onCheckedChange = { checked -> selected = if (checked) selected + id else selected - id },
                        )
                        Text(label, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = !deleting, onClick = onDismiss) { Text("Done") }
                TextButton(enabled = selected.isNotEmpty() && !deleting, onClick = { confirm = true }) {
                    Text(if (deleting) "Deleting…" else "Delete ${selected.size}", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Delete ${selected.size} messages?") },
        text = { Text("This permanently deletes the selected messages and cannot be undone.") },
        confirmButton = { TextButton(onClick = {
            confirm = false
            deleting = true
            scope.launch {
                try {
                    val result = session.deleteMessages(threadId, selected.toList())
                    val gone = result.ids.toSet()
                    selection = selection?.let { current ->
                        current.copy(ids = current.ids.filterNot { it in gone }, allIds = current.allIds.filterNot { it in gone })
                    }
                    selected = emptySet()
                    error = null
                } catch (failure: Exception) {
                    error = failure.message ?: "Could not delete messages."
                } finally { deleting = false }
            }
        }) { Text("Delete messages", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
}
