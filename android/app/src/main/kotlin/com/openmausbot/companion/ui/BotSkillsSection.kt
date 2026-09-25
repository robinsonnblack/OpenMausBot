package com.openmausbot.companion.ui

import com.openmausbot.companion.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import com.openmausbot.companion.core.ManagedSkill
import com.openmausbot.companion.core.OfferedOrganizationSkill
import kotlinx.coroutines.launch

/** Manage skills on the paired computer. A disabled skill must be read before enabling. */
@Composable
internal fun BotSkillsSection(botId: String) {
    val session = LocalCompanion.current.session
    val scope = rememberCoroutineScope()
    var skills by remember(botId) { mutableStateOf<List<ManagedSkill>>(emptyList()) }
    var stagedCount by remember(botId) { mutableStateOf(0) }
    var loading by remember(botId) { mutableStateOf(true) }
    var busy by remember(botId) { mutableStateOf(false) }
    var error by remember(botId) { mutableStateOf<String?>(null) }
    var source by remember(botId) { mutableStateOf("") }
    var preview by remember(botId) { mutableStateOf<Pair<ManagedSkill, String>?>(null) }
    var removePending by remember(botId) { mutableStateOf<ManagedSkill?>(null) }
    var organization by remember(botId) { mutableStateOf<String?>(null) }
    var offered by remember(botId) { mutableStateOf<List<OfferedOrganizationSkill>>(emptyList()) }
    var addPending by remember(botId) { mutableStateOf<OfferedOrganizationSkill?>(null) }

    suspend fun refresh() {
        val result = session.managedSkills(botId)
        skills = result.skills
        stagedCount = result.staged.size
        error = null
    }

    suspend fun refreshOrganization() {
        val catalog = session.offeredOrganizationSkills(botId)
        organization = catalog.organization?.name
        offered = catalog.skills
    }

    LaunchedEffect(botId) {
        loading = true
        try { refresh() } catch (failure: Exception) {
            error = failure.message ?: "Could not load skills."
        } finally { loading = false }
        runCatching { refreshOrganization() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.ui_skills_are_stored_on_the_paired_computer_a_39a0950))
        if (loading) CircularProgressIndicator()
        skills.forEach { skill ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(skill.name, style = MaterialTheme.typography.titleSmall)
                Text(skill.description)
                Text(stringResource(
                    R.string.ui_skill_status_and_source,
                    stringResource(if (skill.enabled) R.string.ui_status_enabled else R.string.ui_status_disabled),
                    skill.source,
                ))
                skill.warnings.forEach { warning ->
                    Text(warning, color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            try {
                                val text = session.managedSkillText(botId, skill.name)
                                if (text.isBlank()) throw IllegalStateException("Skill contents are unavailable.")
                                preview = skill to text
                                error = null
                            } catch (failure: Exception) {
                                error = failure.message ?: "Could not read skill."
                            } finally { busy = false }
                        }
                    }) { Text(stringResource(if (skill.enabled) R.string.ui_read_skill else R.string.ui_review_enable_skill)) }
                    if (skill.enabled) TextButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            try {
                                session.setManagedSkillEnabled(botId, skill.name, false)
                                refresh()
                            } catch (failure: Exception) {
                                error = failure.message ?: "Could not disable skill."
                            } finally { busy = false }
                        }
                    }) { Text(stringResource(R.string.ui_disable_9a7d4e0)) }
                    TextButton(enabled = !busy, onClick = { removePending = skill }) { Text(stringResource(R.string.ui_remove_e963907)) }
                }
            }
        }
        if (!loading && skills.isEmpty()) Text(stringResource(R.string.ui_no_skills_installed_for_this_bot_0133dd1))
        if (stagedCount > 0) Text(stringResource(R.string.ui_dynamic_1_s_skill_proposal_s_await_a_decision_299180c, stagedCount))
        OutlinedTextField(
            value = source,
            onValueChange = { source = it },
            label = { Text(stringResource(R.string.ui_import_from_github_url_or_owner_repo_95f0cfc)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
        )
        Text(stringResource(R.string.ui_imports_start_disabled_review_the_full_ski_3d578a1))
        TextButton(enabled = !busy && source.isNotBlank(), onClick = {
            busy = true
            scope.launch {
                try {
                    session.importManagedSkills(botId, source.trim())
                    source = ""
                    refresh()
                } catch (failure: Exception) {
                    error = failure.message ?: "Could not import skills."
                } finally { busy = false }
            }
        }) { Text(stringResource(R.string.ui_import_d6fbc9d)) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (organization != null && offered.isNotEmpty()) {
            Text(stringResource(R.string.ui_dynamic_from_1_s_1616793, organization), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.ui_these_skills_were_published_by_your_organi_0205790))
            offered.forEach { skill ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(skill.name, style = MaterialTheme.typography.titleSmall)
                    Text(skill.description)
                    Text("${skill.packageName} · ${skill.release} · ${skill.publisher}")
                    if (skill.added) Text(stringResource(R.string.ui_added_b68734c))
                    else TextButton(enabled = !busy, onClick = { addPending = skill }) { Text(stringResource(R.string.ui_add_to_bot_ab80336)) }
                }
            }
        }
    }

    preview?.let { (skill, text) ->
        AlertDialog(
            onDismissRequest = { if (!busy) preview = null },
            title = { Text(skill.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.ui_read_the_complete_skill_before_enabling_it_5c43aed))
                    Text(text, modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()))
                }
            },
            confirmButton = {
                if (!skill.enabled) TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            session.setManagedSkillEnabled(botId, skill.name, true)
                            preview = null
                            refresh()
                        } catch (failure: Exception) {
                            error = failure.message ?: "Could not enable skill."
                        } finally { busy = false }
                    }
                }) { Text(stringResource(R.string.ui_enable_this_skill_399b34a)) }
            },
            dismissButton = { TextButton(onClick = { preview = null }, enabled = !busy) { Text(stringResource(R.string.ui_close_bbfa773)) } },
        )
    }
    removePending?.let { skill ->
        AlertDialog(
            onDismissRequest = { removePending = null },
            title = { Text(stringResource(R.string.ui_dynamic_remove_1_s_c8e14e6, skill.name)) },
            text = { Text(stringResource(R.string.ui_this_removes_the_skill_from_the_paired_com_a6b822f)) },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                removePending = null
                busy = true
                scope.launch {
                    try {
                        session.removeManagedSkill(botId, skill.name)
                        refresh()
                    } catch (failure: Exception) {
                        error = failure.message ?: "Could not remove skill."
                    } finally { busy = false }
                }
            }) { Text(stringResource(R.string.ui_remove_e963907)) } },
            dismissButton = { TextButton(onClick = { removePending = null }) { Text(stringResource(R.string.ui_cancel_77dfd21)) } },
        )
    }
    addPending?.let { skill ->
        AlertDialog(
            onDismissRequest = { addPending = null },
            title = { Text(stringResource(R.string.ui_dynamic_add_1_s_0b8ac0a, skill.name)) },
            text = { Text(stringResource(R.string.ui_dynamic_this_organization_skill_will_be_enable_c4f96de, skill.packageName, skill.publisher)) },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                addPending = null
                busy = true
                scope.launch {
                    try {
                        session.addOrganizationSkill(botId, skill.installId, skill.name)
                        refresh()
                        refreshOrganization()
                    } catch (failure: Exception) {
                        error = failure.message ?: "Could not add organization skill."
                    } finally { busy = false }
                }
            }) { Text(stringResource(R.string.ui_add_and_enable_cd1bbf5)) } },
            dismissButton = { TextButton(onClick = { addPending = null }) { Text(stringResource(R.string.ui_cancel_77dfd21)) } },
        )
    }
}
