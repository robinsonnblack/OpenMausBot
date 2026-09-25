package com.openmausbot.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.core.BrowserProfile
import java.util.UUID
import kotlinx.coroutines.launch

/** Manages named browser sessions on the paired computer, not phone cookies. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowserProfilesSheet(onDismiss: () -> Unit) {
    val session = LocalCompanion.current.session
    val state by session.state.collectAsState()
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf<List<BrowserProfile>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<BrowserProfile?>(null) }
    var editName by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<BrowserProfile?>(null) }

    LaunchedEffect(Unit) {
        try { profiles = session.configStatus()?.browserProfiles.orEmpty() }
        catch (failure: Exception) { error = failure.message ?: "Could not load browser profiles." }
        finally { loading = false }
    }

    fun save(next: List<BrowserProfile>, onSaved: () -> Unit = {}) {
        busy = true
        error = null
        scope.launch {
            try {
                profiles = session.updateBrowserProfiles(profiles, next).browserProfiles
                onSaved()
            } catch (failure: Exception) {
                error = failure.message ?: "Could not save browser profiles."
                // A whole-list PATCH cannot safely merge another window's edits.
                // Refresh the baseline, retain typed drafts, and require a new tap.
                try { profiles = session.configStatus()?.browserProfiles.orEmpty() }
                catch (_: Exception) { error = "Could not refresh browser profiles. Reopen this screen before retrying." }
            } finally { busy = false }
        }
    }

    deleting?.let { profile ->
        val users = state.bots.filter { it.browserProfile == profile.id }
        AlertDialog(
            onDismissRequest = { if (!busy) deleting = null },
            title = { Text("Delete ${profile.name}?") },
            text = { Text(if (users.isEmpty()) "This browser session will be erased on the computer."
                else "This browser session will be erased. ${users.joinToString { it.name }} will return to their own browser.") },
            confirmButton = {
                TextButton(enabled = !busy && users.none { it.busy == true }, onClick = {
                    save(profiles.filterNot { it.id == profile.id }) { deleting = null }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Browser profiles", style = MaterialTheme.typography.titleLarge)
            Text("Named browser sessions live on the paired computer and can be shared by bots.")
            if (loading) CircularProgressIndicator()
            else {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (profiles.isEmpty()) Text("No named browser profiles yet.")
                profiles.forEach { profile ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(profile.name, modifier = Modifier.weight(1f))
                        TextButton(enabled = !busy, onClick = {
                            editing = profile
                            editName = profile.name
                        }) { Text("Rename") }
                        TextButton(enabled = !busy && state.bots.none { it.browserProfile == profile.id && it.busy == true },
                            onClick = { deleting = profile }) { Text("Delete") }
                    }
                }
                editing?.let { profile ->
                    OutlinedTextField(
                        value = editName, onValueChange = { editName = it.take(40) },
                        label = { Text("Rename ${profile.name}") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row {
                        TextButton(enabled = !busy && editName.isNotBlank(), onClick = {
                            if (profiles.none { it.id == profile.id }) {
                                error = "This profile was removed on the computer. Review the list before saving."
                            } else save(profiles.map { if (it.id == profile.id) it.copy(name = editName.trim()) else it }) {
                                editing = null
                            }
                        }) { Text("Save name") }
                        TextButton(enabled = !busy, onClick = { editing = null }) { Text("Cancel") }
                    }
                }
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it.take(40) },
                    label = { Text("New profile name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(enabled = !busy && newName.isNotBlank() && profiles.size < 20, onClick = {
                    val id = "profile-${UUID.randomUUID().toString().replace("-", "")}"
                    save(profiles + BrowserProfile(id, newName.trim())) { newName = "" }
                }) { Text("Create profile") }
                if (profiles.size >= 20) Text("The computer allows at most 20 profiles.")
            }
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Done") }
        }
    }
}
