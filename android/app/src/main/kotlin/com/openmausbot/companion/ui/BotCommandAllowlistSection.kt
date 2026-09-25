package com.openmausbot.companion.ui

import androidx.compose.ui.platform.LocalContext
import com.openmausbot.companion.R
import androidx.compose.ui.res.stringResource
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
    val l10n = LocalContext.current
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
            error = failure.message ?: l10n.getString(R.string.android_remaining_bot_command_allowlist_section_48148c53)
        } finally {
            loading = false
        }
    }
    LaunchedEffect(botId, connectionId) { reload() }

    removing?.let { rule ->
        AlertDialog(
            onDismissRequest = { if (!busy) removing = null },
            title = { Text(stringResource(R.string.ui_remove_command_permission_90940a7)) },
            text = { Text(stringResource(R.string.ui_dynamic_this_bot_will_ask_again_before_running_897f150, rule.cwd, rule.command)) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            status = session.removeBotCommandRule(botId, rule.id)
                            removing = null
                        } catch (failure: Exception) {
                            error = failure.message ?: l10n.getString(R.string.android_remaining_bot_command_allowlist_section_293a7f0c)
                        } finally { busy = false }
                    }
                }) { Text(stringResource(R.string.ui_remove_e963907)) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { removing = null }) { Text(stringResource(R.string.ui_cancel_77dfd21)) } },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.ui_an_exact_command_is_allowed_only_for_this_18c839e))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (loading) CircularProgressIndicator()
        if (status == null && !loading) {
            TextButton(onClick = { scope.launch { reload() } }) { Text(stringResource(R.string.ui_try_loading_again_b5375fa)) }
        }
        status?.let { current ->
            if (current.rules.isEmpty()) Text(stringResource(R.string.ui_no_commands_are_always_allowed_for_this_bo_95cac14))
            current.rules.forEach { rule ->
                Column {
                    Text(rule.command, style = MaterialTheme.typography.bodyMedium)
                    Text("${rule.providerInstanceId} · ${rule.cwd}", style = MaterialTheme.typography.bodySmall)
                    TextButton(enabled = !busy, onClick = { removing = rule }) { Text(stringResource(R.string.ui_remove_permission_2114922)) }
                }
            }
            if (current.supported) {
                Text(stringResource(R.string.ui_dynamic_current_provider_1_s_386803f, current.context.providerInstanceId))
                OutlinedTextField(
                    value = command, onValueChange = { command = it },
                    label = { Text(stringResource(R.string.ui_exact_command_a93a31d)) }, modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                OutlinedTextField(
                    value = cwd, onValueChange = { cwd = it },
                    label = { Text(stringResource(R.string.ui_absolute_working_folder_4c69ed7)) }, modifier = Modifier.fillMaxWidth(),
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
                                    error = l10n.getString(R.string.android_remaining_bot_command_allowlist_section_105f056a)
                                } else {
                                    status = session.addBotCommandRule(
                                        botId, command, cwd.trim(), current.context.providerInstanceId,
                                    )
                                    command = ""
                                }
                            } catch (failure: Exception) {
                                error = failure.message ?: l10n.getString(R.string.android_remaining_bot_command_allowlist_section_5d05d469)
                            } finally { busy = false }
                        }
                    }) { Text(stringResource(if (busy) R.string.ui_saving else R.string.ui_always_allow_exact_command)) }
                }
            } else Text(stringResource(R.string.ui_this_bot_s_current_provider_does_not_suppo_9fa61ff))
        }
    }
}
