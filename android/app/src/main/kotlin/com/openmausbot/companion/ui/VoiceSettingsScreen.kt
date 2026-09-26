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
fun VoiceSettingsScreen(onBack: () -> Unit) { PhoneVoiceSettings(onBack) }
