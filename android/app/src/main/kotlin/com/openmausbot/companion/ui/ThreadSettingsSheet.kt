package com.openmausbot.companion.ui

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
                ?: throw IllegalStateException("Could not load the computer's thread settings.")
            original = current
            concurrent = current.maxConcurrentPerBot.toString()
            capKiB = current.eventLogMaxBytes?.div(1024)?.toString().orEmpty()
            retentionDays = current.eventLogRetentionDays?.toString().orEmpty()
        } catch (failure: Exception) {
            error = failure.message ?: "Could not load thread settings."
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
            Text("Thread settings", style = MaterialTheme.typography.titleLarge)
            Text("These settings apply to the paired computer, not just this phone.",
                style = MaterialTheme.typography.bodySmall)
            if (loading) CircularProgressIndicator()
            if (original != null) {
                OutlinedTextField(
                    value = concurrent, onValueChange = { concurrent = it.filter(Char::isDigit).take(2); error = null },
                    label = { Text("Parallel tasks per bot (1–10)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = capKiB, onValueChange = { capKiB = it.filter(Char::isDigit).take(7); error = null },
                    label = { Text("Event-log cap in KiB (optional)") },
                    supportingText = { Text("Leave empty to keep logs without a size cap. Minimum 256 KiB.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = retentionDays, onValueChange = { retentionDays = it.filter(Char::isDigit).take(4); error = null },
                    label = { Text("Closed-thread log retention in days (optional)") },
                    supportingText = { Text("Leave empty to retain logs indefinitely.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!valid) Text("Enter 1–10 tasks, a cap of 256–4,194,304 KiB, and 1–3650 days or leave optional fields empty.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(enabled = !loading && !saving && valid && original != null, onClick = {
                scope.launch {
                    saving = true
                    error = null
                    try {
                        val current = session.configStatus()?.threads
                            ?: throw IllegalStateException("Could not verify the current thread settings.")
                        if (current != original) throw IllegalStateException("The computer's settings changed. Reopen this screen to review them.")
                        val requested = ThreadSettings(concurrentValue!!, capValue?.times(1024), daysValue)
                        val saved = session.updateThreadSettings(requested).threads
                        if (saved != requested) throw IllegalStateException("The computer did not confirm the saved settings.")
                        onDismiss()
                    } catch (failure: Exception) {
                        error = failure.message ?: "Could not save thread settings."
                    } finally { saving = false }
                }
            }) { Text(if (saving) "Saving…" else "Save") }
            TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") }
        }
    }
}
