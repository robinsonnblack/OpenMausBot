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
import androidx.compose.material3.FilterChip
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
import com.openmausbot.companion.core.LocalVmConfig
import com.openmausbot.companion.core.LocalVmInventory
import com.openmausbot.companion.core.LocalVmStatus
import kotlinx.coroutines.launch

private data class PendingLocalVmAction(val action: String, val botId: String? = null, val botName: String? = null)

/** Every action targets the paired computer. Merely opening this screen changes nothing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocalVmManagementSheet(onDismiss: () -> Unit) {
    val l10n = LocalContext.current
    val session = LocalCompanion.current.session
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<LocalVmStatus?>(null) }
    var inventory by remember { mutableStateOf<LocalVmInventory?>(null) }
    var mode by remember { mutableStateOf("shared") }
    var maximum by remember { mutableStateOf(2) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf<PendingLocalVmAction?>(null) }

    suspend fun refresh() {
        val current = session.localVmStatus()
        status = current
        mode = current.mode
        maximum = current.maxInstances
        inventory = if (current.mode == "per-bot") session.localVmInventory() else null
    }
    LaunchedEffect(Unit) {
        try { refresh() }
        catch (failure: Exception) { error = failure.message ?: l10n.getString(R.string.android_remaining_local_vm_management_sheet_54cb2ca4) }
        finally { loading = false }
    }
    fun act(target: PendingLocalVmAction) {
        busy = true
        error = null
        scope.launch {
            try {
                if (target.botId != null) session.botLocalVmAction(target.botId, target.action)
                else session.localVmAction(target.action)
                refresh()
            }
            catch (failure: Exception) { error = failure.message ?: l10n.getString(R.string.android_local_vm_action_error) }
            finally { busy = false }
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.ui_local_vm_7f61d89), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.ui_this_isolated_desktop_runs_on_the_paired_c_ad72a1c),
                style = MaterialTheme.typography.bodySmall)
            if (loading || busy) CircularProgressIndicator()
            status?.let { current ->
                Text(stringResource(R.string.ui_vm_runtime_status,
                    current.runtime ?: stringResource(R.string.ui_vm_not_installed),
                    stringResource(if (current.daemonUp) R.string.ui_vm_running else R.string.ui_vm_not_running)))
                Text(stringResource(R.string.ui_vm_image_container_status,
                    stringResource(if (current.image) R.string.ui_vm_available else R.string.ui_vm_missing),
                    current.container))
                Text(if (current.ready) stringResource(R.string.ui_vm_desktop_ready)
                    else current.problem ?: stringResource(R.string.ui_vm_desktop_not_ready))
                Text(stringResource(R.string.ui_isolation_mode_4166201), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("shared", "per-bot").forEach { value ->
                        FilterChip(selected = mode == value, onClick = { mode = value },
                            label = { Text(stringResource(if (value == "shared") R.string.ui_vm_shared else R.string.ui_vm_per_bot)) })
                    }
                }
                if (mode == "per-bot") {
                    Text(stringResource(R.string.ui_maximum_isolated_desktops_5fe6c2f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..4).forEach { value ->
                            FilterChip(selected = maximum == value, onClick = { maximum = value },
                                label = { Text(value.toString()) })
                        }
                    }
                }
                TextButton(enabled = !busy && (mode != current.mode || maximum != current.maxInstances), onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val latest = session.localVmStatus()
                            if (latest.mode != current.mode || latest.maxInstances != current.maxInstances) {
                                throw IllegalStateException(l10n.getString(R.string.android_remaining_local_vm_management_sheet_eeb784a8))
                            }
                            val requested = LocalVmConfig(mode, maximum)
                            val saved = session.updateLocalVmConfig(requested).localVm
                            if (saved != requested) throw IllegalStateException(l10n.getString(R.string.android_remaining_local_vm_management_sheet_b0338c59))
                            refresh()
                        } catch (failure: Exception) { error = failure.message ?: l10n.getString(R.string.android_remaining_local_vm_management_sheet_adc16026) }
                        finally { busy = false }
                    }
                }) { Text(stringResource(R.string.ui_save_vm_policy_a09a1e6)) }

                if (current.mode == "shared") {
                    Text(stringResource(R.string.ui_shared_vm_85baef0), style = MaterialTheme.typography.titleMedium)
                    if (current.runtime != null && current.daemonUp) {
                        if (!current.image) TextButton(enabled = !busy, onClick = { confirmation = PendingLocalVmAction("pull") }) { Text(stringResource(R.string.ui_download_image_d6f34a9)) }
                        if (current.image && current.container == "missing") TextButton(enabled = !busy, onClick = { confirmation = PendingLocalVmAction("run") }) { Text(stringResource(R.string.ui_create_desktop_6f5358e)) }
                        if (current.container == "stopped") TextButton(enabled = !busy, onClick = { confirmation = PendingLocalVmAction("start") }) { Text(stringResource(R.string.ui_start_desktop_2c8d254)) }
                        if (current.container == "running") TextButton(enabled = !busy, onClick = { confirmation = PendingLocalVmAction("stop") }) { Text(stringResource(R.string.ui_stop_desktop_1f13f5b)) }
                        if (current.container != "missing") TextButton(enabled = !busy, onClick = { confirmation = PendingLocalVmAction("remove") }) { Text(stringResource(R.string.ui_remove_desktop_5ed19d5), color = MaterialTheme.colorScheme.error) }
                    }
                } else {
                    Text(stringResource(R.string.ui_per_bot_desktops_56e561d), style = MaterialTheme.typography.titleMedium)
                    inventory?.let { list ->
                        if (!list.available) Text(list.problem ?: stringResource(R.string.ui_vm_runtime_unavailable))
                        if (list.instances.isEmpty()) Text(stringResource(R.string.ui_no_per_bot_desktop_is_currently_installed_6521cb4))
                        list.instances.forEach { instance ->
                            Column {
                                Text(stringResource(R.string.ui_vm_instance_status,
                                    instance.name,
                                    instance.container,
                                    if (instance.ready) stringResource(R.string.ui_vm_ready)
                                        else instance.problem ?: stringResource(R.string.ui_vm_not_ready)) +
                                    if (instance.inUse) stringResource(R.string.ui_vm_in_use_suffix) else "")
                                if (instance.managed) Row {
                                    if (instance.container == "running") TextButton(enabled = !busy && !instance.inUse,
                                        onClick = { confirmation = PendingLocalVmAction("stop", instance.botId, instance.name) }) { Text(stringResource(R.string.ui_stop_9e25347)) }
                                    TextButton(enabled = !busy && !instance.inUse,
                                        onClick = { confirmation = PendingLocalVmAction("remove", instance.botId, instance.name) }) {
                                        Text(stringResource(R.string.ui_remove_e963907), color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try { refresh() }
                        catch (failure: Exception) { error = failure.message ?: l10n.getString(R.string.android_remaining_local_vm_management_sheet_7fa7e0f5) }
                        finally { busy = false }
                    }
                }) { Text(stringResource(R.string.ui_refresh_56e3bad)) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.ui_done_e9b450d)) }
        }
    }

    confirmation?.let { target ->
        val actionName = stringResource(when (target.action) {
            "pull" -> R.string.ui_vm_download_action
            "remove" -> R.string.ui_remove_action
            "start" -> R.string.ui_vm_start_action
            "stop" -> R.string.ui_vm_stop_action
            else -> R.string.ui_vm_change_action
        })
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(if (target.botId != null) stringResource(R.string.ui_vm_bot_action_confirm, actionName, target.botName.orEmpty())
                else stringResource(R.string.ui_vm_shared_action_confirm, actionName)) },
            text = { Text(if (target.botId != null) when (target.action) {
                "remove" -> stringResource(R.string.ui_vm_remove_bot_explanation, target.botName.orEmpty())
                else -> stringResource(R.string.ui_vm_stop_bot_explanation, target.botName.orEmpty())
            } else when (target.action) {
                "pull" -> stringResource(R.string.ui_vm_download_explanation)
                "remove" -> stringResource(R.string.ui_vm_remove_shared_explanation)
                else -> stringResource(R.string.ui_vm_change_shared_explanation)
            }) },
            confirmButton = { TextButton(onClick = { confirmation = null; act(target) }) { Text(stringResource(R.string.ui_continue_2e02623)) } },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text(stringResource(R.string.ui_cancel_77dfd21)) } },
        )
    }
}
