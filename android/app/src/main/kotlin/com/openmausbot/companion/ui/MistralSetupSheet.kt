package com.openmausbot.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.core.ConfigStatus
import com.openmausbot.companion.core.ProviderConnection
import kotlinx.coroutines.launch

/** Admin-scoped provider setup; keys never enter Android preferences or logs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProviderSetupSheet(provider: ProviderConnection, onDismiss: () -> Unit) {
    val session = LocalCompanion.current.session
    val scope = rememberCoroutineScope()
    var configured by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var verdict by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }

    fun configuredIn(status: ConfigStatus?): Boolean = when (provider) {
        ProviderConnection.MISTRAL -> status?.mistral?.configured == true
        ProviderConnection.ANTHROPIC -> status?.anthropic?.configured == true
        ProviderConnection.XAI -> status?.xai?.configured == true
        ProviderConnection.OPENAI_COMPAT -> status?.openaiCompat?.configured == true
    }

    LaunchedEffect(provider) {
        try {
            val status = session.configStatus()
            configured = configuredIn(status)
            url = if (provider == ProviderConnection.OPENAI_COMPAT) status?.openaiCompat?.url.orEmpty() else ""
        }
        catch (failure: Exception) { error = failure.message ?: "Could not read provider status." }
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 550.dp)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("${provider.label} connection")
            Text(if (configured) "A key is configured on this computer." else "No key is configured.")
            Text("The key is saved on the paired computer, not on this phone.")
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(512); verdict = null; error = null },
                label = { Text(if (configured) "Replace API key" else "API key") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (provider != ProviderConnection.MISTRAL) OutlinedTextField(
                value = url, onValueChange = { url = it.take(2_000); verdict = null; error = null },
                label = { Text("Custom API URL (optional)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(enabled = !busy && draft.trim().isNotEmpty(), onClick = {
                scope.launch {
                    busy = true
                    try {
                        configured = configuredIn(session.setProviderConnection(provider, draft.trim(),
                            if (provider == ProviderConnection.MISTRAL) null else url.trim()))
                        draft = ""
                        verdict = if (configured) "Saved on this computer." else "The computer did not confirm the key."
                    } catch (failure: Exception) {
                        error = failure.message ?: "Could not save the key."
                    } finally { busy = false }
                }
            }) { Text("Save key") }
            TextButton(enabled = !busy && (draft.trim().isNotEmpty() || configured), onClick = {
                scope.launch {
                    busy = true
                    try {
                        val check = session.testProviderConnection(provider, draft.trim().ifEmpty { null },
                            if (provider == ProviderConnection.MISTRAL) null else url.trim().ifEmpty { null })
                        verdict = if (check.ok) {
                            if (check.models.isEmpty()) "The key reached ${provider.label}; no models were listed."
                            else "Available models: " + check.models.joinToString(", ")
                        } else {
                            when (check.reason) {
                                "rejected" -> "${provider.label} rejected the key."
                                "unreachable" -> "The computer could not reach ${provider.label}."
                                else -> "${provider.label} returned an unexpected response."
                            }
                        }
                    } catch (failure: Exception) {
                        error = failure.message ?: "Could not test the key."
                    } finally { busy = false }
                }
            }) { Text("Test and discover models") }
            if (configured) TextButton(enabled = !busy, onClick = { confirmRemove = true }) {
                Text("Remove key")
            }
            verdict?.let { Text(it) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Done") }
        }
    }

    if (confirmRemove) AlertDialog(
        onDismissRequest = { confirmRemove = false },
        title = { Text("Remove ${provider.label} key?") },
        text = { Text("Bots using ${provider.label} may become unavailable until a new key is saved.") },
        confirmButton = {
            TextButton(onClick = {
                confirmRemove = false
                scope.launch {
                    busy = true
                    try {
                        configured = configuredIn(session.setProviderConnection(provider, "",
                            if (provider == ProviderConnection.MISTRAL) null else url.trim()))
                        draft = ""
                        verdict = "${provider.label} key removed from this computer."
                    } catch (failure: Exception) {
                        error = failure.message ?: "Could not remove the key."
                    } finally { busy = false }
                }
            }) { Text("Remove") }
        },
        dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
    )
}
