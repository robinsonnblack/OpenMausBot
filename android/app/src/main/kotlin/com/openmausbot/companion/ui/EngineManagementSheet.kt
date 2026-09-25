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
import com.openmausbot.companion.core.Instance
import kotlinx.coroutines.launch

/** Admin actions run on the paired computer; Android never installs an engine locally. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EngineManagementSheet(onDismiss: () -> Unit) {
    val session = LocalCompanion.current.session
    val scope = rememberCoroutineScope()
    var engines by remember { mutableStateOf<List<Instance>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmingInstall by remember { mutableStateOf<Instance?>(null) }

    LaunchedEffect(Unit) {
        try { engines = session.modelInstances() }
        catch (failure: Exception) { error = failure.message ?: "Could not load engines." }
        finally { loading = false }
    }

    fun act(id: String, action: String) {
        busy = id
        error = null
        scope.launch {
            try { engines = session.manageEngine(id, action) }
            catch (failure: Exception) { error = failure.message ?: "The computer could not manage this engine." }
            finally { busy = null }
        }
    }

    ModalBottomSheet(onDismissRequest = { if (busy == null) onDismiss() }) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 690.dp)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Engines", style = MaterialTheme.typography.titleLarge)
            Text("These engines run on the paired computer. Installation is offered only when that computer supports it.",
                style = MaterialTheme.typography.bodySmall)
            if (loading) CircularProgressIndicator()
            engines.forEach { engine ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(engine.displayName ?: engine.instanceId, style = MaterialTheme.typography.titleMedium)
                    Text("${engine.snapshot.state}${engine.snapshot.version?.let { " · $it" }.orEmpty()} · ${engine.models.options.size} models")
                    engine.snapshot.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Row {
                        TextButton(enabled = busy == null, onClick = { act(engine.instanceId, "refresh-models") }) {
                            Text("Refresh models")
                        }
                        if (!engine.snapshot.isAvailable) TextButton(
                            enabled = busy == null, onClick = { confirmingInstall = engine },
                        ) { Text("Install on computer") }
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(enabled = busy == null, onClick = onDismiss) { Text("Done") }
        }
    }

    confirmingInstall?.let { engine ->
        AlertDialog(
            onDismissRequest = { confirmingInstall = null },
            title = { Text("Install ${engine.displayName ?: engine.instanceId} on this computer?") },
            text = { Text("The paired computer may download and install this engine. This can take a while.") },
            confirmButton = { TextButton(onClick = {
                confirmingInstall = null
                act(engine.instanceId, "install")
            }) { Text("Install") } },
            dismissButton = { TextButton(onClick = { confirmingInstall = null }) { Text("Cancel") } },
        )
    }
}
