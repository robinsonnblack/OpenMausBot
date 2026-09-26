package com.openmausbot.companion.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.ImageAttachmentSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImageAttachmentSettingsSheet(onDismiss: () -> Unit) {
    val session = LocalCompanion.current.session
    var initial by remember { mutableStateOf<ImageAttachmentSettings?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(session) {
        try { initial = session.imageAttachmentSettings() }
        catch (failure: Exception) { error = failure.message }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val loaded = initial
        if (loaded != null) ImageAttachmentSettingsEditor(loaded) { requested ->
            val confirmed = session.updateImageAttachmentSettings(requested).imageAttachments
            check(confirmed == requested) { "The server did not confirm the values." }
        }
        else Column(Modifier.fillMaxWidth().padding(20.dp)) {
            if (error == null) CircularProgressIndicator() else Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
        }
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.pairing_access_close)) }
    }
}

@Composable
internal fun ImageAttachmentSettingsEditor(initial: ImageAttachmentSettings, onSave: suspend (ImageAttachmentSettings) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var count by remember(initial) { mutableStateOf(initial.maxImages.toString()) }
    var mb by remember(initial) { mutableStateOf((initial.maxTotalImageBytes / 1_000_000.0).toString()) }
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.image_attachment_settings_title), style = MaterialTheme.typography.titleLarge)
            PermissionHelp(stringResource(R.string.image_attachment_settings_help))
        }
        OutlinedTextField(count, { count = it; saved = false; error = null }, label = { Text(stringResource(R.string.image_attachment_settings_count)) }, singleLine = true,
            enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(mb, { mb = it; saved = false; error = null }, label = { Text(stringResource(R.string.image_attachment_settings_size)) }, singleLine = true,
            enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(enabled = !busy && !saved, onClick = {
            val number = count.toLongOrNull()
            val size = mb.replace(',', '.').toDoubleOrNull()
            val bytes = size?.times(1_000_000)
            if (number == null || number <= 0 || bytes == null || !bytes.isFinite() || bytes < 1 || bytes >= Long.MAX_VALUE.toDouble()) {
                error = context.getString(R.string.image_attachment_settings_invalid)
            } else {
                busy = true; error = null
                scope.launch {
                    try { onSave(ImageAttachmentSettings(number, kotlin.math.round(bytes).toLong())); saved = true }
                    catch (failure: Exception) { error = failure.message }
                    finally { busy = false }
                }
            }
        }) { Text(stringResource(if (busy) R.string.image_attachment_settings_saving else if (saved) R.string.image_attachment_settings_saved else R.string.image_attachment_settings_save)) }
    }
}
