package com.openmausbot.companion.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.OpenMausApp
import com.openmausbot.companion.R
import com.openmausbot.companion.audio.PhoneVoiceConfig
import com.openmausbot.companion.core.CompanionJson
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.security.KeyPairGenerator
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.*
import java.security.spec.MGF1ParameterSpec

internal fun decryptPhoneVoice(envelope: JsonObject, privateKey: java.security.PrivateKey): PhoneVoiceConfig {
    require(envelope.values.all { it is JsonPrimitive && it.isString && it.content.length <= 32768 })
    val rsa = Cipher.getInstance("RSA/ECB/OAEPPadding")
    rsa.init(Cipher.DECRYPT_MODE, privateKey, OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT))
    val key = rsa.doFinal(Base64.getDecoder().decode(envelope.getValue("wrappedKey").jsonPrimitive.content))
    try {
        val aes = Cipher.getInstance("AES/GCM/NoPadding")
        aes.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, Base64.getDecoder().decode(envelope.getValue("iv").jsonPrimitive.content)))
        aes.updateAAD(envelope.getValue("requestId").jsonPrimitive.content.toByteArray())
        val plaintext = aes.doFinal(Base64.getDecoder().decode(envelope.getValue("ciphertext").jsonPrimitive.content))
        try { return CompanionJson.decodeFromString<PhoneVoiceConfig>(plaintext.toString(Charsets.UTF_8)) } finally { plaintext.fill(0) }
    } finally { key.fill(0) }
}

@Composable internal fun PhoneVoiceSettings(onBack: () -> Unit) {
    val context = LocalContext.current
    val speech = (context.applicationContext as OpenMausApp).phoneSpeech
    val session = LocalCompanion.current.session
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf<PhoneVoiceConfig?>(null) }
    var error by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val sample = stringResource(R.string.phone_voice_sample)
    val saved = stringResource(R.string.phone_voice_saved)
    val speechError by speech.error.collectAsState()
    LaunchedEffect(Unit) { try { config = withContext(Dispatchers.IO) { speech.store.load() } } catch (e: Exception) { error = e.message.orEmpty() } }
    DisposableEffect(Unit) { onDispose { speech.stop() } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.ui_back_b52b36b)) }
        Text(stringResource(R.string.phone_voice_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.phone_voice_help))
        config?.let { cfg ->
            listOf("android" to stringResource(R.string.phone_voice_android), "elevenlabs" to "ElevenLabs", "fish" to "Fish Audio", "xai" to "Grok (xAI)", "chatterbox" to "Chatterbox").forEach { (id, name) ->
                Row { RadioButton(cfg.provider == id, enabled = !busy, onClick = { config = cfg.copy(provider = id, key = "", voice = "", model = "") }); Text(name, Modifier.padding(top = 12.dp)) }
            }
            if (cfg.provider != "android") {
                if (cfg.provider != "chatterbox") OutlinedTextField(cfg.key, { config = cfg.copy(key = it) }, enabled = !busy,
                    label = { Text(stringResource(R.string.phone_voice_key)) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(cfg.voice, { config = cfg.copy(voice = it) }, enabled = !busy,
                    label = { Text(stringResource(R.string.phone_voice_default)) }, modifier = Modifier.fillMaxWidth())
                if (cfg.provider == "chatterbox") OutlinedTextField(cfg.endpoint, { config = cfg.copy(endpoint = it) }, enabled = !busy,
                    label = { Text(stringResource(R.string.phone_voice_url)) }, modifier = Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = { scope.launch { busy = true; try { withContext(Dispatchers.IO) { speech.store.save(cfg) }; status = saved; error = "" } catch (e: Exception) { error = e.message.orEmpty() } finally { busy = false } } }) { Text(stringResource(R.string.phone_voice_save)) }
                TextButton(enabled = !busy, onClick = { scope.launch { busy = true; try { withContext(Dispatchers.IO) { speech.store.save(cfg) }; speech.speak(sample, cfg.voice) } catch (e: Exception) { error = e.message.orEmpty() } finally { busy = false } } }) { Text(stringResource(R.string.phone_voice_test)) }
            }
            TextButton(enabled = !busy, onClick = { scope.launch {
                busy = true
                try {
                    val pair = withContext(Dispatchers.IO) { KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair() }
                    val envelope = session.importTtsSettings(Base64.getEncoder().encodeToString(pair.public.encoded))
                    val imported = withContext(Dispatchers.IO) { decryptPhoneVoice(envelope, pair.private).also { speech.store.save(it) } }
                    config = imported; error = ""; status = saved
                } catch (e: Exception) { error = e.message.orEmpty() } finally { busy = false }
            } }) { Text(stringResource(R.string.phone_voice_import)) }
        }
        if (status.isNotBlank()) Text(status)
        if (error.isNotBlank() || speechError != null) Text(speechError ?: error, color = MaterialTheme.colorScheme.error)
    }
}

@Composable internal fun ReadAloudButton(text: String, voice: String?, messageId: String) {
    val context = LocalContext.current
    val speech = (context.applicationContext as OpenMausApp).phoneSpeech
    val scope = rememberCoroutineScope()
    val active by speech.messageId.collectAsState()
    val error by speech.error.collectAsState()
    var requested by remember(messageId) { mutableStateOf(false) }
    Column {
        TextButton(onClick = { if (active == messageId) speech.stop() else scope.launch { requested = true; speech.speak(text, voice, messageId) } }) {
            Text(stringResource(if (active == messageId) R.string.phone_voice_stop else R.string.phone_voice_read))
        }
        if (requested && active == null && error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
    }
}
