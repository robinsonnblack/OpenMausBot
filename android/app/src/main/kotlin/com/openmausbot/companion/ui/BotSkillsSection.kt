package com.openmausbot.companion.ui

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
        Text("Skills are stored on the paired computer and can change this bot's behavior.")
        if (loading) CircularProgressIndicator()
        skills.forEach { skill ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(skill.name, style = MaterialTheme.typography.titleSmall)
                Text(skill.description)
                Text("${if (skill.enabled) "Enabled" else "Disabled"} · ${skill.source}")
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
                    }) { Text(if (skill.enabled) "Read" else "Review and enable") }
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
                    }) { Text("Disable") }
                    TextButton(enabled = !busy, onClick = { removePending = skill }) { Text("Remove") }
                }
            }
        }
        if (!loading && skills.isEmpty()) Text("No skills installed for this bot.")
        if (stagedCount > 0) Text("$stagedCount skill proposal(s) await a decision in chat.")
        OutlinedTextField(
            value = source,
            onValueChange = { source = it },
            label = { Text("Import from GitHub (URL or owner/repo)") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
        )
        Text("Imports start disabled. Review the full SKILL.md before enabling.")
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
        }) { Text("Import") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (organization != null && offered.isNotEmpty()) {
            Text("From $organization", style = MaterialTheme.typography.titleSmall)
            Text("These skills were published by your organization's admin. Adding one enables it for this bot.")
            offered.forEach { skill ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(skill.name, style = MaterialTheme.typography.titleSmall)
                    Text(skill.description)
                    Text("${skill.packageName} · ${skill.release} · ${skill.publisher}")
                    if (skill.added) Text("Added")
                    else TextButton(enabled = !busy, onClick = { addPending = skill }) { Text("Add to bot") }
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
                    Text("Read the complete skill before enabling it. It may instruct the bot to use tools.")
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
                }) { Text("Enable this skill") }
            },
            dismissButton = { TextButton(onClick = { preview = null }, enabled = !busy) { Text("Close") } },
        )
    }
    removePending?.let { skill ->
        AlertDialog(
            onDismissRequest = { removePending = null },
            title = { Text("Remove ${skill.name}?") },
            text = { Text("This removes the skill from the paired computer.") },
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
            }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removePending = null }) { Text("Cancel") } },
        )
    }
    addPending?.let { skill ->
        AlertDialog(
            onDismissRequest = { addPending = null },
            title = { Text("Add ${skill.name}?") },
            text = { Text("This organization skill will be enabled for the bot immediately. Source: ${skill.packageName} by ${skill.publisher}.") },
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
            }) { Text("Add and enable") } },
            dismissButton = { TextButton(onClick = { addPending = null }) { Text("Cancel") } },
        )
    }
}
