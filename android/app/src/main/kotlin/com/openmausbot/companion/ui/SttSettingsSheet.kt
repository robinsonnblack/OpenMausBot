package com.openmausbot.companion.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.openmausbot.companion.R
import com.openmausbot.companion.core.CompanionJson
import com.openmausbot.companion.dictation.*
import java.security.KeyPairGenerator
import java.security.spec.MGF1ParameterSpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

internal fun decryptSttImport(envelope: JsonObject, privateKey: java.security.PrivateKey): SttConfig {
    try {
        fun bytes(name: String) = Base64.getDecoder().decode(envelope.getValue(name).jsonPrimitive.content)
        val wrapped = bytes("wrappedKey"); val iv = bytes("iv"); val ciphertext = bytes("ciphertext")
        require(wrapped.size == 256 && iv.size == 12 && ciphertext.size in 16..65536)
        val rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        rsa.init(Cipher.DECRYPT_MODE, privateKey, OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT))
        val key = rsa.doFinal(wrapped)
        try {
            val aes = Cipher.getInstance("AES/GCM/NoPadding")
            aes.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            aes.updateAAD(envelope.getValue("requestId").jsonPrimitive.content.toByteArray())
            val plain = aes.doFinal(ciphertext)
            try { return CompanionJson.decodeFromString<SttConfig>(plain.toString(Charsets.UTF_8)) } finally { plain.fill(0) }
        } finally { key.fill(0) }
    } catch (_: Exception) { error("The encrypted STT import could not be verified. Try again.") }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SttSettingsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val session = LocalCompanion.current.session
    val store = remember { SttStore(context) }
    var initial by remember { mutableStateOf<SttConfig?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { try { initial = withContext(Dispatchers.IO) { store.load() } } catch (_: Exception) { failure = context.getString(R.string.stt_read_failed) } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
      Column(Modifier.fillMaxWidth().fillMaxHeight()) {
        val loaded = initial
        if(loaded == null) Column(Modifier.padding(20.dp)) { if(failure == null) CircularProgressIndicator() else Text(failure.orEmpty(), color = MaterialTheme.colorScheme.error) }
        else SttSettingsEditor(loaded, modifier = Modifier.weight(1f), onSave = { withContext(Dispatchers.IO) { store.save(it) } }, onImport = { current ->
            val pair = withContext(Dispatchers.IO) { KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair() }
            val envelope = session.importSttSettings(Base64.getEncoder().encodeToString(pair.public.encoded))
            val imported = withContext(Dispatchers.IO) { decryptSttImport(envelope, pair.private) }
            val merged = mergeSttImport(current, imported)
            withContext(Dispatchers.IO) { store.save(merged) }
            merged
        })
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.pairing_access_close)) }
      }
    }
}

@Composable
internal fun SttSettingsEditor(initial: SttConfig, onSave: suspend (SttConfig) -> Unit, onImport: suspend (SttConfig) -> SttConfig, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(initial) }
    var pauseInput by remember { mutableStateOf(TextFieldValue(initial.silenceMs.toString())) }
    var menu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var imported by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun change(value: SttConfig) { config = value; saved = false; imported = false; error = null }
    val provider = STT_PROVIDERS.first { it.id == config.provider }
    val profile = config.profiles[provider.id] ?: SttProfile(model = provider.model)
    fun profileChange(value: SttProfile) = change(config.copy(profiles = config.profiles + (provider.id to value)))
    Column(modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.stt_settings_title), style = MaterialTheme.typography.titleLarge)
            PermissionHelp(stringResource(R.string.stt_settings_help))
        }
        Box {
            OutlinedButton(enabled = !busy, onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) { Text(provider.name) }
            DropdownMenu(menu, { menu = false }) { STT_PROVIDERS.forEach { item -> DropdownMenuItem(text = { Text(item.name) }, onClick = { change(config.copy(provider = item.id)); menu = false }) } }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(stringResource(R.string.stt_stop_mode), modifier = Modifier.weight(1f))
            PermissionHelp(stringResource(R.string.stt_recording_options_help), stringResource(R.string.stt_stop_mode))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = config.stopMode == "manual", enabled = !busy, onClick = { change(config.copy(stopMode = "manual")) }, label = { Text(stringResource(R.string.stt_stop_manual)) })
            FilterChip(selected = config.stopMode == "silence", enabled = !busy, onClick = { change(config.copy(stopMode = "silence")) }, label = { Text(stringResource(R.string.stt_stop_silence)) })
        }
        if (config.stopMode == "silence") OutlinedTextField(pauseInput,
            { value -> if (value.text.all(Char::isDigit)) { val edited = value.text != pauseInput.text; pauseInput = value; if (edited) change(config.copy(silenceMs = value.text.toIntOrNull() ?: 0)) } }, label = { Text(stringResource(R.string.stt_pause_ms)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(stringResource(R.string.stt_after_action), modifier = Modifier.weight(1f))
            PermissionHelp(stringResource(R.string.stt_after_action_help), stringResource(R.string.stt_after_action))
        }
        Row(Modifier.testTag("stt-after-actions"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = config.afterAction == "insert", enabled = !busy, onClick = { change(config.copy(afterAction = "insert")) }, label = { Text(stringResource(R.string.stt_insert)) })
            FilterChip(selected = config.afterAction == "send", enabled = !busy, onClick = { change(config.copy(afterAction = "send")) }, label = { Text(stringResource(R.string.stt_send)) })
        }
        if(provider.id != "android") {
            OutlinedTextField(profile.key, { profileChange(profile.copy(key = it)) }, label = { Text(stringResource(R.string.stt_key)) },
                visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
            if(provider.id in listOf("azure", "compatible")) OutlinedTextField(profile.endpoint, { profileChange(profile.copy(endpoint = it)) },
                label = { Text(stringResource(R.string.stt_endpoint)) }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
            if(provider.id != "azure") OutlinedTextField(profile.model.ifBlank { provider.model }, { profileChange(profile.copy(model = it)) },
                label = { Text(stringResource(R.string.stt_model)) }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(config.language, { change(config.copy(language = it)) }, label = { Text(stringResource(R.string.stt_language)) },
                singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        }
      }
        error?.let { Text(sttErrorText(context, it), color = MaterialTheme.colorScheme.error) }
        Button(enabled = !busy && !saved, onClick = {
            busy = true; error = null
            scope.launch { try { onSave(config); saved = true } catch(c: CancellationException) { throw c } catch(f: Exception) { error = f.message ?: context.getString(R.string.stt_save_failed) } finally { busy = false } }
        }) { Text(stringResource(if (busy && !importing) R.string.stt_saving else if(saved) R.string.stt_saved else R.string.stt_save)) }
        OutlinedButton(enabled = !busy, onClick = {
            busy = true; importing = true; error = null
            scope.launch { try { config = onImport(config); pauseInput = TextFieldValue(config.silenceMs.toString()); imported = true; saved = true } catch(c: CancellationException) { throw c } catch(f: Exception) { error = f.message ?: context.getString(R.string.stt_import_failed) } finally { busy = false; importing = false } }
        }) { Text(stringResource(if (importing) R.string.stt_importing else if(imported) R.string.stt_imported else R.string.stt_import)) }
    }
}
