package com.openmausbot.companion.ui

import com.openmausbot.companion.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.core.Instance
import com.openmausbot.companion.core.ModelSelection
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Admin editor for the model inherited by future bots on the paired computer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DefaultBotModelSheet(onDismiss: () -> Unit) {
    val session = LocalCompanion.current.session
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<ModelSelection?>(null) }
    var selected by remember { mutableStateOf<ModelSelection?>(null) }
    var instances by remember { mutableStateOf<List<Instance>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val loaded = coroutineScope {
                val defaults = async { session.botCreationOptions() }
                val models = async { session.modelInstances() }
                defaults.await() to models.await()
            }
            original = loaded.first.modelSelection
            selected = original
            instances = loaded.second
        } catch (failure: Exception) {
            error = failure.message ?: "Could not load bot defaults."
        } finally { loading = false }
    }

    val current = selected
    val instance = instances.firstOrNull { it.instanceId == current?.instanceId }
    val available = instance?.snapshot?.isAvailable == true
    val models = ModelRules.modelChoices(instance, current?.model.orEmpty())
    val offered = current != null && (current.model == instance?.models?.defaultModel ||
        instance?.models?.options?.any { it.id == current.model } == true)

    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 550.dp)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.ui_default_model_for_new_bots_e479071))
            Text(stringResource(R.string.ui_future_bots_on_this_computer_inherit_this_cbf2108))
            if (loading) CircularProgressIndicator()
            else {
                ChoicePicker(
                    label = stringResource(R.string.ui_provider_7ceee3f),
                    choices = instances.filter { it.snapshot.isAvailable }.map {
                        VoiceChoice(it.instanceId, ModelRules.instanceLabel(it), null, true)
                    },
                    selected = current?.instanceId.orEmpty(),
                    onSelect = { id ->
                        instances.firstOrNull { it.instanceId == id }?.let {
                            selected = ModelSelection(id, it.models.defaultModel)
                        }
                    },
                )
                ChoicePicker(
                    label = stringResource(R.string.ui_model_68c2cc7),
                    choices = models.map { VoiceChoice(it.id, it.label, null, true) },
                    selected = current?.model.orEmpty(),
                    enabled = available,
                    onSelect = { id -> current?.let { selected = it.copy(model = id, effort = null) } },
                )
                val efforts = ModelRules.effortLevels(instance)
                if (efforts.isNotEmpty()) ChoicePicker(
                    label = stringResource(R.string.ui_reasoning_effort_cd32c0f),
                    choices = listOf(VoiceChoice("", "Default", null, true)) +
                        efforts.map { VoiceChoice(it, ModelRules.effortLabel(it), null, true) },
                    selected = current?.effort.orEmpty(),
                    onSelect = { effort -> current?.let { selected = it.copy(effort = effort.ifEmpty { null }) } },
                )
                if (!available) Text(stringResource(R.string.ui_choose_an_available_provider_66910a4), color = MaterialTheme.colorScheme.error)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(
                    enabled = !saving && current != null && current != original && available && offered,
                    onClick = {
                        val choice = selected ?: return@TextButton
                        scope.launch {
                            saving = true
                            try {
                                val confirmed = session.setDefaultBotModel(choice).modelSelection
                                if (confirmed != choice) error = "The computer did not confirm the selected model."
                                else onDismiss()
                            } catch (failure: Exception) {
                                error = failure.message ?: "Could not save the default model."
                            } finally { saving = false }
                        }
                    },
                ) { Text(stringResource(if (saving) R.string.ui_saving else R.string.ui_save_default)) }
            }
            TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.ui_cancel_77dfd21)) }
        }
    }
}
