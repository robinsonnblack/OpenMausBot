package com.openmausbot.companion.ui

import com.openmausbot.companion.R
import androidx.compose.ui.res.stringResource
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
import com.openmausbot.companion.core.BrowserProfile
import com.openmausbot.companion.core.Instance
import com.openmausbot.companion.core.ModelSelection
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray

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
    val connection by session.connection.collectAsState()
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
    var browserProfiles by remember { mutableStateOf<List<BrowserProfile>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var savedTemplate by remember { mutableStateOf(JsonObject(emptyMap())) }
    var profileText by remember { mutableStateOf("{}") }
    var memoryText by remember { mutableStateOf("{}") }
    var skillsText by remember { mutableStateOf("[]") }
    var routinesText by remember { mutableStateOf("[]") }
    var showingContent by remember { mutableStateOf(false) }
    var showingRawContent by remember { mutableStateOf(false) }
    val draftJson = remember { Json { prettyPrint = true } }
    val loadError = stringResource(R.string.android_new_bot_load_error)
    val createError = stringResource(R.string.android_new_bot_create_error)
    val jsonError = stringResource(R.string.android_new_bot_json_error)
    val memoryJsonError = stringResource(R.string.android_new_bot_memory_json_error)
    val skillsJsonError = stringResource(R.string.android_new_bot_skills_json_error)
    val routinesJsonError = stringResource(R.string.android_new_bot_routines_json_error)
    val skillDescription = stringResource(R.string.android_new_bot_skill_description)
    val skillInstructions = stringResource(R.string.android_new_bot_skill_instructions)
    val dailyCheck = stringResource(R.string.android_new_bot_daily_check)
    val routineTask = stringResource(R.string.android_new_bot_describe_task)

    LaunchedEffect(Unit) {
        try {
            val result = coroutineScope {
                val defaults = async { session.botCreationOptions() }
                val models = async { session.modelInstances() }
                defaults.await() to models.await()
            }
            val profile = result.first.defaults["profile"] as? JsonObject
            savedTemplate = result.first.defaults
            profileText = draftJson.encodeToString(JsonElement.serializer(), result.first.defaults["profile"] ?: JsonObject(emptyMap()))
            memoryText = draftJson.encodeToString(JsonElement.serializer(), result.first.defaults["memory"] ?: JsonObject(emptyMap()))
            skillsText = draftJson.encodeToString(JsonElement.serializer(), result.first.defaults["skills"] ?: JsonArray(emptyList()))
            routinesText = draftJson.encodeToString(JsonElement.serializer(), result.first.defaults["routines"] ?: JsonArray(emptyList()))
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
                browserProfile = string("browserProfile"),
            )
            selection = result.first.modelSelection
            instances = result.second
            try { browserProfiles = session.configStatus()?.browserProfiles.orEmpty() }
            catch (_: Exception) { /* Model-first creation still works without profile discovery. */ }
        } catch (failure: Exception) {
            error = failure.message ?: loadError
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
    val isAdmin = connection?.serverScopes?.contains("admin") == true

    fun updateArrayEntry(source: String, index: Int, field: String, value: JsonElement): String {
        val items = draftJson.parseToJsonElement(source).jsonArray.toMutableList()
        items[index] = JsonObject(items[index].jsonObject + (field to value))
        return draftJson.encodeToString(JsonElement.serializer(), JsonArray(items))
    }
    fun submit(acknowledge: Boolean) {
        val model = selection ?: return
        val creationTemplate = if (isAdmin) try {
            val profile = draftJson.parseToJsonElement(profileText).jsonObject
            val memory = draftJson.parseToJsonElement(memoryText).jsonObject
            val skills = draftJson.parseToJsonElement(skillsText).jsonArray
            val routines = draftJson.parseToJsonElement(routinesText).jsonArray
            JsonObject(savedTemplate + mapOf("profile" to profile, "memory" to memory,
                "skills" to skills, "routines" to routines))
        } catch (failure: Exception) {
            error = "$jsonError ${failure.message.orEmpty()}"
            return
        } else null
        saving = true
        error = null
        scope.launch {
            try {
                val bot = session.createBot(
                    name.trim(), title.trim(), description.trim(), model,
                    section.ifEmpty { null },
                    if (isAdmin) preferences else null,
                    acknowledge,
                    creationTemplate,
                )
                if (bot != null) onCreated(bot)
                else error = session.actionError ?: createError
            } catch (failure: Exception) {
                error = failure.message ?: createError
            } finally { saving = false }
        }
    }

    if (confirmingLocalAuto) AlertDialog(
        onDismissRequest = { confirmingLocalAuto = false },
        title = { Text(stringResource(R.string.ui_allow_automatic_pc_actions_3f3cbfd)) },
        text = { Text(stringResource(R.string.ui_this_bot_can_act_on_this_computer_without_51d302f)) },
        confirmButton = { TextButton(onClick = { confirmingLocalAuto = false; submit(true) }) { Text(stringResource(R.string.ui_create_with_auto_approval_4786fc0)) } },
        dismissButton = { TextButton(onClick = { confirmingLocalAuto = false }) { Text(stringResource(R.string.ui_cancel_77dfd21)) } },
    )

    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 690.dp)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.ui_new_bot_66d3c05))
            if (!loaded) CircularProgressIndicator()
            else {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(80) },
                    label = { Text(stringResource(R.string.ui_name_709a232)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = title, onValueChange = { title = it.take(200) },
                    label = { Text(stringResource(R.string.ui_role_or_title_53f2cc0)) }, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description, onValueChange = { description = it.take(2_000) },
                    label = { Text(stringResource(R.string.ui_description_55f8ebc)) }, minLines = 2, modifier = Modifier.fillMaxWidth(),
                )
                ChoicePicker(
                    label = stringResource(R.string.ui_team_2188872),
                    choices = listOf(VoiceChoice("", stringResource(R.string.android_new_bot_no_team), null, true)) +
                        state.sidebarSections.map { VoiceChoice(it.name, it.name, null, true) },
                    selected = section,
                    onSelect = { section = it },
                )
                ChoicePicker(
                    label = stringResource(R.string.ui_provider_7ceee3f),
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
                    label = stringResource(R.string.ui_model_68c2cc7),
                    choices = models.map { VoiceChoice(it.id, it.label, null, true) },
                    selected = selected?.model.orEmpty(),
                    enabled = available,
                    onSelect = { id -> selected?.let { selection = it.copy(model = id, effort = null) } },
                )
                val efforts = ModelRules.effortLevels(instance)
                if (efforts.isNotEmpty()) ChoicePicker(
                    label = stringResource(R.string.ui_reasoning_effort_cd32c0f),
                    choices = listOf(VoiceChoice("", stringResource(R.string.ui_model_default_effort), null, true)) +
                        efforts.map { VoiceChoice(it, ModelRules.effortLabel(it), null, true) },
                    selected = selected?.effort.orEmpty(),
                    onSelect = { effort -> selected?.let { selection = it.copy(effort = effort.ifEmpty { null }) } },
                )
                if (isAdmin) TextButton(onClick = { showingAdvanced = !showingAdvanced }) {
                    Text(stringResource(if (showingAdvanced) R.string.ui_hide_bot_settings else R.string.ui_bot_settings))
                }
                if (showingAdvanced && isAdmin) {
                    OutlinedTextField(
                        value = preferences.soul,
                        onValueChange = { preferences = preferences.copy(soul = it) },
                        label = { Text(stringResource(R.string.ui_bot_instructions_67fe721)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (preferences.soul.toByteArray(Charsets.UTF_8).size > 24_000) {
                        Text(stringResource(R.string.ui_bot_instructions_must_be_at_most_24_kb_a655cb6), color = MaterialTheme.colorScheme.error)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.ui_notifications_753a22b), modifier = Modifier.weight(1f))
                        Switch(checked = preferences.notifications, onCheckedChange = {
                            preferences = preferences.copy(notifications = it)
                        })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.ui_speak_replies_90b05ae), modifier = Modifier.weight(1f))
                        Switch(checked = preferences.speakReplies, onCheckedChange = {
                            preferences = preferences.copy(speakReplies = it)
                        })
                    }
                    ChoicePicker(
                        label = stringResource(R.string.ui_color_1d0c830),
                        choices = listOf("green", "blue", "red", "orange", "purple", "cyan", "pink", "yellow", "teal", "coral")
                            .map { VoiceChoice(it, it.replaceFirstChar(Char::uppercaseChar), null, true) },
                        selected = preferences.color,
                        onSelect = { preferences = preferences.copy(color = it) },
                    )
                    ChoicePicker(
                        label = stringResource(R.string.ui_computer_access_b090ead),
                        choices = listOf(VoiceChoice("", stringResource(R.string.ui_model_default_effort), null, true)) +
                            listOf("cloud", "vm", "local", "browser", "off")
                                .map { VoiceChoice(it, it.replaceFirstChar(Char::uppercaseChar), null, true) },
                        selected = preferences.computer.orEmpty(),
                        onSelect = { preferences = preferences.copy(computer = it.ifEmpty { null }) },
                    )
                    ChoicePicker(
                        label = stringResource(R.string.ui_browser_profile_d7d5c8f),
                        choices = listOf(
                            VoiceChoice("", stringResource(R.string.android_new_bot_own_browser), null, true),
                            VoiceChoice("guest", stringResource(R.string.ui_bot_temporary_browser), null, true),
                        ) + browserProfiles.map { VoiceChoice(it.id, it.name, null, true) },
                        selected = preferences.browserProfile.orEmpty(),
                        onSelect = { preferences = preferences.copy(browserProfile = it.ifEmpty { null }) },
                    )
                    ChoicePicker(
                        label = stringResource(R.string.ui_action_approval_d6cf31e),
                        choices = listOf(
                            VoiceChoice("ask", stringResource(R.string.android_new_bot_ask_actions), null, true),
                            VoiceChoice("auto", stringResource(R.string.android_new_bot_approve_automatically), null, true),
                        ),
                        selected = preferences.approvalMode,
                        onSelect = { preferences = preferences.copy(approvalMode = it) },
                    )
                    Text(stringResource(R.string.ui_full_and_custom_desktop_grants_must_be_con_9e2da95), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = preferences.cwd.orEmpty(),
                        onValueChange = { preferences = preferences.copy(cwd = it.take(4_096).ifBlank { null }) },
                        label = { Text(stringResource(R.string.ui_working_folder_on_computer_optional_702ecf4)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = preferences.voice,
                        onValueChange = { preferences = preferences.copy(voice = it.take(200)) },
                        label = { Text(stringResource(R.string.ui_voice_id_optional_b8dd8bb)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (isAdmin) TextButton(onClick = { showingContent = !showingContent }) {
                    Text(stringResource(if (showingContent) R.string.ui_hide_bot_content else R.string.ui_bot_content))
                }
                if (showingContent && isAdmin) {
                    Text(stringResource(R.string.ui_these_are_part_of_this_bot_s_draft_they_ar_ff27d04), style = MaterialTheme.typography.bodySmall)
                    val memoryFiles = runCatching { draftJson.parseToJsonElement(memoryText).jsonObject }.getOrNull()
                    if (memoryFiles != null) {
                        OutlinedTextField(
                            value = memoryFiles["MEMORY.md"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            onValueChange = { value ->
                                memoryText = draftJson.encodeToString(JsonElement.serializer(),
                                    JsonObject(memoryFiles + ("MEMORY.md" to JsonPrimitive(value))))
                            },
                            label = { Text(stringResource(R.string.ui_memory_index_1254180)) }, minLines = 3, modifier = Modifier.fillMaxWidth(),
                        )
                        memoryFiles.filterKeys { it != "MEMORY.md" }.forEach { (path, content) ->
                            OutlinedTextField(
                                value = content.jsonPrimitive.contentOrNull.orEmpty(),
                                onValueChange = { value ->
                                    memoryText = draftJson.encodeToString(JsonElement.serializer(),
                                        JsonObject(memoryFiles + (path to JsonPrimitive(value))))
                                },
                                label = { Text(path) }, minLines = 2, modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    TextButton(onClick = {
                        try {
                            val existing = draftJson.parseToJsonElement(memoryText).jsonObject
                            val path = generateSequence(1) { it + 1 }.map { "memory/topic$it.md" }
                                .first { it !in existing }
                            memoryText = draftJson.encodeToString(JsonElement.serializer(),
                                JsonObject(existing + (path to JsonPrimitive(""))))
                        } catch (_: Exception) { error = memoryJsonError }
                    }) { Text(stringResource(R.string.ui_add_memory_topic_3bf3be5)) }
                    val skillItems = runCatching { draftJson.parseToJsonElement(skillsText).jsonArray }.getOrNull()
                    skillItems?.forEachIndexed { index, item ->
                        val skill = item as? JsonObject ?: return@forEachIndexed
                        Text(stringResource(R.string.ui_new_bot_skill_name,
                            skill["name"]?.jsonPrimitive?.contentOrNull ?: index + 1))
                        OutlinedTextField(
                            value = skill["text"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            onValueChange = { skillsText = updateArrayEntry(skillsText, index, "text", JsonPrimitive(it)) },
                            label = { Text(stringResource(R.string.ui_skill_md_55b8417)) }, minLines = 4, modifier = Modifier.fillMaxWidth(),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.ui_enable_after_creation_63ce6fd), modifier = Modifier.weight(1f))
                            Switch(checked = skill["enabled"]?.jsonPrimitive?.booleanOrNull == true,
                                onCheckedChange = { skillsText = updateArrayEntry(skillsText, index, "enabled", JsonPrimitive(it)) })
                        }
                    }
                    TextButton(onClick = {
                        try {
                            val existing = draftJson.parseToJsonElement(skillsText).jsonArray
                            val name = generateSequence(1) { it + 1 }.map { "new-skill-$it" }
                                .first { candidate -> existing.none { (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull == candidate } }
                            val skill = JsonObject(mapOf(
                                "name" to JsonPrimitive(name), "description" to JsonPrimitive(skillDescription),
                                "source" to JsonPrimitive("android-draft"), "enabled" to JsonPrimitive(false),
                                "warnings" to JsonArray(emptyList()),
                                "text" to JsonPrimitive("---\nname: $name\ndescription: $skillDescription\n---\n$skillInstructions"),
                            ))
                            skillsText = draftJson.encodeToString(JsonElement.serializer(), JsonArray(existing + skill))
                        } catch (_: Exception) { error = skillsJsonError }
                    }) { Text(stringResource(R.string.ui_add_skill_d61f09b)) }
                    val routineItems = runCatching { draftJson.parseToJsonElement(routinesText).jsonArray }.getOrNull()
                    routineItems?.forEachIndexed { index, item ->
                        val routine = item as? JsonObject ?: return@forEachIndexed
                        Text(stringResource(R.string.ui_dynamic_routine_1_s_dbdf8d6, index + 1))
                        OutlinedTextField(
                            value = routine["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            onValueChange = { routinesText = updateArrayEntry(routinesText, index, "name", JsonPrimitive(it)) },
                            label = { Text(stringResource(R.string.ui_routine_name_9a8a0dd)) }, modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = routine["prompt"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            onValueChange = { routinesText = updateArrayEntry(routinesText, index, "prompt", JsonPrimitive(it)) },
                            label = { Text(stringResource(R.string.ui_routine_instructions_d1546d5)) }, minLines = 2, modifier = Modifier.fillMaxWidth(),
                        )
                        Text(stringResource(R.string.ui_new_bot_schedule_type,
                            routine["schedule"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull.orEmpty()),
                            style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.ui_enable_after_creation_63ce6fd), modifier = Modifier.weight(1f))
                            Switch(checked = routine["enabled"]?.jsonPrimitive?.booleanOrNull == true,
                                onCheckedChange = { routinesText = updateArrayEntry(routinesText, index, "enabled", JsonPrimitive(it)) })
                        }
                    }
                    TextButton(onClick = {
                        try {
                            val existing = draftJson.parseToJsonElement(routinesText).jsonArray
                            val routine = JsonObject(mapOf(
                                "name" to JsonPrimitive(dailyCheck), "prompt" to JsonPrimitive(routineTask),
                                "enabled" to JsonPrimitive(false),
                                "schedule" to JsonObject(mapOf("type" to JsonPrimitive("daily"),
                                    "time" to JsonPrimitive("09:00"),
                                    "weekdays" to JsonArray((1..5).map(::JsonPrimitive)))),
                            ))
                            routinesText = draftJson.encodeToString(JsonElement.serializer(), JsonArray(existing + routine))
                        } catch (_: Exception) { error = routinesJsonError }
                    }) { Text(stringResource(R.string.ui_add_daily_routine_3e412e7)) }
                    TextButton(onClick = { showingRawContent = !showingRawContent }) {
                        Text(stringResource(if (showingRawContent) R.string.ui_hide_template_editor else R.string.ui_edit_full_template))
                    }
                    if (showingRawContent) {
                        Text(stringResource(R.string.ui_advanced_editor_profile_fields_all_memory_8d81fcd), style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(value = profileText, onValueChange = { profileText = it },
                            label = { Text(stringResource(R.string.ui_profile_json_object_66c6703)) }, minLines = 4, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = memoryText, onValueChange = { memoryText = it },
                            label = { Text(stringResource(R.string.ui_memory_files_json_object_121129d)) }, minLines = 4, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = skillsText, onValueChange = { skillsText = it },
                            label = { Text(stringResource(R.string.ui_skills_json_array_41eb708)) }, minLines = 4, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = routinesText, onValueChange = { routinesText = it },
                            label = { Text(stringResource(R.string.ui_routines_json_array_a50d811)) }, minLines = 4, modifier = Modifier.fillMaxWidth())
                    }
                }
                if (!available) Text(stringResource(R.string.ui_choose_an_available_provider_66910a4), color = MaterialTheme.colorScheme.error)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(
                    enabled = !saving && name.trim().isNotEmpty() && available && modelOffered &&
                        (!isAdmin || preferences.soul.toByteArray(Charsets.UTF_8).size <= 24_000),
                    onClick = {
                        if (isAdmin && preferences.computer == "local" && preferences.approvalMode == "auto")
                            confirmingLocalAuto = true
                        else submit(false)
                    },
                ) { Text(stringResource(if (saving) R.string.ui_creating_bot else R.string.ui_create_bot)) }
            }
            TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.ui_cancel_77dfd21)) }
        }
    }
}
