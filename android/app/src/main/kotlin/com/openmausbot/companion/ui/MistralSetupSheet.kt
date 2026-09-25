package com.openmausbot.companion.ui

import androidx.compose.ui.platform.LocalContext
import com.openmausbot.companion.R
import androidx.compose.ui.res.stringResource
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
    val l10n = LocalContext.current
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
        catch (failure: Exception) { error = failure.message ?: l10n.getString(R.string.android_remaining_mistral_setup_sheet_2366f64f) }
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 550.dp)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.ui_dynamic_1_s_connection_8b94a5f, provider.label))
            Text(stringResource(if (configured) R.string.ui_mistral_key_configured else R.string.ui_mistral_no_key))
            Text(stringResource(R.string.ui_the_key_is_saved_on_the_paired_computer_no_4b5a828))
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(512); verdict = null; error = null },
                label = { Text(stringResource(if (configured) R.string.ui_mistral_replace_key else R.string.ui_mistral_api_key)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (provider != ProviderConnection.MISTRAL) OutlinedTextField(
                value = url, onValueChange = { url = it.take(2_000); verdict = null; error = null },
                label = { Text(stringResource(R.string.ui_custom_api_url_optional_35fec44)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(enabled = !busy && draft.trim().isNotEmpty(), onClick = {
                scope.launch {
                    busy = true
                    try {
                        configured = configuredIn(session.setProviderConnection(provider, draft.trim(),
                            if (provider == ProviderConnection.MISTRAL) null else url.trim()))
                        draft = ""
                        verdict = if (configured) l10n.getString(R.string.android_remaining_mistral_setup_sheet_3a91d94e) else l10n.getString(R.string.android_remaining_mistral_setup_sheet_22356ceb)
                    } catch (failure: Exception) {
                        error = failure.message ?: l10n.getString(R.string.android_remaining_mistral_setup_sheet_fa674564)
                    } finally { busy = false }
                }
            }) { Text(stringResource(R.string.ui_save_key_f5216b3)) }
            TextButton(enabled = !busy && (draft.trim().isNotEmpty() || configured), onClick = {
                scope.launch {
                    busy = true
                    try {
                        val check = session.testProviderConnection(provider, draft.trim().ifEmpty { null },
                            if (provider == ProviderConnection.MISTRAL) null else url.trim().ifEmpty { null })
                        verdict = if (check.ok) {
                            if (check.models.isEmpty()) l10n.getString(R.string.android_provider_no_models, provider.label)
                            else l10n.getString(R.string.android_provider_available_models, check.models.joinToString(", "))
                        } else {
                            when (check.reason) {
                                "rejected" -> l10n.getString(R.string.android_provider_key_rejected, provider.label)
                                "unreachable" -> l10n.getString(R.string.android_provider_unreachable, provider.label)
                                else -> l10n.getString(R.string.android_provider_unexpected, provider.label)
                            }
                        }
                    } catch (failure: Exception) {
                        error = failure.message ?: l10n.getString(R.string.android_remaining_mistral_setup_sheet_111e307b)
                    } finally { busy = false }
                }
            }) { Text(stringResource(R.string.ui_test_and_discover_models_726e489)) }
            if (configured) TextButton(enabled = !busy, onClick = { confirmRemove = true }) {
                Text(stringResource(R.string.ui_remove_key_582d9a7))
            }
            verdict?.let { Text(it) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.ui_done_e9b450d)) }
        }
    }

    if (confirmRemove) AlertDialog(
        onDismissRequest = { confirmRemove = false },
        title = { Text(stringResource(R.string.ui_dynamic_remove_1_s_key_128fb5a, provider.label)) },
        text = { Text(stringResource(R.string.ui_dynamic_bots_using_1_s_may_become_unavailable_5983068, provider.label)) },
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
                        error = failure.message ?: l10n.getString(R.string.android_remaining_mistral_setup_sheet_494601c7)
                    } finally { busy = false }
                }
            }) { Text(stringResource(R.string.ui_remove_e963907)) }
        },
        dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.ui_cancel_77dfd21)) } },
    )
}
