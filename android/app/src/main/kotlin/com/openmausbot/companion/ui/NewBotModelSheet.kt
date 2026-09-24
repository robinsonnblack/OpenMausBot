package com.openmausbot.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.Instance
import com.openmausbot.companion.core.ModelSelection
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Create on the paired computer with a model chosen before the first turn. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewBotModelSheet(onCreated: (Bot) -> Unit, onDismiss: () -> Unit) {
    val session = LocalCompanion.current.session
    val state by session.state.collectAsState()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("") }
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
            name = result.first.suggestedName
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
                if (!available) Text("Choose an available provider.", color = MaterialTheme.colorScheme.error)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(
                    enabled = !saving && name.trim().isNotEmpty() && available && modelOffered,
                    onClick = {
                        val model = selection ?: return@TextButton
                        saving = true
                        scope.launch {
                            try {
                                val bot = session.createBot(
                                    name.trim(), title.trim(), description.trim(), model,
                                    section.ifEmpty { null },
                                )
                                if (bot != null) onCreated(bot)
                                else error = session.actionError ?: "Could not create the bot."
                            } catch (failure: Exception) {
                                error = failure.message ?: "Could not create the bot."
                            } finally { saving = false }
                        }
                    },
                ) { Text(if (saving) "Creating…" else "Create bot") }
            }
            TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") }
        }
    }
}
