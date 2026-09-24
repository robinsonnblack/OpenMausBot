package com.openmausbot.companion.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.Instance
import kotlinx.coroutines.launch

/** Keeps unsaved team edits while the bot creation sheet is in front. */
internal class TeamManagementDraft {
    val selectedState = mutableStateOf<String?>(null)
    val nameDraftState = mutableStateOf("")
    val originalIdsState = mutableStateOf<Set<String>>(emptySet())
    val pickedIdsState = mutableStateOf<Set<String>>(emptySet())

    fun includeCreatedBot(id: String) {
        originalIdsState.value = originalIdsState.value + id
        pickedIdsState.value = pickedIdsState.value + id
    }

    fun clear() {
        selectedState.value = null
        nameDraftState.value = ""
        originalIdsState.value = emptySet()
        pickedIdsState.value = emptySet()
    }
}

/** Admin pairing only. Team membership lives on the paired computer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TeamManagementSheet(
    draft: TeamManagementDraft,
    onDismiss: () -> Unit,
    onCreateBot: (String) -> Unit,
) {
    val session = LocalCompanion.current.session
    val state by session.state.collectAsState()
    val scope = rememberCoroutineScope()
    var names by remember { mutableStateOf<List<String>>(emptyList()) }
    var selected by draft.selectedState
    var nameDraft by draft.nameDraftState
    var originalIds by draft.originalIdsState
    var pickedIds by draft.pickedIdsState
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingChief by remember { mutableStateOf<Bot?>(null) }
    var instances by remember { mutableStateOf<List<Instance>>(emptyList()) }

    LaunchedEffect(Unit) {
        try { names = session.teamSections() }
        catch (failure: Exception) { error = failure.message ?: "Could not load teams." }
        try { instances = session.modelInstances() }
        catch (failure: Exception) { error = failure.message ?: "Could not load model capabilities." }
    }

    fun openTeam(name: String) {
        selected = name
        nameDraft = name
        originalIds = state.bots.filter { it.section == name }.map(Bot::id).toSet()
        pickedIds = originalIds
        error = null
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 650.dp)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Manage teams")
            Text("Changes on this phone also change the paired computer.")
            if (selected == null) {
                names.forEach { name ->
                    TextButton(onClick = { openTeam(name) }) { Text(name) }
                }
                if (names.isEmpty() && error == null) Text("No teams yet.")
            } else {
                val team = requireNotNull(selected)
                TextButton(enabled = !busy, onClick = { selected = null; error = null }) { Text("All teams") }
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it.take(60); error = null },
                    label = { Text("Team name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    enabled = !busy && nameDraft.trim().isNotEmpty() && nameDraft.trim() != team,
                    onClick = {
                        scope.launch {
                            busy = true
                            try {
                                session.renameTeam(team, nameDraft.trim())
                                names = session.teamSections()
                                openTeam(nameDraft.trim())
                            } catch (failure: Exception) {
                                error = failure.message ?: "Could not rename the team."
                            } finally { busy = false }
                        }
                    },
                ) { Text("Rename team") }

                Text("Team members")
                TextButton(enabled = !busy, onClick = { onCreateBot(team) }) {
                    Text("Create bot in this team")
                }
                state.bots.filter { it.hidden != true }.forEach { bot ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) {
                            pickedIds = if (bot.id in pickedIds) pickedIds - bot.id else pickedIds + bot.id
                        }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = bot.id in pickedIds, onCheckedChange = null)
                        Text(bot.name)
                        if (bot.section != null && bot.section != team) Text(" · ${bot.section}")
                    }
                }
                TextButton(
                    enabled = !busy && pickedIds != originalIds,
                    onClick = {
                        scope.launch {
                            busy = true
                            try {
                                session.updateTeamMembers(
                                    team,
                                    (pickedIds - originalIds).toList(),
                                    (originalIds - pickedIds).toList(),
                                )
                                names = session.teamSections()
                                openTeam(team)
                            } catch (failure: Exception) {
                                error = failure.message ?: "Could not update team members."
                            } finally { busy = false }
                        }
                    },
                ) { Text("Save members") }
                Text("Chief of Staff")
                val teamBots = state.bots.filter { it.section == team && it.hidden != true }
                val chief = teamBots.firstOrNull { it.chiefOfStaff == true }
                Text(chief?.let { "Current Chief: ${it.name}" } ?: "No Chief appointed")
                teamBots.forEach { bot ->
                    val canCoordinate = instances.firstOrNull {
                        it.instanceId == bot.modelSelection.instanceId
                    }?.capabilities?.agentsMcp == true
                    TextButton(
                        enabled = !busy && (bot.chiefOfStaff == true || canCoordinate),
                        onClick = { pendingChief = bot },
                    ) {
                        Text(if (bot.chiefOfStaff == true) "Remove ${bot.name} as Chief"
                            else "Make ${bot.name} Chief")
                    }
                    if (bot.chiefOfStaff != true && !canCoordinate) {
                        Text("${bot.name}'s provider cannot coordinate bots.")
                    }
                }
                TextButton(enabled = !busy, onClick = { confirmDelete = true }) {
                    Text("Delete team")
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Done") }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete ${selected.orEmpty()}?") },
        text = { Text("The team heading and membership are removed. Bots and conversations are kept.") },
        confirmButton = {
            TextButton(onClick = {
                val team = selected ?: return@TextButton
                confirmDelete = false
                scope.launch {
                    busy = true
                    try {
                        session.deleteTeam(team)
                        names = session.teamSections()
                        selected = null
                    } catch (failure: Exception) {
                        error = failure.message ?: "Could not delete the team."
                    } finally { busy = false }
                }
            }) { Text("Delete team") }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )

    pendingChief?.let { bot ->
        val previous = state.bots.firstOrNull {
            it.section == bot.section && it.chiefOfStaff == true && it.id != bot.id
        }
        val appoint = bot.chiefOfStaff != true
        AlertDialog(
            onDismissRequest = { pendingChief = null },
            title = { Text(if (appoint) "Appoint ${bot.name} as Chief?" else "Remove ${bot.name} as Chief?") },
            text = {
                Text(if (appoint && previous != null)
                    "This hands the team role over from ${previous.name} to ${bot.name}."
                    else if (appoint) "${bot.name} can create and coordinate specialists in this team."
                    else "This team will have no Chief until you appoint another bot.")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingChief = null
                    scope.launch {
                        busy = true
                        try {
                            session.setChiefOfStaff(bot.id, appoint)
                            selected?.let(::openTeam)
                        } catch (failure: Exception) {
                            error = failure.message ?: "Could not change the Chief."
                        } finally { busy = false }
                    }
                }) { Text(if (appoint) "Appoint" else "Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingChief = null }) { Text("Cancel") } },
        )
    }
}
