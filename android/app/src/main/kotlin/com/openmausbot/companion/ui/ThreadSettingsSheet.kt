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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.core.ThreadSettings
import kotlinx.coroutines.launch

/** Settings run on the paired computer and are editable only with an admin pairing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThreadSettingsSheet(onDismiss: () -> Unit) {
    val l10n = LocalContext.current
    val session = LocalCompanion.current.session
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<ThreadSettings?>(null) }
    var concurrent by remember { mutableStateOf("") }
    var capKiB by remember { mutableStateOf("") }
    var retentionDays by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val current = session.configStatus()?.threads
                ?: throw IllegalStateException(l10n.getString(R.string.android_remaining_thread_settings_sheet_0f645f53))
            original = current
            concurrent = current.maxConcurrentPerBot.toString()
            capKiB = current.eventLogMaxBytes?.div(1024)?.toString().orEmpty()
            retentionDays = current.eventLogRetentionDays?.toString().orEmpty()
        } catch (failure: Exception) {
            error = failure.message ?: l10n.getString(R.string.android_remaining_thread_settings_sheet_8bf0fe90)
        } finally { loading = false }
    }

    val concurrentValue = concurrent.toIntOrNull()
    val capValue = if (capKiB.isBlank()) null else capKiB.toLongOrNull()
    val daysValue = if (retentionDays.isBlank()) null else retentionDays.toIntOrNull()
    val valid = concurrentValue in 1..10 &&
        (capKiB.isBlank() || capValue in 256L..4_194_304L) &&
        (retentionDays.isBlank() || daysValue in 1..3650)

    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 690.dp)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.ui_thread_settings_9ab5123), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.ui_these_settings_apply_to_the_paired_compute_ab7235e),
                style = MaterialTheme.typography.bodySmall)
            if (loading) CircularProgressIndicator()
            if (original != null) {
                OutlinedTextField(
                    value = concurrent, onValueChange = { concurrent = it.filter(Char::isDigit).take(2); error = null },
                    label = { Text(stringResource(R.string.ui_parallel_tasks_per_bot_1_10_7a16634)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = capKiB, onValueChange = { capKiB = it.filter(Char::isDigit).take(7); error = null },
                    label = { Text(stringResource(R.string.ui_event_log_cap_in_kib_optional_8815723)) },
                    supportingText = { Text(stringResource(R.string.ui_leave_empty_to_keep_logs_without_a_size_ca_401ba8f)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = retentionDays, onValueChange = { retentionDays = it.filter(Char::isDigit).take(4); error = null },
                    label = { Text(stringResource(R.string.ui_closed_thread_log_retention_in_days_option_c38fb4b)) },
                    supportingText = { Text(stringResource(R.string.ui_leave_empty_to_retain_logs_indefinitely_c82c98d)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!valid) Text(stringResource(R.string.ui_enter_1_10_tasks_a_cap_of_256_4_194_304_ki_c966fd5),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(enabled = !loading && !saving && valid && original != null, onClick = {
                scope.launch {
                    saving = true
                    error = null
                    try {
                        val current = session.configStatus()?.threads
                            ?: throw IllegalStateException(l10n.getString(R.string.android_remaining_thread_settings_sheet_e940023b))
                        if (current != original) throw IllegalStateException(l10n.getString(R.string.android_remaining_thread_settings_sheet_1a4d7e5d))
                        val requested = ThreadSettings(concurrentValue!!, capValue?.times(1024), daysValue)
                        val saved = session.updateThreadSettings(requested).threads
                        if (saved != requested) throw IllegalStateException(l10n.getString(R.string.android_remaining_thread_settings_sheet_c15f72bd))
                        onDismiss()
                    } catch (failure: Exception) {
                        error = failure.message ?: l10n.getString(R.string.android_remaining_thread_settings_sheet_6f0e4e94)
                    } finally { saving = false }
                }
            }) { Text(stringResource(if (saving) R.string.ui_saving else R.string.ui_save_action)) }
            TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.ui_cancel_77dfd21)) }
        }
    }
}
