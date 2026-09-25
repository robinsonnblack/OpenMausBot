package com.openmausbot.companion.ui

import com.openmausbot.companion.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.storage.ChatPreferences

private val names = mapOf(
    "system" to "System default", "midnight" to "Midnight", "atelier" to "Atelier",
    "foundry" to "Foundry", "lagoon" to "Lagoon", "graphite" to "Graphite",
    "linen" to "Linen", "dusk" to "Dusk", "daylight" to "Daylight",
    "chatgpt" to "ChatGPT", "custom" to "Custom",
)

/** Every color role from the desktop stylesheet is editable on the phone too. */
@Composable
fun ThemeEditor(preferences: ChatPreferences, onDismiss: () -> Unit) {
    val selected by preferences.themeId.collectAsState()
    val saved by preferences.customColors.collectAsState()
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(saved.ifEmpty { PresetThemes.colors.getValue("chatgpt") + ("chat-layout" to "chatgpt") }) }
    val valid = PresetThemes.colorRoles.all { role ->
        val value = draft[role].orEmpty()
        value == "transparent" || value.matches(Regex("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?"))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (editing) R.string.ui_custom_theme_title else R.string.ui_appearance_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!editing) {
                    Text(stringResource(R.string.ui_choose_a_look_for_this_phone_your_computer_43e0f76))
                    (listOf("system") + PresetThemes.colors.keys + "custom").forEach { id ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selected == id, onClick = {
                                if (id == "custom") {
                                    draft = saved.ifEmpty { PresetThemes.colors.getValue("chatgpt") + ("chat-layout" to "chatgpt") }
                                    editing = true
                                } else {
                                    preferences.setTheme(id)
                                    onDismiss()
                                }
                            })
                            Text(names[id] ?: id)
                        }
                    }
                    TextButton(onClick = { editing = true }) { Text(stringResource(R.string.ui_edit_custom_colors_c23f59a)) }
                } else {
                    Text(stringResource(R.string.ui_start_from_a_preset_then_change_any_color_b788e2a))
                    PresetThemes.colors.keys.forEach { id ->
                        TextButton(onClick = { draft = PresetThemes.colors.getValue(id) + ("chat-layout" to if (id == "chatgpt") "chatgpt" else "standard") }) {
                            Text(stringResource(R.string.ui_dynamic_copy_1_s_colors_825c9ef, names[id] ?: id))
                        }
                    }
                    TextButton(onClick = {
                        draft = draft + ("chat-layout" to if (draft["chat-layout"] == "chatgpt") "standard" else "chatgpt")
                    }) { Text(stringResource(
                        R.string.ui_theme_chat_layout,
                        if (draft["chat-layout"] == "chatgpt") "ChatGPT" else stringResource(R.string.ui_theme_standard),
                    )) }
                    PresetThemes.colorRoles.forEach { role ->
                        val value = draft[role].orEmpty()
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(28.dp).background(
                                if (value == "transparent") MaterialTheme.colorScheme.surface
                                else runCatching { themeColor(value) }.getOrDefault(MaterialTheme.colorScheme.error),
                            ))
                            OutlinedTextField(
                                value = value,
                                onValueChange = { draft = draft + (role to it) },
                                label = { Text(role.replace('-', ' ')) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    if (!valid) Text(stringResource(R.string.ui_correct_invalid_colors_before_saving_a1c9f07), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            if (editing) TextButton(enabled = valid, onClick = {
                preferences.setCustomColors(draft)
                onDismiss()
            }) { Text(stringResource(R.string.ui_save_and_use_dd8b341)) }
            else TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_done_e9b450d)) }
        },
        dismissButton = {
            if (editing) TextButton(onClick = { editing = false }) { Text(stringResource(R.string.ui_back_b52b36b)) }
            else TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel_77dfd21)) }
        },
    )
}
