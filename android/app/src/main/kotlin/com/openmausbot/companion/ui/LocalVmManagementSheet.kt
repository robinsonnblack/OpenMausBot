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

/** Every action targets the paired computer. Merely opening this screen changes nothing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocalVmManagementSheet(onDismiss: () -> Unit) {
    val session = LocalCompanion.current.session
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<LocalVmStatus?>(null) }
    var inventory by remember { mutableStateOf<LocalVmInventory?>(null) }
    var mode by remember { mutableStateOf("shared") }
    var maximum by remember { mutableStateOf(2) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        val current = session.localVmStatus()
        status = current
        mode = current.mode
        maximum = current.maxInstances
        inventory = if (current.mode == "per-bot") session.localVmInventory() else null
    }
    LaunchedEffect(Unit) {
        try { refresh() }
        catch (failure: Exception) { error = failure.message ?: "Could not load Local VM status." }
        finally { loading = false }
    }
    fun act(action: String) {
        busy = true
        error = null
        scope.launch {
            try { session.localVmAction(action); refresh() }
            catch (failure: Exception) { error = failure.message ?: "Local VM action failed." }
            finally { busy = false }
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Local VM", style = MaterialTheme.typography.titleLarge)
            Text("This isolated desktop runs on the paired computer. Actions may download images or change its containers.",
                style = MaterialTheme.typography.bodySmall)
            if (loading || busy) CircularProgressIndicator()
            status?.let { current ->
                Text("Runtime: ${current.runtime ?: "not installed"} · ${if (current.daemonUp) "running" else "not running"}")
                Text("Image: ${if (current.image) "available" else "missing"} · Container: ${current.container}")
                Text(if (current.ready) "Desktop ready" else current.problem ?: "Desktop not ready")
                Text("Isolation mode", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("shared", "per-bot").forEach { value ->
                        FilterChip(selected = mode == value, onClick = { mode = value },
                            label = { Text(if (value == "shared") "Shared" else "Per bot") })
                    }
                }
                if (mode == "per-bot") {
                    Text("Maximum isolated desktops")
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
                                throw IllegalStateException("The computer's VM policy changed. Refresh before saving.")
                            }
                            val requested = LocalVmConfig(mode, maximum)
                            val saved = session.updateLocalVmConfig(requested).localVm
                            if (saved != requested) throw IllegalStateException("The computer did not confirm the saved policy.")
                            refresh()
                        } catch (failure: Exception) { error = failure.message ?: "Could not save VM policy." }
                        finally { busy = false }
                    }
                }) { Text("Save VM policy") }

                if (current.mode == "shared") {
                    Text("Shared VM", style = MaterialTheme.typography.titleMedium)
                    if (current.runtime != null && current.daemonUp) {
                        if (!current.image) TextButton(enabled = !busy, onClick = { confirmation = "pull" }) { Text("Download image") }
                        if (current.image && current.container == "missing") TextButton(enabled = !busy, onClick = { confirmation = "run" }) { Text("Create desktop") }
                        if (current.container == "stopped") TextButton(enabled = !busy, onClick = { confirmation = "start" }) { Text("Start desktop") }
                        if (current.container == "running") TextButton(enabled = !busy, onClick = { confirmation = "stop" }) { Text("Stop desktop") }
                        if (current.container != "missing") TextButton(enabled = !busy, onClick = { confirmation = "remove" }) { Text("Remove desktop", color = MaterialTheme.colorScheme.error) }
                    }
                } else {
                    Text("Per-bot desktops", style = MaterialTheme.typography.titleMedium)
                    inventory?.let { list ->
                        if (!list.available) Text(list.problem ?: "Runtime unavailable")
                        if (list.instances.isEmpty()) Text("No per-bot desktop is currently installed.")
                        list.instances.forEach { instance ->
                            Text("${instance.name}: ${instance.container} · ${if (instance.ready) "ready" else instance.problem ?: "not ready"}${if (instance.inUse) " · in use" else ""}")
                        }
                    }
                }
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try { refresh() }
                        catch (failure: Exception) { error = failure.message ?: "Could not refresh VM status." }
                        finally { busy = false }
                    }
                }) { Text("Refresh") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Done") }
        }
    }

    confirmation?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text("${action.replaceFirstChar(Char::uppercase)} the Local VM on this computer?") },
            text = { Text(when (action) {
                "pull" -> "This downloads the VM image to the paired computer."
                "remove" -> "This deletes the shared VM container on the paired computer. Files stored inside it may be lost."
                else -> "This changes the shared VM on the paired computer."
            }) },
            confirmButton = { TextButton(onClick = { confirmation = null; act(action) }) { Text("Continue") } },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } },
        )
    }
}
