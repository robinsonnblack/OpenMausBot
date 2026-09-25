package com.openmausbot.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import kotlinx.coroutines.launch

/** The same host-wide 1–1,440 minute limit exposed by desktop General settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoomTurnTimeoutSheet(onDismiss: () -> Unit) {
    val session = LocalCompanion.current.session
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<Int?>(null) }
    var minutes by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val current = session.configStatus()?.rooms?.turnTimeoutMinutes
                ?: throw IllegalStateException("Could not load the computer's room timeout.")
            original = current
            minutes = current.toString()
        } catch (failure: Exception) {
            error = failure.message ?: "Could not load the room timeout."
        } finally {
            loading = false
        }
    }

    val parsed = minutes.toIntOrNull()
    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Room turn timeout", style = MaterialTheme.typography.titleLarge)
            Text("How long one bot may take to finish a room turn before the computer stops it.")
            if (loading) CircularProgressIndicator()
            if (original != null) {
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter(Char::isDigit).take(4); error = null },
                    label = { Text("Minutes (1–1,440)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                )
            }
            if (parsed !in 1..1440 && original != null) {
                Text("Enter a whole number from 1 to 1,440.", color = MaterialTheme.colorScheme.error)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(enabled = !loading && !saving && parsed in 1..1440, onClick = {
                scope.launch {
                    saving = true
                    error = null
                    try {
                        val current = session.configStatus()?.rooms?.turnTimeoutMinutes
                            ?: throw IllegalStateException("Could not verify the current room timeout.")
                        if (current != original) throw IllegalStateException("The computer's setting changed. Reopen this screen to review it.")
                        val saved = session.updateRoomTurnTimeout(requireNotNull(parsed)).rooms?.turnTimeoutMinutes
                        if (saved != parsed) throw IllegalStateException("The computer did not confirm the saved timeout.")
                        onDismiss()
                    } catch (failure: Exception) {
                        error = failure.message ?: "Could not save the room timeout."
                    } finally {
                        saving = false
                    }
                }
            }) { Text(if (saving) "Saving…" else "Save") }
            TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") }
        }
    }
}
