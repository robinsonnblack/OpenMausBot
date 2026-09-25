package com.openmausbot.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.BotCreationPreferences
import com.openmausbot.companion.core.Instance
import com.openmausbot.companion.core.ModelSelection
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Create on the paired computer with a model chosen before the first turn. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewBotModelSheet(
    onCreated: (Bot) -> Unit,
    onDismiss: () -> Unit,
    initialSection: String? = null,
) {
    val session = LocalCompanion.current.session
    val state by session.state.collectAsState()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var section by remember(initialSection) { mutableStateOf(initialSection.orEmpty()) }
    var preferences by remember { mutableStateOf(BotCreationPreferences()) }
    var showingAdvanced by remember { mutableStateOf(false) }
    var confirmingLocalAuto by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<ModelSelection?>(null) }
    var instances by remember { mutableStateOf<List<Instance>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val result = coroutineScope {
                val defaults = async { session.botCreationOptions() }
                val models = async { session.modelInstances() }
                defaults.await() to models.await()
            }
            val profile = result.first.defaults["profile"] as? JsonObject
            fun string(key: String): String? = profile?.get(key)?.jsonPrimitive?.contentOrNull
            fun bool(key: String): Boolean? = profile?.get(key)?.jsonPrimitive?.booleanOrNull
            name = string("name")?.takeIf(String::isNotBlank) ?: result.first.suggestedName
            title = string("title").orEmpty()
            description = string("description").orEmpty()
            if (initialSection == null) section = string("section").orEmpty()
            preferences = BotCreationPreferences(
                soul = string("soul").orEmpty(),
                notifications = bool("notifications") ?: true,
                speakReplies = bool("speakReplies") ?: false,
                computer = string("computer"),
                approvalMode = string("approvalMode")?.takeIf { it == "auto" } ?: "ask",
                cwd = string("cwd"),
                voice = string("voice").orEmpty(),
                color = string("color") ?: "green",
            )
            selection = result.first.modelSelection
            instances = result.second
        } catch (failure: Exception) {
            error = failure.message ?: "Could not load bot creation settings."
        } finally { loaded = true }
    }

    val selected = selection
    val instance = instances.firstOrNull { it.instanceId == selected?.instanceId }
    val available = instance?.snapshot?.isAvailable == true
    val models = ModelRules.modelChoices(instance, selected?.model.orEmpty())
    val modelOffered = selected != null && (
        selected.model == instance?.models?.defaultModel ||
            instance?.models?.options?.any { it.id == selected.model } == true
    )
    fun submit(acknowledge: Boolean) {
        val model = selection ?: return
        saving = true
        error = null
        scope.launch {
            try {
                val bot = session.createBot(
                    name.trim(), title.trim(), description.trim(), model,
                    section.ifEmpty { null }, preferences, acknowledge,
                )
                if (bot != null) onCreated(bot)
                else error = session.actionError ?: "Could not create the bot."
            } catch (failure: Exception) {
                error = failure.message ?: "Could not create the bot."
            } finally { saving = false }
        }
    }

    if (confirmingLocalAuto) AlertDialog(
        onDismissRequest = { confirmingLocalAuto = false },
        title = { Text("Allow automatic PC actions?") },
        text = { Text("This bot can act on this computer without asking for each action. Only enable this for a bot you trust.") },
        confirmButton = { TextButton(onClick = { confirmingLocalAuto = false; submit(true) }) { Text("Create with auto approval") } },
        dismissButton = { TextButton(onClick = { confirmingLocalAuto = false }) { Text("Cancel") } },
    )

    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 690.dp)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("New bot")
            if (!loaded) CircularProgressIndicator()
            else {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(80) },
                    label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = title, onValueChange = { title = it.take(200) },
                    label = { Text("Role or title") }, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description, onValueChange = { description = it.take(2_000) },
                    label = { Text("Description") }, minLines = 2, modifier = Modifier.fillMaxWidth(),
                )
                ChoicePicker(
                    label = "Team",
                    choices = listOf(VoiceChoice("", "No team", null, true)) +
                        state.sidebarSections.map { VoiceChoice(it.name, it.name, null, true) },
                    selected = section,
                    onSelect = { section = it },
                )
                ChoicePicker(
                    label = "Provider",
                    choices = instances.filter { it.snapshot.isAvailable }.map {
                        VoiceChoice(it.instanceId, ModelRules.instanceLabel(it), null, true)
                    },
                    selected = selected?.instanceId.orEmpty(),
                    onSelect = { id ->
                        instances.firstOrNull { it.instanceId == id }?.let { provider ->
                            selection = ModelSelection(id, provider.models.defaultModel)
                        }
                    },
                )
                ChoicePicker(
                    label = "Model",
                    choices = models.map { VoiceChoice(it.id, it.label, null, true) },
                    selected = selected?.model.orEmpty(),
                    enabled = available,
                    onSelect = { id -> selected?.let { selection = it.copy(model = id, effort = null) } },
                )
                val efforts = ModelRules.effortLevels(instance)
                if (efforts.isNotEmpty()) ChoicePicker(
                    label = "Reasoning effort",
                    choices = listOf(VoiceChoice("", "Default", null, true)) +
                        efforts.map { VoiceChoice(it, ModelRules.effortLabel(it), null, true) },
                    selected = selected?.effort.orEmpty(),
                    onSelect = { effort -> selected?.let { selection = it.copy(effort = effort.ifEmpty { null }) } },
                )
                TextButton(onClick = { showingAdvanced = !showingAdvanced }) {
                    Text(if (showingAdvanced) "Hide bot settings" else "Bot settings")
                }
                if (showingAdvanced) {
                    OutlinedTextField(
                        value = preferences.soul,
                        onValueChange = { preferences = preferences.copy(soul = it) },
                        label = { Text("Bot instructions") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (preferences.soul.toByteArray(Charsets.UTF_8).size > 24_000) {
                        Text("Bot instructions must be at most 24 KB.", color = MaterialTheme.colorScheme.error)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Notifications", modifier = Modifier.weight(1f))
                        Switch(checked = preferences.notifications, onCheckedChange = {
                            preferences = preferences.copy(notifications = it)
                        })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Speak replies", modifier = Modifier.weight(1f))
                        Switch(checked = preferences.speakReplies, onCheckedChange = {
                            preferences = preferences.copy(speakReplies = it)
                        })
                    }
                    ChoicePicker(
                        label = "Color",
                        choices = listOf("green", "blue", "red", "orange", "purple", "cyan", "pink", "yellow", "teal", "coral")
                            .map { VoiceChoice(it, it.replaceFirstChar(Char::uppercaseChar), null, true) },
                        selected = preferences.color,
                        onSelect = { preferences = preferences.copy(color = it) },
                    )
                    ChoicePicker(
                        label = "Computer access",
                        choices = listOf(VoiceChoice("", "Default", null, true)) +
                            listOf("cloud", "vm", "local", "browser", "off")
                                .map { VoiceChoice(it, it.replaceFirstChar(Char::uppercaseChar), null, true) },
                        selected = preferences.computer.orEmpty(),
                        onSelect = { preferences = preferences.copy(computer = it.ifEmpty { null }) },
                    )
                    ChoicePicker(
                        label = "Action approval",
                        choices = listOf(
                            VoiceChoice("ask", "Ask before actions", null, true),
                            VoiceChoice("auto", "Approve automatically", null, true),
                        ),
                        selected = preferences.approvalMode,
                        onSelect = { preferences = preferences.copy(approvalMode = it) },
                    )
                    Text("Full and custom desktop grants must be configured on the computer.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = preferences.cwd.orEmpty(),
                        onValueChange = { preferences = preferences.copy(cwd = it.take(4_096).ifBlank { null }) },
                        label = { Text("Working folder on computer (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = preferences.voice,
                        onValueChange = { preferences = preferences.copy(voice = it.take(200)) },
                        label = { Text("Voice ID (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (!available) Text("Choose an available provider.", color = MaterialTheme.colorScheme.error)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(
                    enabled = !saving && name.trim().isNotEmpty() && available && modelOffered &&
                        preferences.soul.toByteArray(Charsets.UTF_8).size <= 24_000,
                    onClick = {
                        if (preferences.computer == "local" && preferences.approvalMode == "auto")
                            confirmingLocalAuto = true
                        else submit(false)
                    },
                ) { Text(if (saving) "Creating…" else "Create bot") }
            }
            TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") }
        }
    }
}
