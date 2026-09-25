package com.openmausbot.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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

/** Admin-only host feature switch; each bot has a separate browser permission. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HostBrowserFeatureSheet(onDismiss: () -> Unit) {
    val session = LocalCompanion.current.session
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf<ConfigStatus?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDisable by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        config = session.configStatus()
        if (config == null) error = "Could not load the computer's browser settings."
        loading = false
    }

    fun change(next: Boolean) {
        scope.launch {
            busy = true
            error = null
            try {
                val fresh = session.configStatus()
                    ?: throw IllegalStateException("Could not refresh the computer's browser settings.")
                if ((fresh.features?.browser == true) != (config?.features?.browser == true)) {
                    config = fresh
                    throw IllegalStateException("The browser setting changed on the computer. Review it before saving.")
                }
                config = session.updateHostBrowserEnabled(next)
            } catch (failure: Exception) {
                error = failure.message ?: "Could not change browser access."
            } finally { busy = false }
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Built-in browser on this computer", style = MaterialTheme.typography.titleLarge)
            if (loading) CircularProgressIndicator()
            else config?.let { status ->
                val active = status.features?.browser == true
                val engine = status.browserEngine
                val available = engine?.kind == "engine"
                Text(when {
                    available && active -> "Bots with browser permission can use the computer's built-in browser."
                    available -> "The browser engine is ready, but host-wide access is off."
                    engine?.installing == true -> "The browser engine is installing on the computer."
                    active -> "Host-wide access is on, but the browser engine is not ready."
                    else -> engine?.reason ?: "The browser engine is not ready on the computer."
                })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable built-in browser", modifier = Modifier.weight(1f))
                    Switch(
                        checked = active,
                        enabled = !busy && (active || available || engine?.installable == true),
                        onCheckedChange = { next ->
                            if (!next && active) confirmDisable = true else change(next)
                        },
                    )
                }
                if (!available && engine?.installable == true) {
                    Text("Install the browser engine from Settings → Engines before bots can use it.")
                }
                Text("Each bot also needs its own Built-in browser permission. Disabling this host switch closes live browser sessions on the computer.")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Done") }
        }
    }

    if (confirmDisable) AlertDialog(
        onDismissRequest = { confirmDisable = false },
        title = { Text("Turn off the built-in browser?") },
        text = { Text("This closes live built-in browser sessions on the paired computer. Bots will lose browser access until you turn it on again.") },
        confirmButton = {
            TextButton(onClick = { confirmDisable = false; change(false) }) { Text("Turn off") }
        },
        dismissButton = { TextButton(onClick = { confirmDisable = false }) { Text("Cancel") } },
    )
}
