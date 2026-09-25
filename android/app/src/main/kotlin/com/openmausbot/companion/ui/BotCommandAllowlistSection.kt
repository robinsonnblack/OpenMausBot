package com.openmausbot.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.openmausbot.companion.core.CommandAllowlistRule
import com.openmausbot.companion.core.CommandAllowlistStatus
import kotlinx.coroutines.launch

/** Exact host command permissions, scoped to one bot and its current provider. */
@Composable
internal fun BotCommandAllowlistSection(botId: String, connectionId: String?) {
    val session = LocalCompanion.current.session
    val scope = rememberCoroutineScope()
    var status by remember(botId, connectionId) { mutableStateOf<CommandAllowlistStatus?>(null) }
    var loading by remember(botId, connectionId) { mutableStateOf(true) }
    var busy by remember(botId, connectionId) { mutableStateOf(false) }
    var error by remember(botId, connectionId) { mutableStateOf<String?>(null) }
    var command by remember(botId, connectionId) { mutableStateOf("") }
    var cwd by remember(botId, connectionId) { mutableStateOf("") }
    var removing by remember(botId, connectionId) { mutableStateOf<CommandAllowlistRule?>(null) }

    suspend fun reload() {
        loading = true
        error = null
        status = null
        try {
            val next = session.botCommandAllowlist(botId)
            status = next
            cwd = next.context.cwd.orEmpty()
        } catch (failure: Exception) {
            error = failure.message ?: "Could not load command permissions."
        } finally {
            loading = false
        }
    }
    LaunchedEffect(botId, connectionId) { reload() }

    removing?.let { rule ->
        AlertDialog(
            onDismissRequest = { if (!busy) removing = null },
            title = { Text("Remove command permission?") },
            text = { Text("This bot will ask again before running this exact command in ${rule.cwd}:\n${rule.command}") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            status = session.removeBotCommandRule(botId, rule.id)
                            removing = null
                        } catch (failure: Exception) {
                            error = failure.message ?: "Could not remove this permission."
                        } finally { busy = false }
                    }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { removing = null }) { Text("Cancel") } },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("An exact command is allowed only for this bot, provider and working folder. Never enter secrets in a command.")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (loading) CircularProgressIndicator()
        if (status == null && !loading) {
            TextButton(onClick = { scope.launch { reload() } }) { Text("Try loading again") }
        }
        status?.let { current ->
            if (current.rules.isEmpty()) Text("No commands are always allowed for this bot.")
            current.rules.forEach { rule ->
                Column {
                    Text(rule.command, style = MaterialTheme.typography.bodyMedium)
                    Text("${rule.providerInstanceId} · ${rule.cwd}", style = MaterialTheme.typography.bodySmall)
                    TextButton(enabled = !busy, onClick = { removing = rule }) { Text("Remove permission") }
                }
            }
            if (current.supported) {
                Text("Current provider: ${current.context.providerInstanceId}")
                OutlinedTextField(
                    value = command, onValueChange = { command = it },
                    label = { Text("Exact command") }, modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                OutlinedTextField(
                    value = cwd, onValueChange = { cwd = it },
                    label = { Text("Absolute working folder") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row {
                    TextButton(enabled = !busy && command.isNotBlank() && cwd.isNotBlank(), onClick = {
                        scope.launch {
                            busy = true
                            error = null
                            try {
                                // A desktop edit or provider switch must be reviewed before saving.
                                val fresh = session.botCommandAllowlist(botId)
                                if (fresh.context != current.context || !fresh.supported) {
                                    status = fresh
                                    error = "The bot's provider or working folder changed. Review the new context before adding a rule."
                                } else {
                                    status = session.addBotCommandRule(
                                        botId, command, cwd.trim(), current.context.providerInstanceId,
                                    )
                                    command = ""
                                }
                            } catch (failure: Exception) {
                                error = failure.message ?: "Could not add this permission."
                            } finally { busy = false }
                        }
                    }) { Text(if (busy) "Saving…" else "Always allow exact command") }
                }
            } else Text("This bot's current provider does not support structured command approvals.")
        }
    }
}
