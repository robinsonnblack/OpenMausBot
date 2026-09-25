package com.openmausbot.companion.ui

import com.openmausbot.companion.R
import androidx.compose.ui.res.stringResource
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
    val loadTeamsError = stringResource(R.string.android_team_load_error)
    val loadModelsError = stringResource(R.string.android_team_load_models_error)
    val updateAccessError = stringResource(R.string.android_team_update_access_error)
    val renameError = stringResource(R.string.android_team_rename_error)
    val updateMembersError = stringResource(R.string.android_team_update_members_error)
    val deleteError = stringResource(R.string.android_team_delete_error)
    val changeChiefError = stringResource(R.string.android_team_change_chief_error)
    var names by remember { mutableStateOf<List<String>>(emptyList()) }
    var selected by draft.selectedState
    var nameDraft by draft.nameDraftState
    var originalIds by draft.originalIdsState
    var pickedIds by draft.pickedIdsState
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingChief by remember { mutableStateOf<Bot?>(null) }
    var pendingManagedGrant by remember { mutableStateOf<Pair<Bot, List<String>>?>(null) }
    var instances by remember { mutableStateOf<List<Instance>>(emptyList()) }

    LaunchedEffect(Unit) {
        try { names = session.teamSections() }
        catch (failure: Exception) { error = failure.message ?: loadTeamsError }
        try { instances = session.modelInstances() }
        catch (failure: Exception) { error = failure.message ?: loadModelsError }
    }

    fun openTeam(name: String) {
        selected = name
        nameDraft = name
        originalIds = state.bots.filter { it.section == name }.map(Bot::id).toSet()
        pickedIds = originalIds
        error = null
    }

    fun saveManagedTeams(bot: Bot, selectedTeams: List<String>, confirmed: Boolean) {
        scope.launch {
            busy = true
            try {
                session.setChiefManagedTeams(bot.id, selectedTeams, confirmed)
                selected?.let(::openTeam)
            } catch (failure: Exception) {
                error = failure.message ?: updateAccessError
            } finally { busy = false }
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 650.dp)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.ui_manage_teams_c99bf82))
            Text(stringResource(R.string.ui_changes_on_this_phone_also_change_the_pair_9f8fbb1))
            if (selected == null) {
                names.forEach { name ->
                    TextButton(onClick = { openTeam(name) }) { Text(name) }
                }
                if (names.isEmpty() && error == null) Text(stringResource(R.string.ui_no_teams_yet_9210826))
            } else {
                val team = requireNotNull(selected)
                TextButton(enabled = !busy, onClick = { selected = null; error = null }) { Text(stringResource(R.string.ui_all_teams_e75f540)) }
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it.take(60); error = null },
                    label = { Text(stringResource(R.string.ui_team_name_9b11ed1)) },
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
                                error = failure.message ?: renameError
                            } finally { busy = false }
                        }
                    },
                ) { Text(stringResource(R.string.ui_rename_team_bbd28e3)) }

                Text(stringResource(R.string.ui_team_members_8bd76ae))
                TextButton(enabled = !busy, onClick = { onCreateBot(team) }) {
                    Text(stringResource(R.string.ui_create_bot_in_this_team_f2e808c))
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
                                error = failure.message ?: updateMembersError
                            } finally { busy = false }
                        }
                    },
                ) { Text(stringResource(R.string.ui_save_members_b5c33de)) }
                Text(stringResource(R.string.ui_chief_of_staff_ab970be))
                val teamBots = state.bots.filter { it.section == team && it.hidden != true }
                val chief = teamBots.firstOrNull { it.chiefOfStaff == true }
                Text(chief?.let { stringResource(R.string.android_team_current_chief, it.name) }
                    ?: stringResource(R.string.android_team_no_chief))
                teamBots.forEach { bot ->
                    val canCoordinate = instances.firstOrNull {
                        it.instanceId == bot.modelSelection.instanceId
                    }?.capabilities?.agentsMcp == true
                    TextButton(
                        enabled = !busy && (bot.chiefOfStaff == true || canCoordinate),
                        onClick = { pendingChief = bot },
                    ) {
                        Text(stringResource(if (bot.chiefOfStaff == true) R.string.ui_remove_named_chief
                            else R.string.ui_make_named_chief, bot.name))
                    }
                    if (bot.chiefOfStaff != true && !canCoordinate) {
                        Text(stringResource(R.string.ui_dynamic_1_s_s_provider_cannot_coordinate_bots_630b82d, bot.name))
                    }
                }
                if (chief != null) {
                    val original = chief.managedSections.orEmpty().toSet()
                    var selectedExtra by remember(chief.id, chief.managedSections) {
                        mutableStateOf(original)
                    }
                    val choices = (listOf("") + names).filter { it != team }.distinct().sorted()
                    Text(stringResource(R.string.ui_dynamic_additional_teams_for_1_s_78587fb, chief.name))
                    Text(stringResource(R.string.ui_the_chief_can_coordinate_bots_and_propose_e6d0193))
                    if (choices.isEmpty()) Text(stringResource(R.string.ui_create_another_team_to_extend_the_chief_s_36ad2a6))
                    choices.forEach { other ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) {
                                selectedExtra = if (other in selectedExtra) selectedExtra - other else selectedExtra + other
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = other in selectedExtra, onCheckedChange = null)
                            Text(other.ifEmpty { stringResource(R.string.android_team_general) })
                        }
                    }
                    val selectedNames = choices.filter { it in selectedExtra }
                    TextButton(
                        enabled = !busy && selectedNames.toSet() != original,
                        onClick = {
                            if (selectedNames.any { it !in original }) {
                                pendingManagedGrant = chief to selectedNames
                            } else saveManagedTeams(chief, selectedNames, false)
                        },
                    ) { Text(stringResource(R.string.ui_save_chief_team_access_9807639)) }
                }
                TextButton(enabled = !busy, onClick = { confirmDelete = true }) {
                    Text(stringResource(R.string.ui_delete_team_a9661e7))
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.ui_done_e9b450d)) }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.ui_dynamic_delete_1_s_cd24016, selected.orEmpty())) },
        text = { Text(stringResource(R.string.ui_the_team_heading_and_membership_are_remove_ef3cdbe)) },
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
                        error = failure.message ?: deleteError
                    } finally { busy = false }
                }
            }) { Text(stringResource(R.string.ui_delete_team_a9661e7)) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.ui_cancel_77dfd21)) } },
    )

    pendingManagedGrant?.let { (bot, extraTeams) ->
        val generalTeamName = stringResource(R.string.ui_general_team_name)
        AlertDialog(
            onDismissRequest = { pendingManagedGrant = null },
            title = { Text(stringResource(R.string.ui_dynamic_give_1_s_access_to_more_teams_745ba86, bot.name)) },
            text = { Text(stringResource(
                R.string.ui_chief_extra_teams_explanation,
                extraTeams.joinToString { it.ifEmpty { generalTeamName } },
            )) },
            confirmButton = {
                TextButton(onClick = {
                    pendingManagedGrant = null
                    saveManagedTeams(bot, extraTeams, true)
                }) { Text(stringResource(R.string.ui_grant_team_access_03bbb07)) }
            },
            dismissButton = { TextButton(onClick = { pendingManagedGrant = null }) { Text(stringResource(R.string.ui_cancel_77dfd21)) } },
        )
    }

    pendingChief?.let { bot ->
        val previous = state.bots.firstOrNull {
            it.section == bot.section && it.chiefOfStaff == true && it.id != bot.id
        }
        val appoint = bot.chiefOfStaff != true
        AlertDialog(
            onDismissRequest = { pendingChief = null },
            title = { Text(stringResource(if (appoint) R.string.ui_appoint_named_chief_confirm
                else R.string.ui_remove_named_chief_confirm, bot.name)) },
            text = {
                Text(if (appoint && previous != null)
                    stringResource(R.string.ui_chief_role_handover, previous.name, bot.name)
                    else if (appoint) stringResource(R.string.ui_chief_can_coordinate, bot.name)
                    else stringResource(R.string.ui_team_no_chief))
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
                            error = failure.message ?: changeChiefError
                        } finally { busy = false }
                    }
                }) { Text(stringResource(if (appoint) R.string.ui_appoint_action else R.string.ui_remove_action)) }
            },
            dismissButton = { TextButton(onClick = { pendingChief = null }) { Text(stringResource(R.string.ui_cancel_77dfd21)) } },
        )
    }
}
