package com.openmausbot.companion.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.core.Bot
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun TransferSettingsSheet(bot: Bot, onDismiss: () -> Unit) {
    val session = LocalCompanion.current.session
    val state by session.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    fun label(key: String): String {
        val id = context.resources.getIdentifier(key.replace('.', '_').lowercase(), "string", context.packageName)
        return if (id != 0) context.getString(id) else key
    }
    var source by remember(bot.id) { mutableStateOf<JsonObject?>(null) }
    var fields by remember(bot.id) { mutableStateOf(setOf<String>()) }
    var targets by remember(bot.id) { mutableStateOf(setOf<String>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var done by remember { mutableStateOf("") }
    var confirmed by remember { mutableStateOf<JsonObject?>(null) }
    suspend fun load() {
        try { source = session.transferSettings(bot.id); error = "" }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message.orEmpty() }
    }
    LaunchedEffect(bot.id) { load() }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label("transfer.title"), style = MaterialTheme.typography.titleLarge)
            Text(label("transfer.description"))
            Text(label("transfer.targets"), style = MaterialTheme.typography.titleMedium)
            state.bots.filter { it.id != bot.id }.forEach { candidate ->
                Row { Checkbox(candidate.id in targets, enabled = !busy, onCheckedChange = { targets = if (it) targets + candidate.id else targets - candidate.id }); Text(candidate.name, Modifier.padding(top = 12.dp)) }
            }
            Text(label("transfer.fields"), style = MaterialTheme.typography.titleMedium)
            source?.keys?.forEach { field ->
                Row { Checkbox(field in fields, enabled = !busy, onCheckedChange = { fields = if (it) fields + field else fields - field }); Text(label("transfer.field.$field"), Modifier.padding(top = 12.dp)) }
            }
            Button(enabled = !busy && fields.isNotEmpty() && targets.isNotEmpty(), onClick = {
                scope.launch {
                    busy = true
                    try {
                        val fresh = session.transferSettings(bot.id)
                        confirmed = buildJsonObject {
                            fields.forEach { put(it, fresh[it] ?: JsonNull) }
                            if ("approvalMode" in fields) put("confirmFullAccess", fresh["approvalMode"]?.jsonPrimitive?.content == "full")
                            if (fields.any { it in listOf("computer", "approvalMode", "modelSelection", "alwaysAllow") }) put("acknowledgeLocalAuto", true)
                            if ("peers" in fields || "managedSections" in fields) put("acknowledgePeerScope", true)
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.message.orEmpty() }
                    finally { busy = false }
                }
            }) { Text(label("transfer.review")) }
            if (error.isNotEmpty()) { Text(error, color = MaterialTheme.colorScheme.error); TextButton(onClick = { scope.launch { load() } }) { Text(label("transfer.review")) } }
            if (done.isNotEmpty()) Text(done)
            TextButton(enabled = !busy, onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.openmausbot.companion.R.string.pairing_access_close)) }
        }
    }
    confirmed?.let { patch ->
        AlertDialog(onDismissRequest = { if (!busy) confirmed = null }, title = { Text(label("transfer.title")) },
            text = { Text(label("transfer.confirm").replace("{bots}", state.bots.filter { it.id in targets }.joinToString { it.name }).replace("{fields}", fields.joinToString("; ") { label("transfer.field.$it") + ": " + patch[it].toString() })) },
            dismissButton = { TextButton(enabled = !busy, onClick = { confirmed = null }) { Text(androidx.compose.ui.res.stringResource(com.openmausbot.companion.R.string.pairing_access_close)) } },
            confirmButton = { TextButton(enabled = !busy, onClick = { scope.launch {
                busy = true; val failures = mutableListOf<String>(); val completed = mutableSetOf<String>()
                try {
                    for (id in targets) try { session.applyTransferSettings(id, patch); completed += id }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { failures += "${state.bots.find { it.id == id }?.name}: ${e.message}" }
                    targets -= completed; error = failures.joinToString("\n")
                    done = label("transfer.completed").replace("{count}", completed.size.toString()); confirmed = null
                } finally { busy = false }
            } }) { Text(label("transfer.apply")) } })
    }
}
