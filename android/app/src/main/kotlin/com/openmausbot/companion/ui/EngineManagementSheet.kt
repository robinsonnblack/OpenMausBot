package com.openmausbot.companion.ui

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
            Text(stringResource(R.string.ui_engines_7f5d63a), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.ui_these_engines_run_on_the_paired_computer_i_5761473),
                style = MaterialTheme.typography.bodySmall)
            if (loading) CircularProgressIndicator()
            engines.forEach { engine ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(engine.displayName ?: engine.instanceId, style = MaterialTheme.typography.titleMedium)
                    Text(if (engine.snapshot.version == null) {
                        stringResource(R.string.ui_engine_status_models_without_version,
                            engine.snapshot.state, engine.models.options.size)
                    } else {
                        stringResource(R.string.ui_engine_status_models,
                            engine.snapshot.state, engine.snapshot.version.orEmpty(), engine.models.options.size)
                    })
                    engine.snapshot.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Row {
                        TextButton(enabled = busy == null, onClick = { act(engine.instanceId, "refresh-models") }) {
                            Text(stringResource(R.string.ui_refresh_models_ba6da3f))
                        }
                        if (!engine.snapshot.isAvailable) TextButton(
                            enabled = busy == null, onClick = { confirmingInstall = engine },
                        ) { Text(stringResource(R.string.ui_install_on_computer_7ec9984)) }
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(enabled = busy == null, onClick = onDismiss) { Text(stringResource(R.string.ui_done_e9b450d)) }
        }
    }

    confirmingInstall?.let { engine ->
        AlertDialog(
            onDismissRequest = { confirmingInstall = null },
            title = { Text(stringResource(R.string.ui_dynamic_install_1_s_on_this_computer_48d0e36, engine.displayName ?: engine.instanceId)) },
            text = { Text(stringResource(R.string.ui_the_paired_computer_may_download_and_insta_0bfa662)) },
            confirmButton = { TextButton(onClick = {
                confirmingInstall = null
                act(engine.instanceId, "install")
            }) { Text(stringResource(R.string.ui_install_fd6c3eb)) } },
            dismissButton = { TextButton(onClick = { confirmingInstall = null }) { Text(stringResource(R.string.ui_cancel_77dfd21)) } },
        )
    }
}
