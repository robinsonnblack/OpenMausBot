package com.openmausbot.companion.ui

import androidx.compose.ui.platform.LocalContext
import com.openmausbot.companion.R
import androidx.compose.ui.res.stringResource
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
    val l10n = LocalContext.current
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
        catch (failure: Exception) { error = failure.message ?: l10n.getString(R.string.android_remaining_browser_profiles_sheet_0090d07d) }
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
                error = failure.message ?: l10n.getString(R.string.android_remaining_browser_profiles_sheet_30cfb0dd)
                // A whole-list PATCH cannot safely merge another window's edits.
                // Refresh the baseline, retain typed drafts, and require a new tap.
                try { profiles = session.configStatus()?.browserProfiles.orEmpty() }
                catch (_: Exception) { error = l10n.getString(R.string.android_remaining_browser_profiles_sheet_6303e02a) }
            } finally { busy = false }
        }
    }

    deleting?.let { profile ->
        val users = state.bots.filter { it.browserProfile == profile.id }
        AlertDialog(
            onDismissRequest = { if (!busy) deleting = null },
            title = { Text(stringResource(R.string.ui_dynamic_delete_1_s_cd24016, profile.name)) },
            text = { Text(if (users.isEmpty()) stringResource(R.string.ui_browser_session_erased)
                else stringResource(R.string.ui_browser_session_erased_users,
                    users.joinToString { it.name })) },
            confirmButton = {
                TextButton(enabled = !busy && users.none { it.busy == true }, onClick = {
                    save(profiles.filterNot { it.id == profile.id }) { deleting = null }
                }) { Text(stringResource(R.string.ui_delete_f6fdbe4)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.ui_cancel_77dfd21)) } },
        )
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.ui_browser_profiles_f7370dc), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.ui_named_browser_sessions_live_on_the_paired_863fb0f))
            if (loading) CircularProgressIndicator()
            else {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (profiles.isEmpty()) Text(stringResource(R.string.ui_no_named_browser_profiles_yet_3363a04))
                profiles.forEach { profile ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(profile.name, modifier = Modifier.weight(1f))
                        TextButton(enabled = !busy, onClick = {
                            editing = profile
                            editName = profile.name
                        }) { Text(stringResource(R.string.ui_rename_d3f4cb8)) }
                        TextButton(enabled = !busy && state.bots.none { it.browserProfile == profile.id && it.busy == true },
                            onClick = { deleting = profile }) { Text(stringResource(R.string.ui_delete_f6fdbe4)) }
                    }
                }
                editing?.let { profile ->
                    OutlinedTextField(
                        value = editName, onValueChange = { editName = it.take(40) },
                        label = { Text(stringResource(R.string.ui_dynamic_rename_1_s_f90cb3c, profile.name)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row {
                        TextButton(enabled = !busy && editName.isNotBlank(), onClick = {
                            if (profiles.none { it.id == profile.id }) {
                                error = l10n.getString(R.string.android_remaining_browser_profiles_sheet_867a30f2)
                            } else save(profiles.map { if (it.id == profile.id) it.copy(name = editName.trim()) else it }) {
                                editing = null
                            }
                        }) { Text(stringResource(R.string.ui_save_name_8ce6864)) }
                        TextButton(enabled = !busy, onClick = { editing = null }) { Text(stringResource(R.string.ui_cancel_77dfd21)) }
                    }
                }
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it.take(40) },
                    label = { Text(stringResource(R.string.ui_new_profile_name_393313b)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(enabled = !busy && newName.isNotBlank() && profiles.size < 20, onClick = {
                    val id = "profile-${UUID.randomUUID().toString().replace("-", "")}"
                    save(profiles + BrowserProfile(id, newName.trim())) { newName = "" }
                }) { Text(stringResource(R.string.ui_create_profile_96b8eeb)) }
                if (profiles.size >= 20) Text(stringResource(R.string.ui_the_computer_allows_at_most_20_profiles_e631e10))
            }
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.ui_done_e9b450d)) }
        }
    }
}
