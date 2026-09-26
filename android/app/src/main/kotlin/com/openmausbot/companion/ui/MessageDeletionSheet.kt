package com.openmausbot.companion.ui

import androidx.compose.ui.platform.LocalContext
import com.openmausbot.companion.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.clickable
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.text.style.TextOverflow
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
    val l10n = LocalContext.current
    val session = LocalCompanion.current.session
    val scope = rememberCoroutineScope()
    var selection by remember(threadId) { mutableStateOf<MessageDeletionSelection?>(null) }
    var selected by remember(threadId) { mutableStateOf(emptySet<String>()) }
    var error by remember(threadId) { mutableStateOf<String?>(null) }
    var deleting by remember(threadId) { mutableStateOf(false) }
    var confirm by remember(threadId) { mutableStateOf(false) }

    LaunchedEffect(threadId) {
        try { selection = session.messageDeletionSelection(threadId) }
        catch (failure: Exception) { error = failure.message ?: l10n.getString(R.string.android_remaining_message_deletion_sheet_f00aec8c) }
    }
    val labels = remember(messages) { messages.associateBy(Message::id) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { if (!deleting) onDismiss() }, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.ui_delete_messages_93f3675), style = MaterialTheme.typography.titleLarge)
            PermissionHelp(stringResource(R.string.ui_select_messages_to_permanently_remove_olde_000f52e))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = selection != null && !deleting, onClick = {
                    selected = selection?.allIds.orEmpty().toSet()
                }) { Text(stringResource(R.string.ui_select_all_913afff)) }
                TextButton(enabled = selected.isNotEmpty() && !deleting, onClick = { selected = emptySet() }) {
                    Text(stringResource(R.string.ui_clear_719ea39))
                }
                Text(stringResource(R.string.ui_dynamic_1_s_selected_51753f0, selected.size), modifier = Modifier.padding(top = 12.dp))
            }
            if (selection == null && error == null) CircularProgressIndicator()
            if (selection?.allIds?.isEmpty() == true) Text(stringResource(R.string.ui_no_messages_in_this_conversation_089151e))
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(selection?.allIds.orEmpty().asReversed(), key = { it }) { id ->
                    val message = labels[id]
                    val label = if (message == null) l10n.getString(R.string.android_deletion_older_version, id) else {
                        val sender = l10n.getString(if (message.role == Message.Role.USER) R.string.android_deletion_you else R.string.android_deletion_bot)
                        "$sender: ${message.text?.take(100) ?: message.kind.name.lowercase()}"
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Checkbox(
                            checked = id in selected,
                            enabled = !deleting,
                            onCheckedChange = { checked -> selected = if (checked) selected + id else selected - id },
                        )
                        Text(label, modifier = Modifier.weight(1f).padding(top = 12.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = !deleting, onClick = onDismiss) { Text(stringResource(R.string.ui_done_e9b450d)) }
                TextButton(enabled = selected.isNotEmpty() && !deleting, onClick = { confirm = true }) {
                    Text(if (deleting) stringResource(R.string.ui_deleting)
                        else stringResource(R.string.ui_delete_count, selected.size),
                        color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(stringResource(R.string.ui_dynamic_delete_1_s_messages_3a611c8, selected.size)) },
        text = { Text(stringResource(R.string.ui_this_permanently_deletes_the_selected_mess_0d8a658)) },
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
                    error = failure.message ?: l10n.getString(R.string.android_remaining_message_deletion_sheet_66ac29ab)
                } finally { deleting = false }
            }
        }) { Text(stringResource(R.string.ui_delete_messages_93f3675), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.ui_cancel_77dfd21)) } },
    )
}
