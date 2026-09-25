package com.openmausbot.companion.ui

import com.openmausbot.companion.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.GroupResponder
import com.openmausbot.companion.core.Room
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Edit the same room fields as the desktop, using the paired computer's validation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoomSettingsSheet(
    room: Room,
    bots: List<Bot>,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
) {
    val session = LocalCompanion.current.session
    val connection by session.connection.collectAsState()
    val scope = rememberCoroutineScope()
    var name by remember(room.id) { mutableStateOf(room.name) }
    var bulletin by remember(room.id) { mutableStateOf(room.bulletin) }
    var members by remember(room.id) { mutableStateOf(room.memberIds.toSet()) }
    var responder by remember(room.id) { mutableStateOf(room.defaultResponder) }
    var saving by remember(room.id) { mutableStateOf(false) }
    var confirmDelete by remember(room.id) { mutableStateOf(false) }
    var error by remember(room.id) { mutableStateOf<String?>(null) }
    val available = bots.filter { it.hidden != true || it.id in room.memberIds }
    val orderedMembers = room.memberIds.filter { it in members } +
        available.map { it.id }.filter { it in members && it !in room.memberIds }
    val validResponder = responder.kind != "member" || responder.botId in members
    val changed = name.trim() != room.name || bulletin != room.bulletin ||
        orderedMembers != room.memberIds || responder != room.defaultResponder

    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.ui_group_settings_121d556), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.ui_name_709a232)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = bulletin,
                onValueChange = { bulletin = it },
                label = { Text(stringResource(R.string.ui_group_instructions_36a914e)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.ui_members_1cb449c), style = MaterialTheme.typography.titleMedium)
            available.forEach { bot ->
                Row(
                    modifier = Modifier.fillMaxWidth().toggleable(
                        value = bot.id in members,
                        role = Role.Checkbox,
                        onValueChange = { selected ->
                            members = if (selected) members + bot.id else members - bot.id
                            if (!selected && responder.botId == bot.id) responder = GroupResponder("everyone")
                        },
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = bot.id in members, onCheckedChange = null)
                    Text(bot.name, modifier = Modifier.padding(start = 8.dp))
                }
            }
            Text(stringResource(R.string.ui_who_responds_by_default_56b3401), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.ui_dynamic_lets_the_bots_decide_who_should_re_e189313))
            listOf(
                "everyone" to "Everyone",
                "mentions" to "Only @mentions",
                "dynamic" to "Dynamic",
            ).forEach { (kind, label) ->
                FilterChip(
                    selected = responder.kind == kind,
                    onClick = { responder = GroupResponder(kind) },
                    label = { Text(label) },
                )
            }
            orderedMembers.forEach { id ->
                val bot = available.firstOrNull { it.id == id } ?: return@forEach
                FilterChip(
                    selected = responder.kind == "member" && responder.botId == id,
                    onClick = { responder = GroupResponder("member", id) },
                    label = { Text(stringResource(R.string.ui_dynamic_only_1_s_d47f0d2, bot.name)) },
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.ui_cancel_77dfd21)) }
                TextButton(
                    enabled = !saving && changed && name.trim().isNotEmpty() &&
                        name.trim().length <= 100 && bulletin.length <= 12_000 &&
                        members.isNotEmpty() && validResponder,
                    onClick = {
                        saving = true
                        error = null
                        scope.launch {
                            try {
                                session.updateRoom(
                                    room = room,
                                    name = name.trim().takeIf { it != room.name },
                                    memberIds = orderedMembers.takeIf { it != room.memberIds },
                                    bulletin = bulletin.takeIf { it != room.bulletin },
                                    defaultResponder = responder.takeIf { it != room.defaultResponder },
                                )
                                onDismiss()
                            } catch (failure: Exception) {
                                error = failure.message ?: "Could not save group settings."
                            } finally {
                                saving = false
                            }
                        }
                    },
                ) {
                    if (saving) CircularProgressIndicator() else Text(stringResource(R.string.ui_save_efc007a))
                }
            }
            if (connection?.serverScopes?.contains("admin") == true) {
                TextButton(enabled = !saving, onClick = { confirmDelete = true }) {
                    Text(stringResource(R.string.ui_delete_group_b6f15b2), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { if (!saving) confirmDelete = false },
        title = { Text(stringResource(R.string.ui_dynamic_delete_1_s_cd24016, room.name)) },
        text = { Text(stringResource(R.string.ui_this_permanently_deletes_the_group_convers_1d90d54)) },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                saving = true
                error = null
                scope.launch {
                    try {
                        session.deleteRoom(room.id)
                        confirmDelete = false
                        onDeleted()
                    } catch (failure: Exception) {
                        if (failure is CancellationException) throw failure
                        error = failure.message ?: "Could not delete the group."
                        confirmDelete = false
                    } finally { saving = false }
                }
            }) { Text(stringResource(R.string.ui_delete_group_b6f15b2), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = { confirmDelete = false }) { Text(stringResource(R.string.ui_cancel_77dfd21)) } },
    )
}
