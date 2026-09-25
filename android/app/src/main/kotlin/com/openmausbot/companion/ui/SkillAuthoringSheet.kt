package com.openmausbot.companion.ui

import androidx.compose.ui.platform.LocalContext
import com.openmausbot.companion.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.core.ConfigStatus
import kotlinx.coroutines.launch

/** The host's existing skill-drafting gate; proposed skills still require review. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SkillAuthoringSheet(onDismiss: () -> Unit) {
    val l10n = LocalContext.current
    val session = LocalCompanion.current.session
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf<ConfigStatus?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try { config = session.configStatus() }
        catch (failure: Exception) { error = failure.message ?: l10n.getString(R.string.android_remaining_skill_authoring_sheet_993c6ee8) }
        if (config == null && error == null) error = l10n.getString(R.string.android_remaining_skill_authoring_sheet_993c6ee8)
        loading = false
    }

    fun change(next: Boolean) {
        scope.launch {
            busy = true
            error = null
            try {
                val fresh = session.configStatus()
                    ?: throw IllegalStateException(l10n.getString(R.string.android_remaining_skill_authoring_sheet_526be934))
                if ((fresh.features?.skillAuthoring != false) != (config?.features?.skillAuthoring != false)) {
                    config = fresh
                    throw IllegalStateException(l10n.getString(R.string.android_remaining_skill_authoring_sheet_a5393f78))
                }
                config = session.updateSkillAuthoringEnabled(next)
            } catch (failure: Exception) {
                error = failure.message ?: l10n.getString(R.string.android_remaining_skill_authoring_sheet_35d6a8dc)
            } finally { busy = false }
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.ui_skill_authoring_on_this_computer_c606f2d), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.ui_when_enabled_bots_may_draft_new_skills_for_1370cc7))
            if (loading) CircularProgressIndicator()
            else config?.let { status ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ui_allow_bots_to_draft_skills_40a0794), modifier = Modifier.weight(1f))
                    Switch(
                        checked = status.features?.skillAuthoring != false,
                        enabled = !busy,
                        onCheckedChange = ::change,
                    )
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.ui_done_e9b450d)) }
        }
    }
}
