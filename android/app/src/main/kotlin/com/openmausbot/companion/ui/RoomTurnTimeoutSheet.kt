package com.openmausbot.companion.ui

import androidx.compose.ui.platform.LocalContext
import com.openmausbot.companion.R
import androidx.compose.ui.res.stringResource
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
    val l10n = LocalContext.current
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
                ?: throw IllegalStateException(l10n.getString(R.string.android_remaining_room_turn_timeout_sheet_e1c3373f))
            original = current
            minutes = current.toString()
        } catch (failure: Exception) {
            error = failure.message ?: l10n.getString(R.string.android_remaining_room_turn_timeout_sheet_8f4388ab)
        } finally {
            loading = false
        }
    }

    val parsed = minutes.toIntOrNull()
    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.ui_room_turn_timeout_0bf9d2d), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.ui_how_long_one_bot_may_take_to_finish_a_room_21a83ff))
            if (loading) CircularProgressIndicator()
            if (original != null) {
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter(Char::isDigit).take(4); error = null },
                    label = { Text(stringResource(R.string.ui_minutes_1_1_440_856bca8)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                )
            }
            if (parsed !in 1..1440 && original != null) {
                Text(stringResource(R.string.ui_enter_a_whole_number_from_1_to_1_440_5fcb58c), color = MaterialTheme.colorScheme.error)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(enabled = !loading && !saving && parsed in 1..1440, onClick = {
                scope.launch {
                    saving = true
                    error = null
                    try {
                        val current = session.configStatus()?.rooms?.turnTimeoutMinutes
                            ?: throw IllegalStateException(l10n.getString(R.string.android_remaining_room_turn_timeout_sheet_33ec7c78))
                        if (current != original) throw IllegalStateException(l10n.getString(R.string.android_remaining_room_turn_timeout_sheet_198f7645))
                        val saved = session.updateRoomTurnTimeout(requireNotNull(parsed)).rooms?.turnTimeoutMinutes
                        if (saved != parsed) throw IllegalStateException(l10n.getString(R.string.android_remaining_room_turn_timeout_sheet_9f2ea9b8))
                        onDismiss()
                    } catch (failure: Exception) {
                        error = failure.message ?: l10n.getString(R.string.android_remaining_room_turn_timeout_sheet_1e577f82)
                    } finally {
                        saving = false
                    }
                }
            }) { Text(stringResource(if (saving) R.string.ui_saving else R.string.ui_save_action)) }
            TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.ui_cancel_77dfd21)) }
        }
    }
}
