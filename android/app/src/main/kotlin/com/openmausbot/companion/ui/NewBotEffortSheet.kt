package com.openmausbot.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import kotlinx.coroutines.launch

/** Host-wide fallback effort for future bots, separate from the default model. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewBotEffortSheet(onDismiss: () -> Unit) {
    val session = LocalCompanion.current.session
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val config = session.configStatus()
                ?: throw IllegalStateException("Could not load the computer's new-bot defaults.")
            original = config.newBots?.effort
            selected = original
        } catch (failure: Exception) {
            error = failure.message ?: "Could not load the default effort."
        } finally {
            loading = false
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Default reasoning for new bots", style = MaterialTheme.typography.titleLarge)
            Text("This is a fallback for future bots. Their own model and effort choices take priority, and unsupported levels are skipped.")
            if (!loading) {
                ChoicePicker(
                    label = "Reasoning effort",
                    choices = listOf(VoiceChoice("", "Use model default", null, true)) +
                        listOf("none", "low", "medium", "high", "xhigh", "max").map {
                            VoiceChoice(it, ModelRules.effortLabel(it), null, true)
                        },
                    selected = selected.orEmpty(),
                    enabled = !saving,
                    onSelect = { selected = it.ifEmpty { null }; error = null },
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(enabled = !loading && !saving && error == null && selected != original, onClick = {
                scope.launch {
                    saving = true
                    try {
                        val current = session.configStatus()
                            ?: throw IllegalStateException("Could not verify the computer's current settings.")
                        if (current.newBots?.effort != original) {
                            throw IllegalStateException("The computer's setting changed. Reopen this screen to review it.")
                        }
                        val saved = session.updateNewBotEffort(selected).newBots?.effort
                        if (saved != selected) throw IllegalStateException("The computer did not confirm the saved effort.")
                        onDismiss()
                    } catch (failure: Exception) {
                        error = failure.message ?: "Could not save the default effort."
                    } finally {
                        saving = false
                    }
                }
            }) { Text(if (saving) "Saving…" else "Save") }
            TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") }
        }
    }
}
