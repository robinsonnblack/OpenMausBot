package com.openmausbot.companion.ui

import androidx.compose.ui.res.stringResource
import com.openmausbot.companion.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.core.ConfigStatus
import com.openmausbot.companion.core.VoiceProvider
import kotlinx.coroutines.launch

/** What Settings → Voice says and offers — the decisions of `ios/App/VoiceSettingsView.swift`. */
object VoiceSettingsRules {
    const val HEADER: String = "Calls and spoken replies"
    const val HEADER_FOOTER: String = "Calls use the voice set up on your computer. Each bot's voice is chosen on its profile."
    const val SYSTEM_NOTE: String = "This computer is using its built-in voices. Switch to ElevenLabs to use a key from here."
    const val KEY_FOOTER: String =
        "The key is checked and stored on your computer, never on this phone. Get one at elevenlabs.io → Settings → API Keys."
    const val SAVED: String = "Key saved. Calls are ready."
    const val REMOVED: String = "Key removed."

    fun statusText(status: ConfigStatus?): String? = status?.let { if (it.isTTSConfigured) "Ready" else "Not set up" }

    fun usesSystemVoices(status: ConfigStatus?): Boolean = status?.voiceProvider == VoiceProvider.SYSTEM

    /** The key section is drawn only under ElevenLabs; the built-in engine has no key to enter. */
    fun showsKeyField(status: ConfigStatus?): Boolean = !usesSystemVoices(status)

    fun saveLabel(status: ConfigStatus?): String = if (status?.isTTSConfigured == true) "Replace key" else "Save key"

    fun canSave(key: String, saving: Boolean): Boolean = !saving && key.isNotBlank()

    fun canRemove(status: ConfigStatus?, saving: Boolean): Boolean = !saving && status?.isTTSConfigured == true
}

/**
 * Voice: the key that lets a bot talk, set from the phone — the port of
 * `ios/App/VoiceSettingsView.swift`.
 *
 * The desktop has this under App Settings → Voice. The credential lives on
 * the computer, in the same config the desktop writes, so entering it here is
 * the same as entering it there: one PUT, the server checks the key against
 * ElevenLabs before saving, and the key never comes back in any response.
 * What the phone shows afterwards is only whether one is on file.
 */
@Composable
fun VoiceSettingsScreen(onBack: () -> Unit) {
    val session = LocalCompanion.current.session
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<ConfigStatus?>(null) }
    var key by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    val providerChanged = stringResource(R.string.android_voice_provider_changed)
    val keySaved = stringResource(R.string.android_voice_key_saved)
    val keyRemoved = stringResource(R.string.android_voice_key_removed)

    suspend fun refresh() {
        status = session.configStatus()
    }

    LaunchedEffect(Unit) { refresh() }

    fun run(action: suspend () -> String?, success: String?) {
        if (saving) return
        saving = true
        message = null
        scope.launch {

            try {
                val error = action()
                if (error != null) {
                    message = error
                    messageIsError = true
                    return@launch
                }
                message = success
                messageIsError = false
                refresh()
            } finally {
                saving = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ui_back_b52b36b))
            }
            Text(stringResource(R.string.android_voice_settings_title), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SettingsSection(stringResource(R.string.android_voice_settings_title),
                stringResource(R.string.android_voice_selection_help) +
                    if (VoiceSettingsRules.usesSystemVoices(status)) "\n\n" + stringResource(R.string.android_voice_system_help) else "") {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Status", fontSize = 15.sp, color = secondaryTint, modifier = Modifier.weight(1f))
                    val text = status?.let { stringResource(if (it.isTTSConfigured) R.string.android_voice_ready else R.string.android_voice_not_setup) }
                    if (text == null) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = text,
                            fontSize = 15.sp,
                            color = if (status?.isTTSConfigured == true) Color(0xFF009957) else secondaryTint,
                        )
                    }
                }
                if (VoiceSettingsRules.usesSystemVoices(status)) {
                    ActionRow(text = stringResource(R.string.android_voice_use_elevenlabs), enabled = !saving) {
                        run({ session.updateVoiceProvider("elevenlabs") }, success = providerChanged)
                    }
                }
            }

            if (VoiceSettingsRules.showsKeyField(status)) {
                SettingsSection("ElevenLabs", stringResource(R.string.android_voice_key_help)) {
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it; message = null },
                        label = { Text(stringResource(R.string.android_voice_api_key)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) {
                            ActionRow(
                                text = if (saving) stringResource(R.string.android_voice_saving) else stringResource(if (status?.isTTSConfigured == true) R.string.android_voice_replace_key else R.string.ui_save_key_f5216b3),
                                enabled = VoiceSettingsRules.canSave(key, saving),
                            ) {
                                val entered = key
                                run(
                                    {
                                        val error = session.updateVoiceKey(entered)
                                        if (error == null) key = ""
                                        error
                                    },
                                    success = keySaved,
                                )
                            }
                        }
                        if (saving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                    if (VoiceSettingsRules.canRemove(status, saving = false)) {
                        ActionRow(text = stringResource(R.string.ui_remove_key_582d9a7), enabled = !saving, destructive = true) {
                            run({ session.updateVoiceKey("") }, success = keyRemoved)
                        }
                    }
                    message?.let {
                        Text(
                            text = it,
                            fontSize = 13.sp,
                            color = if (messageIsError) Color(0xFFFF9800) else Color(0xFF009957),
                        )
                    }
                }
            } else {
                message?.let { Text(text = it, fontSize = 13.sp, color = Color(0xFFFF9800)) }
            }
        }
    }
}
