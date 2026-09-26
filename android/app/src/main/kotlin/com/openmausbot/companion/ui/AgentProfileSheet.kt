package com.openmausbot.companion.ui

import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.openmausbot.companion.R
import com.openmausbot.companion.avatar.AvatarImagePicker
import com.openmausbot.companion.avatar.AvatarImageRules
import com.openmausbot.companion.avatar.PreparedAvatar
import com.openmausbot.companion.core.AvatarCrop
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.BotWebhook
import com.openmausbot.companion.core.summarizeBotUsage
import com.openmausbot.companion.core.forTask
import com.openmausbot.companion.core.BotProfilePatch
import com.openmausbot.companion.core.ConfigStatus
import com.openmausbot.companion.core.Instance
import com.openmausbot.companion.core.ModelSelection
import com.openmausbot.companion.core.McpServerSummary
import com.openmausbot.companion.core.Voice
import com.openmausbot.companion.core.VoiceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The paired-safe subset of bot settings — the port of
 * `ios/App/AgentProfileView.swift`.
 *
 * The model, identity, avatar, notifications and the renderer-neutral voice
 * choice. Shared provider keys stay on the computer: this sheet sees the model
 * catalog and configured / not configured, and offers no field that could
 * carry a key.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AgentProfileSheet(bot: Bot, onDismiss: () -> Unit, onOpenOverview: (String) -> Unit) {
    val environment = LocalCompanion.current
    val session = environment.session
    val state by session.state.collectAsState()
    val connection by session.connection.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = environment.voicePreview
    val deleteBotError = stringResource(R.string.android_profile_delete_bot_error)
    val loadMcpError = stringResource(R.string.android_profile_load_mcp_error)
    val changeMcpError = stringResource(R.string.android_profile_change_mcp_error)
    val resetMcpError = stringResource(R.string.android_profile_reset_mcp_error)

    // The record the sheet was opened on, so the form has an origin even after
    // the fleet drops the agent; `current` is what every action is applied to.
    val opened = remember { bot }
    val current = state.bot(opened.id) ?: opened
    val currentTask = current.forTask(opened.threadId)
    val currentTaskRecord = current.tasks?.firstOrNull { it.threadId == opened.threadId }

    var form by rememberSaveable(stateSaver = ProfileFormSaver) {
        mutableStateOf(ProfileForm.of(opened))
    }
    var soulDraft by rememberSaveable(opened.id) { mutableStateOf(opened.soul.orEmpty()) }
    var soulBaseline by rememberSaveable(opened.id) { mutableStateOf(opened.soul.orEmpty()) }
    var savingSoul by remember { mutableStateOf(false) }
    val soulBytes = soulDraft.toByteArray(Charsets.UTF_8).size
    val soulConflict = soulDraft != soulBaseline && current.soul.orEmpty() != soulBaseline
    LaunchedEffect(current.soul) {
        if (soulDraft == soulBaseline) {
            soulDraft = current.soul.orEmpty()
            soulBaseline = soulDraft
        }
    }
    var baseline by rememberSaveable(stateSaver = ProfileFormSaver) {
        mutableStateOf(ProfileForm.of(opened))
    }
    var prompt by rememberSaveable { mutableStateOf("") }
    var voices by remember { mutableStateOf<List<Voice>>(emptyList()) }
    var config by remember { mutableStateOf<ConfigStatus?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var showingHistory by rememberSaveable(opened.id) { mutableStateOf(false) }
    var showingMemory by rememberSaveable(opened.id) { mutableStateOf(false) }
    var showingTransfer by rememberSaveable(opened.id) { mutableStateOf(false) }
    var showingSkills by rememberSaveable(opened.id) { mutableStateOf(false) }
    var showingAccessDetails by rememberSaveable(opened.id) { mutableStateOf(false) }
    var showingBotUsage by rememberSaveable(opened.id) { mutableStateOf(false) }
    var showingCommandRules by rememberSaveable(opened.id) { mutableStateOf(false) }
    var accessWebhooks by remember(opened.id) { mutableStateOf<List<BotWebhook>?>(null) }
    var loadingAccessWebhooks by remember(opened.id) { mutableStateOf(false) }
    var mcpServers by remember(opened.id) { mutableStateOf<List<McpServerSummary>?>(null) }
    var mcpError by remember(opened.id) { mutableStateOf<String?>(null) }
    var choosingTaskSurface by remember(opened.threadId) { mutableStateOf(false) }
    var choosingBotComputer by remember(opened.id) { mutableStateOf(false) }
    var choosingBrowserProfile by remember(opened.id) { mutableStateOf(false) }
    var confirmingLocalAuto by remember(opened.id) { mutableStateOf(false) }
    var choosingSafeApproval by remember(opened.id) { mutableStateOf(false) }
    var confirmingAutoOnComputer by remember(opened.id) { mutableStateOf(false) }
    var workingFolderDraft by rememberSaveable(opened.id) { mutableStateOf(opened.cwd.orEmpty()) }
    var workingFolderBaseline by rememberSaveable(opened.id) { mutableStateOf(opened.cwd.orEmpty()) }
    val workingFolderConflict = workingFolderDraft != workingFolderBaseline && current.cwd.orEmpty() != workingFolderBaseline
    LaunchedEffect(current.cwd) {
        if (workingFolderDraft == workingFolderBaseline) {
            workingFolderDraft = current.cwd.orEmpty()
            workingFolderBaseline = workingFolderDraft
        }
    }
    if (showingTransfer) TransferSettingsSheet(current) { showingTransfer = false }
    var switchingEngine by remember { mutableStateOf(false) }

    // The Model section. The draft survives rotation; the catalog is reloaded.
    var instances by remember { mutableStateOf<List<Instance>>(emptyList()) }
    var modelsLoaded by remember { mutableStateOf(false) }
    var savedModel by remember { mutableStateOf(opened.modelSelection) }
    var selectedInstanceId by rememberSaveable { mutableStateOf(opened.modelSelection.instanceId) }
    var selectedModelId by rememberSaveable { mutableStateOf(opened.modelSelection.model) }
    // "" is the engine default; the picker has no null row.
    var selectedEffort by rememberSaveable { mutableStateOf(opened.modelSelection.effort.orEmpty()) }
    val selectedInstance = instances.firstOrNull { it.instanceId == selectedInstanceId }
    val modelDraft = ModelSelection(selectedInstanceId, selectedModelId, selectedEffort.ifEmpty { null })

    fun liveBot(): Bot = session.state.value.bot(opened.id) ?: opened

    fun showModel(selection: ModelSelection) {
        selectedInstanceId = selection.instanceId
        selectedModelId = selection.model
        selectedEffort = selection.effort.orEmpty()
    }

    LaunchedEffect(Unit) {
        val loaded = coroutineScope {
            val status = async { session.configStatus() }
            val options = async { session.voiceOptions() }
            val catalog = async { session.modelInstances() }
            Triple(status.await(), options.await(), catalog.await())
        }
        config = loaded.first
        voices = loaded.second
        instances = loaded.third
        modelsLoaded = true
        // A stored "speak replies" that nothing can speak is turned off before
        // the toggle is ever drawn.
        // Android can synthesize locally regardless of the laptop voice setup.
    }

    LaunchedEffect(connection?.id, connection?.serverScopes) {
        mcpServers = null
        mcpError = null
        if (connection?.serverScopes?.contains("admin") == true) {
            try {
                mcpServers = session.botMcpServers()
            } catch (error: Exception) {
                mcpError = error.message ?: loadMcpError
            }
        }
    }

    // One preview at a time, and none that outlives this sheet.
    DisposableEffect(lifecycleOwner) {
        player.bind(lifecycleOwner)
        onDispose { player.unbind(lifecycleOwner) }
    }
    LaunchedEffect(Unit) {
        player.playbackErrors.collect { session.actionError = it }
    }

    val pickImage = rememberLauncherForActivityResult(
        remember { AvatarImagePicker.contract() },
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            try {
                when (val prepared = AvatarImagePicker.read(context.contentResolver, uri)) {
                    is PreparedAvatar.Rejected -> session.actionError = prepared.message
                    is PreparedAvatar.Ready -> {
                        val intended = AvatarImageRules.intendedUploadCrop(form.crop)
                        val updated = session.uploadAvatar(
                            data = prepared.data,
                            mime = prepared.mime,
                            forBot = liveBot(),
                            crop = intended,
                        )
                        if (updated != null) {
                            val crop = updated.avatarCrop ?: intended
                            form = form.copy(crop = crop)
                            baseline = baseline.copy(crop = crop)
                        }
                    }
                }
            } finally {
                busy = false
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Box {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterStart),
                    ) {
                        Text(stringResource(R.string.ui_done_e9b450d))
                    }
                    Text(
                        text = stringResource(R.string.ui_bot_settings_7092a29),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                FormSection(header = null) {
                    TextButton(enabled = !busy, onClick = { scope.launch {
                        busy = true
                        try {
                            val updated = session.updateProfile(ProfileRules.patch(form, baseline, config, phoneCanSpeak = true), liveBot())
                            if (updated != null) { form = ProfileForm.of(updated); baseline = form; showingTransfer = true }
                        } finally { busy = false }
                    } }) { Text(stringResource(R.string.transfer_title)) }

                    ActionRow(
                        text = stringResource(R.string.ui_what_this_bot_does_7a664f4),
                        icon = Icons.Filled.Info,
                        onClick = { onOpenOverview(bot.id) },
                    )
                }

                FormSection(header = stringResource(R.string.ui_model_68c2cc7), footer = localizedProfileCopy(ModelRules.FOOTER)) {
                    val instanceChoices = ModelRules.instanceChoices(instances, savedModel)
                    if (!modelsLoaded) {
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(localizedProfileCopy(ModelRules.LOADING), fontSize = 15.sp, modifier = Modifier.weight(1f))
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        }
                    } else if (instanceChoices.isEmpty()) {
                        IconNote(text = localizedProfileCopy(ModelRules.NONE_AVAILABLE), icon = Icons.Filled.Warning)
                    } else {
                        val providerRows = buildList {
                            if (ModelRules.providerMissing(instances, selectedInstanceId)) {
                                add(
                                    VoiceChoice(
                                        id = selectedInstanceId,
                                        label = localizedProfileCopy(ModelRules.CURRENT_PROVIDER_UNAVAILABLE),
                                        detail = null,
                                        enabled = false,
                                    ),
                                )
                            }
                            instanceChoices.forEach {
                                add(
                                    VoiceChoice(
                                        id = it.instanceId,
                                        label = ModelRules.instanceLabel(it),
                                        detail = null,
                                        enabled = it.snapshot.isAvailable,
                                    ),
                                )
                            }
                        }
                        ChoicePicker(
                            label = stringResource(R.string.ui_provider_7ceee3f),
                            choices = providerRows,
                            selected = selectedInstanceId,
                            onSelect = { id ->
                                val instance = instances.firstOrNull { it.instanceId == id }
                                if (instance != null) showModel(ModelRules.defaultsFor(instance, savedModel))
                            },
                        )
                        ChoicePicker(
                            label = stringResource(R.string.ui_model_68c2cc7),
                            choices = ModelRules.modelChoices(selectedInstance, selectedModelId).map {
                                VoiceChoice(id = it.id, label = it.label, detail = null, enabled = true)
                            },
                            selected = selectedModelId,
                            enabled = selectedInstance?.snapshot?.isAvailable == true,
                            onSelect = { selectedModelId = it },
                        )
                        val effortLevels = ModelRules.effortLevels(selectedInstance)
                        if (effortLevels.isNotEmpty()) {
                            ChoicePicker(
                                label = stringResource(R.string.ui_reasoning_effort_cd32c0f),
                                choices = buildList {
                                    add(VoiceChoice(id = "", label = localizedProfileCopy(ModelRules.DEFAULT_EFFORT_LABEL), detail = null, enabled = true))
                                    effortLevels.forEach {
                                        add(VoiceChoice(id = it, label = ModelRules.effortLabel(it), detail = null, enabled = true))
                                    }
                                },
                                selected = selectedEffort,
                                onSelect = { selectedEffort = it },
                            )
                        }
                        ModelRules.note(currentTask?.busy, selectedInstance)?.let { note ->
                            IconNote(text = localizedProfileCopy(note), icon = Icons.Filled.Info)
                        }
                        ActionRow(
                            text = stringResource(R.string.ui_apply_model_474897c),
                            icon = Icons.Filled.Check,
                            enabled = !busy && currentTask != null && ModelRules.canApply(
                                loaded = modelsLoaded,
                                botBusy = currentTask?.busy,
                                instance = selectedInstance,
                                draft = modelDraft,
                                saved = savedModel,
                            ),
                            onClick = {
                                scope.launch {
                                    busy = true
                                    try {
                                        val target = liveBot().forTask(opened.threadId) ?: return@launch
                                        val updated = session.updateModel(modelDraft, target)
                                        if (updated != null) {
                                            savedModel = updated.modelSelection
                                            showModel(updated.modelSelection)
                                        }
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                        )
                    }
                }

                FormSection(header = stringResource(R.string.ui_avatar_7631b26), footer = localizedProfileCopy(ProfileRules.AVATAR_FOOTER)) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BotAvatar(
                            bot = current,
                            size = 112.dp,
                            state = MausState.HAPPY,
                            contentDescription = stringResource(R.string.ui_dynamic_1_s_avatar_de63895, current.name),
                        )
                    }

                    AvatarCropChoices(form.crop) { form = form.copy(crop = it) }


                    ActionRow(
                        text = stringResource(R.string.ui_upload_image_f35dec5),
                        icon = Icons.Filled.AddCircle,
                        enabled = !busy,
                        onClick = { pickImage.launch(AvatarImagePicker.request()) },
                    )

                    if (current.avatarUrl != null) {
                        ActionRow(
                            text = stringResource(R.string.ui_use_mascot_0de90a1),
                            icon = Icons.Filled.Delete,
                            enabled = !busy,
                            destructive = true,
                            onClick = {
                                scope.launch {
                                    busy = true
                                    try {
                                        val updated = session.updateProfile(
                                            BotProfilePatch(
                                                avatarUrl = BotProfilePatch.AvatarURL.Clear,
                                                avatarCrop = AvatarCrop.MASCOT,
                                            ),
                                            liveBot(),
                                        )
                                        if (updated != null) {
                                            val crop = updated.avatarCrop ?: AvatarCrop.MASCOT
                                            form = form.copy(crop = crop)
                                            baseline = baseline.copy(crop = crop)
                                        }
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                        )
                    }
                }

                FormSection(
                    header = stringResource(R.string.ui_generate_an_avatar_01e5cee),
                    footer = localizedProfileCopy(ProfileRules.generateFooter(config)),
                ) {
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text(stringResource(R.string.ui_art_direction_52d878a)) },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ActionRow(
                        text = stringResource(R.string.ui_generate_on_computer_ff5b744),
                        painter = R.drawable.ic_sparkles,
                        enabled = ProfileRules.canGenerate(busy, config, prompt),
                        onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    val intended = AvatarImageRules.intendedUploadCrop(form.crop)
                                    val generated = session.generateAvatar(
                                        ProfileRules.generatePrompt(prompt),
                                        liveBot(),
                                    ) ?: return@launch
                                    // Generation picks a safe crop server-side;
                                    // the selector is the user's explicit choice,
                                    // so persist it against the returned
                                    // attachment rather than leaving the two out
                                    // of sync.
                                    val updated = session.updateProfile(
                                        BotProfilePatch(avatarCrop = intended),
                                        generated,
                                    )
                                    val crop = if (updated != null) {
                                        updated.avatarCrop ?: intended
                                    } else {
                                        // Generation itself succeeded: reflect its
                                        // authoritative fallback rather than
                                        // claiming the requested crop was saved.
                                        generated.avatarCrop ?: AvatarCrop.MASCOT
                                    }
                                    form = form.copy(crop = crop)
                                    baseline = baseline.copy(crop = crop)
                                } finally {
                                    busy = false
                                }
                            }
                        },
                    )
                }

                FormSection(header = stringResource(R.string.ui_identity_7e5a975)) {
                    OutlinedTextField(
                        value = form.name,
                        onValueChange = { form = form.copy(name = it) },
                        label = { Text(stringResource(R.string.ui_name_709a232)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.title,
                        onValueChange = { form = form.copy(title = it) },
                        label = { Text(stringResource(R.string.ui_title_768e0c1)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.description,
                        onValueChange = { form = form.copy(description = it) },
                        label = { Text(stringResource(R.string.ui_what_this_agent_does_82aa1b5)) },
                        minLines = 3,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SwitchRow(
                        label = stringResource(R.string.ui_agent_notifications_a3bd8bd),
                        checked = form.notifications,
                        onCheckedChange = { form = form.copy(notifications = it) },
                    )
                }

                FormSection(
                    header = stringResource(R.string.ui_standing_instructions_4706e23),
                    footer = stringResource(R.string.ui_bot_memory_footer),
                ) {
                    if (current.soulDrift == true) {
                        IconNote(
                            text = stringResource(R.string.ui_soul_md_was_changed_outside_openmausbot_res_eec376d),
                            icon = Icons.Filled.Warning,
                        )
                    }
                    if (soulConflict) {
                        IconNote(
                            text = stringResource(R.string.ui_standing_instructions_changed_on_the_comput_13fc657),
                            icon = Icons.Filled.Warning,
                        )
                    }
                    OutlinedTextField(
                        value = soulDraft,
                        onValueChange = { soulDraft = it },
                        label = { Text(stringResource(R.string.ui_instructions_soul_md_6a3c4fb)) },
                        minLines = 6,
                        maxLines = 15,
                        enabled = !savingSoul,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.ui_dynamic_1_s_24_000_bytes_5437d3f, soulBytes))
                    ActionRow(
                        text = stringResource(R.string.ui_save_standing_instructions_58af9a1),
                        icon = Icons.Filled.Check,
                        enabled = !busy && !savingSoul && !soulConflict && current.soulDrift != true &&
                            soulBytes <= 24_000 && soulDraft != soulBaseline,
                        onClick = {
                            scope.launch {
                                savingSoul = true
                                busy = true
                                try {
                                    val updated = session.updateProfile(
                                        BotProfilePatch(soul = soulDraft),
                                        liveBot(),
                                    )
                                    if (updated != null) {
                                        soulDraft = updated.soul.orEmpty()
                                        soulBaseline = soulDraft
                                    }
                                } finally {
                                    savingSoul = false
                                    busy = false
                                }
                            }
                        },
                    )
                }

                FormSection(header = stringResource(R.string.ui_change_history_81cca21)) {
                    ActionRow(
                        text = stringResource(if (showingHistory) R.string.ui_hide_history else R.string.ui_show_history),
                        icon = Icons.Filled.Info,
                        onClick = { showingHistory = !showingHistory },
                    )
                    if (showingHistory) ProfileHistorySection(opened.id)
                }

                FormSection(header = stringResource(R.string.ui_usage_0bb1864)) {
                    val summary = summarizeBotUsage(current.tasks)
                    val usage = summary.usage
                    if (usage.turns == 0 && usage.input == 0L && usage.output == 0L) {
                        Text(stringResource(R.string.ui_no_usage_recorded_yet_for_this_bot_8c6a388))
                    } else {
                        ActionRow(
                            text = stringResource(if (showingBotUsage) R.string.ui_hide_bot_usage else R.string.ui_show_bot_usage),
                            onClick = { showingBotUsage = !showingBotUsage },
                        )
                        if (showingBotUsage) {
                            Text(stringResource(R.string.ui_dynamic_1_s_turns_across_this_bot_s_threads_66053f6, usage.turns))
                            taskUsageLines(usage).forEach { line -> Text(line) }
                            if (summary.hasUnpricedTurns) {
                                Text(stringResource(R.string.ui_some_turns_have_no_reported_price_the_cost_1852ebc))
                            }
                        }
                    }
                }

                FormSection(header = stringResource(R.string.ui_memory_89c8a28)) {
                    ActionRow(
                        text = stringResource(if (showingMemory) R.string.ui_hide_memory else R.string.ui_open_memory),
                        icon = Icons.Filled.Info,
                        onClick = { showingMemory = !showingMemory },
                    )
                    if (showingMemory) BotMemorySection(opened.id)
                }

                FormSection(header = stringResource(R.string.ui_skills_e09212c)) {
                    ActionRow(
                        text = stringResource(if (showingSkills) R.string.ui_hide_skills else R.string.ui_manage_skills),
                        icon = Icons.Filled.Info,
                        onClick = { showingSkills = !showingSkills },
                    )
                    if (showingSkills) BotSkillsSection(opened.id)
                }

                FormSection(header = stringResource(R.string.ui_access_details_1277ed4)) {
                    ActionRow(
                        text = stringResource(if (showingAccessDetails) R.string.ui_hide_access_details else R.string.ui_show_access_details),
                        onClick = {
                            showingAccessDetails = !showingAccessDetails
                            if (showingAccessDetails) {
                                scope.launch {
                                    loadingAccessWebhooks = true
                                    accessWebhooks = session.loadBotWebhooks(opened.id)
                                    loadingAccessWebhooks = false
                                }
                            }
                        },
                    )
                    if (showingAccessDetails) {
                        Text(stringResource(R.string.ui_always_allowed_tools_a0e4e36))
                        val grants = current.alwaysAllow.orEmpty()
                        if (grants.isEmpty()) Text(stringResource(R.string.ui_no_standing_tool_approvals_e3c188a))
                        else grants.forEach { grant -> Text("• $grant") }
                        Text(stringResource(R.string.ui_inbound_webhooks_7184d03))
                        if (loadingAccessWebhooks) CircularProgressIndicator()
                        else when (val hooks = accessWebhooks) {
                            null -> Text(stringResource(R.string.ui_webhook_status_unavailable_try_reopening_t_8830733))
                            else -> if (hooks.isEmpty()) Text(stringResource(R.string.ui_no_webhooks_for_this_bot_53e4266))
                            else hooks.forEach { hook ->
                                Text(stringResource(
                                    R.string.ui_profile_webhook_status,
                                    hook.name,
                                    stringResource(if (hook.enabled) R.string.ui_status_active else R.string.ui_status_paused),
                                    hook.deliveryCount,
                                ))
                            }
                        }
                    }
                }

                FormSection(header = stringResource(R.string.ui_connected_apps_8f5e8ef)) {
                    val configured = config?.composio?.configured == true
                    val supported = instances.firstOrNull {
                        it.instanceId == current.modelSelection.instanceId
                    }?.capabilities?.composioMcp == true
                    val allowed = current.composio != false
                    Text(when {
                        !configured -> stringResource(R.string.ui_bot_apps_connect_first)
                        !supported -> stringResource(R.string.ui_bot_apps_engine_unsupported)
                        allowed -> stringResource(R.string.ui_bot_apps_allowed)
                        else -> stringResource(R.string.ui_bot_apps_unavailable)
                    })
                    SwitchRow(
                        label = stringResource(R.string.ui_allow_this_bot_to_use_connected_apps_17416a3),
                        checked = allowed,
                        enabled = connection?.serverScopes?.contains("admin") == true && !busy &&
                            (!allowed && configured && supported || allowed),
                        onCheckedChange = { next ->
                            scope.launch {
                                busy = true
                                try { session.setBotConnectedApps(liveBot(), next) }
                                finally { busy = false }
                            }
                        },
                    )
                    if (connection?.serverScopes?.contains("admin") != true) {
                        Text(stringResource(R.string.ui_changing_this_access_requires_an_admin_pai_8c816c6))
                    }
                }

                FormSection(header = stringResource(R.string.ui_mcp_servers_8d9a304)) {
                    Text(stringResource(R.string.ui_choose_which_enabled_servers_on_the_paired_a2312a2))
                    if (connection?.serverScopes?.contains("admin") != true) {
                        Text(stringResource(R.string.ui_managing_mcp_servers_requires_an_admin_pai_107eb99))
                    } else if (mcpError != null) {
                        Text(mcpError!!)
                        ActionRow(text = stringResource(R.string.ui_try_loading_again_b5375fa), onClick = {
                            scope.launch {
                                mcpError = null
                                try { mcpServers = session.botMcpServers() }
                                catch (error: Exception) { mcpError = error.message ?: loadMcpError }
                            }
                        })
                    } else if (mcpServers == null) {
                        CircularProgressIndicator()
                    } else {
                        val available = mcpServers.orEmpty()
                        Text(stringResource(if (current.mcpServers == null) R.string.ui_bot_mcp_all_enabled
                            else R.string.ui_bot_mcp_selected_only))
                        if (available.isEmpty()) Text(stringResource(R.string.ui_no_mcp_servers_are_configured_on_this_comp_353e4b4))
                        available.forEach { server ->
                            val allowed = server.enabled && server.managedBy == null
                            val selected = if (current.mcpServers == null) allowed else server.name in current.mcpServers.orEmpty()
                            SwitchRow(
                                label = server.name + when {
                                    server.managedBy != null -> stringResource(R.string.ui_server_managed_by, server.managedBy.orEmpty())
                                    !server.enabled -> stringResource(R.string.ui_server_disabled_on_computer)
                                    else -> ""
                                },
                                checked = selected,
                                enabled = (allowed || selected) && current.busy != true && !busy,
                                onCheckedChange = { checked ->
                                    val selectedNames = current.mcpServers?.toMutableList()
                                        ?: available.filter { it.enabled && it.managedBy == null }.mapTo(mutableListOf()) { it.name }
                                    if (checked) selectedNames.add(server.name) else selectedNames.remove(server.name)
                                    scope.launch {
                                        busy = true
                                        val result = session.setBotMcpServers(liveBot(), selectedNames.distinct())
                                        if (result == null) mcpError = session.actionError ?: changeMcpError
                                        busy = false
                                    }
                                },
                            )
                        }
                        if (current.mcpServers != null) {
                            ActionRow(text = stringResource(R.string.ui_use_all_enabled_servers_c421ad8), enabled = current.busy != true && !busy,
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        val result = session.setBotMcpServers(liveBot(), null)
                                        if (result == null) mcpError = session.actionError ?: resetMcpError
                                        busy = false
                                    }
                                })
                        }
                    }
                }

                FormSection(header = stringResource(R.string.ui_built_in_browser_ce1c92b)) {
                    val engineReady = config?.browserEngine?.kind == "engine"
                    val featureEnabled = config?.features?.browser == true
                    val supported = instances.firstOrNull {
                        it.instanceId == current.modelSelection.instanceId
                    }?.capabilities?.browserMcp == true
                    val allowed = current.browser != false
                    Text(when {
                        current.computer == "off" -> stringResource(R.string.ui_bot_browser_computer_off)
                        !engineReady -> config?.browserEngine?.reason ?: stringResource(R.string.ui_bot_browser_engine_unavailable)
                        !featureEnabled -> stringResource(R.string.ui_bot_browser_enable_first)
                        !supported -> stringResource(R.string.ui_bot_browser_engine_unsupported)
                        allowed -> stringResource(R.string.ui_bot_browser_allowed)
                        else -> stringResource(R.string.ui_bot_browser_unavailable)
                    })
                    SwitchRow(
                        label = stringResource(R.string.ui_give_this_bot_a_built_in_browser_3c98718),
                        checked = allowed && current.computer != "off",
                        enabled = connection?.serverScopes?.contains("admin") == true && !busy &&
                            current.computer != "off" && (allowed || engineReady && featureEnabled && supported),
                        onCheckedChange = { next ->
                            scope.launch {
                                busy = true
                                try { session.setBotBrowserAccess(liveBot(), next) }
                                finally { busy = false }
                            }
                        },
                    )
                    if (connection?.serverScopes?.contains("admin") != true) {
                        Text(stringResource(R.string.ui_changing_this_access_requires_an_admin_pai_8c816c6))
                    }
                }

                FormSection(header = stringResource(R.string.ui_bot_coordination_a0133a3)) {
                    val canCoordinate = instances.firstOrNull {
                        it.instanceId == current.modelSelection.instanceId
                    }?.capabilities?.agentsMcp == true
                    val askFirst = current.approvePeerComms == true
                    Text(when {
                        !canCoordinate -> stringResource(R.string.ui_bot_coordination_unsupported)
                        askFirst -> stringResource(R.string.ui_bot_coordination_asks)
                        else -> stringResource(R.string.ui_bot_coordination_allowed)
                    })
                    SwitchRow(
                        label = stringResource(R.string.ui_ask_before_contacting_other_bots_fb8e56d),
                        checked = askFirst,
                        enabled = connection?.serverScopes?.contains("admin") == true &&
                            !busy && current.busy != true && (askFirst || canCoordinate),
                        onCheckedChange = { next ->
                            scope.launch {
                                busy = true
                                try { session.setBotPeerContactApproval(liveBot(), next) }
                                finally { busy = false }
                            }
                        },
                    )
                    if (connection?.serverScopes?.contains("admin") != true) {
                        Text(stringResource(R.string.ui_changing_this_setting_requires_an_admin_pa_47a795c))
                    }
                }

                FormSection(header = stringResource(R.string.ui_computer_access_b090ead)) {
                    Text(stringResource(R.string.ui_dynamic_bot_default_1_s_775155b, localizedComputerAccess(current.computer)))
                    if (connection?.serverScopes?.contains("admin") == true) {
                        ActionRow(
                            text = stringResource(R.string.ui_change_bot_default_computer_533fe72),
                            enabled = current.busy != true && !busy,
                            onClick = { choosingBotComputer = true },
                        )
                    }
                    Text(stringResource(R.string.ui_dynamic_this_chat_1_s_3fdd6fa, localizedTaskSurface(currentTaskRecord?.surface, current.computer)))
                    ActionRow(
                        text = stringResource(R.string.ui_change_this_chat_s_computer_576f1d9),
                        enabled = currentTaskRecord != null && currentTaskRecord.busy != true && !busy,
                        onClick = { choosingTaskSurface = true },
                    )
                    Text(stringResource(R.string.ui_dynamic_approvals_for_this_chat_1_s_3fceff5, localizedApprovalAccess(currentTask?.approvalMode, currentTask?.autoApprove)))
                    Text(stringResource(R.string.ui_dynamic_bot_approval_default_1_s_825c310, localizedApprovalAccess(current.approvalMode, current.autoApprove)))
                    if (connection?.serverScopes?.contains("admin") == true && current.approvalMode !in listOf("full", "custom")) {
                        ActionRow(
                            text = stringResource(R.string.ui_change_ask_auto_approval_fc82281),
                            enabled = current.busy != true && !busy,
                            onClick = { choosingSafeApproval = true },
                        )
                    }
                    Text(stringResource(if (connection?.serverScopes?.contains("admin") == true)
                        R.string.ui_bot_approval_desktop_only else R.string.ui_bot_approval_admin_desktop))
                    if (connection?.serverScopes?.contains("admin") == true) {
                        ActionRow(
                            text = stringResource(if (showingCommandRules) R.string.ui_hide_exact_command_permissions
                                else R.string.ui_manage_exact_command_permissions),
                            onClick = { showingCommandRules = !showingCommandRules },
                        )
                        if (showingCommandRules) BotCommandAllowlistSection(opened.id, connection?.id)
                    }
                }

                FormSection(
                    header = stringResource(R.string.ui_browser_profile_d7d5c8f),
                    footer = stringResource(R.string.ui_bot_browser_profile_footer),
                ) {
                    Text(when (current.browserProfile) {
                        null -> stringResource(R.string.ui_bot_own_browser)
                        "guest" -> stringResource(R.string.ui_bot_temporary_browser)
                        else -> config?.browserProfiles?.firstOrNull { it.id == current.browserProfile }?.name
                            ?: stringResource(R.string.ui_bot_profile_unavailable, current.browserProfile.orEmpty())
                    })
                    if (connection?.serverScopes?.contains("admin") == true) {
                        ActionRow(
                            text = stringResource(R.string.ui_choose_browser_profile_2cdb00e),
                            enabled = current.busy != true && !busy && config != null,
                            onClick = { choosingBrowserProfile = true },
                        )
                    } else Text(stringResource(R.string.ui_changing_the_browser_profile_requires_an_a_5d29d91))
                }

                FormSection(
                    header = stringResource(R.string.ui_working_folder_00e69ec),
                    footer = stringResource(R.string.ui_bot_working_folder_footer),
                ) {
                    Text(current.cwd?.takeIf { it.isNotBlank() } ?: stringResource(R.string.android_profile_private_bot_folder))
                    if (currentTaskRecord?.cwd != null && currentTaskRecord.cwd != current.cwd) {
                        Text(stringResource(R.string.ui_dynamic_this_chat_is_still_pinned_to_1_s_b8c7601, currentTaskRecord.cwd.orEmpty()))
                    }
                    if (connection?.serverScopes?.contains("admin") == true) {
                        if (workingFolderConflict) {
                            IconNote(text = stringResource(R.string.ui_the_working_folder_changed_on_the_computer_9271460), icon = Icons.Filled.Warning)
                        }
                        OutlinedTextField(
                            value = workingFolderDraft,
                            onValueChange = { workingFolderDraft = it },
                            label = { Text(stringResource(R.string.ui_absolute_path_on_the_computer_53474d4)) },
                            singleLine = true,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ActionRow(
                            text = stringResource(R.string.ui_save_working_folder_62fa68c),
                            icon = Icons.Filled.Check,
                            enabled = !busy && !workingFolderConflict && workingFolderDraft != workingFolderBaseline,
                            onClick = {
                                scope.launch {
                                    busy = true
                                    val updated = session.setBotWorkingFolder(liveBot(), workingFolderDraft.trim().ifEmpty { null })
                                    if (updated != null) {
                                        workingFolderDraft = updated.cwd.orEmpty()
                                        workingFolderBaseline = workingFolderDraft
                                    }
                                    busy = false
                                }
                            },
                        )
                        if (!current.cwd.isNullOrBlank()) {
                            ActionRow(
                                text = stringResource(R.string.ui_use_private_bot_folder_c5897aa),
                                enabled = !busy,
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        val updated = session.setBotWorkingFolder(liveBot(), null)
                                        if (updated != null) {
                                            workingFolderDraft = ""
                                            workingFolderBaseline = ""
                                        }
                                        busy = false
                                    }
                                },
                            )
                        }
                    } else Text(stringResource(R.string.ui_changing_this_folder_requires_an_admin_pai_9e46413))
                }

                VoiceSection(
                    config = config,
                    switching = switchingEngine,
                    onSwitchEngine = { next ->
                        // The desktop's Voice engine group: one field of the
                        // ordinary config write, then a fresh voice list,
                        // because every engine names its own voices.
                        if (switchingEngine || config?.voiceProvider == next) return@VoiceSection
                        scope.launch {
                            switchingEngine = true
                            try {
                                val updated = session.switchVoiceProvider(next)
                                if (updated != null) {
                                    val (resetForm, resetBaseline) = ProfileRules.afterVoiceProviderSwitch(
                                        form = form,
                                        baseline = baseline,
                                        config = updated,
                                    )
                                    form = resetForm
                                    baseline = resetBaseline
                                    config = updated
                                    // Never render or preview the previous
                                    // provider's identifiers while reloading.
                                    voices = emptyList()
                                    voices = session.voiceOptions()
                                }
                            } finally {
                                switchingEngine = false
                            }
                        }
                    },
                ) {
                    ChoicePicker(
                        label = stringResource(R.string.ui_voice_3091c84),
                        choices = localizedVoiceChoices(ProfileRules.voiceChoices(config, voices, form.voice)),
                        selected = form.voice,
                        onSelect = { form = form.copy(voice = it) },
                    )
                    SwitchRow(
                        label = stringResource(R.string.ui_speak_replies_90b05ae),
                        checked = form.speakReplies,
                        enabled = !busy,
                        onCheckedChange = { form = form.copy(speakReplies = it) },
                    )
                    val previewSample = stringResource(R.string.phone_voice_sample)
                    ActionRow(
                        text = stringResource(R.string.ui_preview_voice_560a6fe),
                        painter = R.drawable.ic_volume_up,
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    val speech = (context.applicationContext as com.openmausbot.companion.OpenMausApp).phoneSpeech
                                    if (!speech.speak(previewSample, form.voice)) session.actionError = speech.error.value
                                } finally {
                                    busy = false
                                }
                            }
                        },
                    )
                    ProfileRules.pickAVoiceHint(config, form.voice)?.let { hint ->
                        IconNote(text = localizedProfileCopy(hint), icon = Icons.Filled.Info)
                    }
                }

                FormSection(header = null) {
                    ActionRow(
                        text = stringResource(R.string.ui_save_profile_changes_49c8333),
                        enabled = ProfileRules.canSave(form, busy),
                        onClick = {
                            scope.launch {
                                busy = true
                                val updated = session.updateProfile(
                                    ProfileRules.patch(form, baseline, config, phoneCanSpeak = true),
                                    liveBot(),
                                )
                                if (updated != null) {
                                    form = ProfileForm.of(updated)
                                    baseline = ProfileForm.of(updated)
                                }
                                busy = false
                            }
                        },
                    )
                }

                if (connection?.serverScopes?.contains("admin") == true) {
                    FormSection(header = null) {
                        ActionRow(
                            text = stringResource(R.string.ui_delete_bot_f633250),
                            icon = Icons.Filled.Delete,
                            enabled = !busy && state.bot(opened.id) != null,
                            destructive = true,
                            onClick = { confirmDelete = true },
                        )
                        deleteError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }

            if (busy) {
                Box(
                    modifier = Modifier.matchParentSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(44.dp))
                }
            }
        }
    }
    if (choosingTaskSurface && currentTaskRecord != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) choosingTaskSurface = false },
            title = { Text(stringResource(R.string.ui_computer_for_this_chat_cda3c01)) },
            text = {
                Column {
                    listOf(
                        null to stringResource(R.string.android_profile_follow_bot_default),
                        "browser" to stringResource(R.string.android_profile_browser),
                        "local" to stringResource(R.string.android_profile_this_computer),
                        "cloud" to stringResource(R.string.android_profile_cloud_computer),
                        "vm" to stringResource(R.string.ui_local_vm_7f61d89),
                    ).forEach { (surface, label) ->
                        TextButton(
                            enabled = !busy,
                            onClick = {
                                scope.launch {
                                    busy = true
                                    if (session.setTaskSurface(currentTaskRecord, liveBot(), surface)) choosingTaskSurface = false
                                    busy = false
                                }
                            },
                        ) { Text(if (currentTaskRecord.surface == surface) "✓ $label" else label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosingTaskSurface = false }) { Text(stringResource(R.string.ui_cancel_77dfd21)) } },
        )
    }
    if (choosingBotComputer) {
        AlertDialog(
            onDismissRequest = { if (!busy) choosingBotComputer = false },
            title = { Text(stringResource(R.string.ui_default_computer_for_this_bot_4e6e9ae)) },
            text = {
                Column {
                    listOf(
                        null to stringResource(R.string.android_profile_automatic),
                        "browser" to stringResource(R.string.android_profile_browser),
                        "local" to stringResource(R.string.android_profile_this_computer),
                        "cloud" to stringResource(R.string.android_profile_cloud_computer),
                        "vm" to stringResource(R.string.ui_local_vm_7f61d89),
                        "off" to stringResource(R.string.android_profile_off),
                    ).forEach { (computer, label) ->
                        TextButton(
                            enabled = !busy,
                            onClick = {
                                if (computer == "local" && (liveBot().approvalMode == "auto" || liveBot().autoApprove == true)) {
                                    choosingBotComputer = false
                                    confirmingLocalAuto = true
                                } else scope.launch {
                                    busy = true
                                    if (session.setBotComputerDefault(liveBot(), computer) != null) choosingBotComputer = false
                                    busy = false
                                }
                            },
                        ) { Text(if (current.computer == computer) "✓ $label" else label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosingBotComputer = false }) { Text(stringResource(R.string.ui_cancel_77dfd21)) } },
        )
    }
    if (choosingBrowserProfile) {
        AlertDialog(
            onDismissRequest = { if (!busy) choosingBrowserProfile = false },
            title = { Text(stringResource(R.string.ui_browser_profile_for_this_bot_d11b9e8)) },
            text = {
                Column {
                    (listOf(null to stringResource(R.string.android_new_bot_own_browser), "guest" to stringResource(R.string.ui_bot_temporary_browser)) +
                        (config?.browserProfiles.orEmpty().map { it.id to it.name })).forEach { (profileId, label) ->
                        TextButton(enabled = !busy, onClick = {
                            scope.launch {
                                busy = true
                                if (session.setBotBrowserProfile(liveBot(), profileId) != null) choosingBrowserProfile = false
                                busy = false
                            }
                        }) { Text(if (current.browserProfile == profileId) "✓ $label" else label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosingBrowserProfile = false }) { Text(stringResource(R.string.ui_cancel_77dfd21)) } },
        )
    }
    if (confirmingLocalAuto) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmingLocalAuto = false },
            title = { Text(stringResource(R.string.ui_allow_automatic_use_of_this_computer_f2feeaf)) },
            text = { Text(stringResource(R.string.ui_this_bot_is_set_to_auto_approval_choosing_9e8896d)) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        if (session.setBotComputerDefault(liveBot(), "local", acknowledgeLocalAuto = true) != null) confirmingLocalAuto = false
                        busy = false
                    }
                }) { Text(stringResource(R.string.ui_allow_3ad0e36)) }
            },
            dismissButton = { TextButton(onClick = { confirmingLocalAuto = false }) { Text(stringResource(R.string.ui_cancel_77dfd21)) } },
        )
    }
    if (choosingSafeApproval) {
        AlertDialog(
            onDismissRequest = { if (!busy) choosingSafeApproval = false },
            title = { Text(stringResource(R.string.ui_approval_default_for_this_bot_69550b5)) },
            text = {
                Column {
                    listOf("ask" to stringResource(R.string.android_new_bot_ask_actions), "auto" to stringResource(R.string.android_profile_auto_approval)).forEach { (mode, label) ->
                        TextButton(enabled = !busy, onClick = {
                            if (mode == "auto" && liveBot().computer == "local") {
                                choosingSafeApproval = false
                                confirmingAutoOnComputer = true
                            } else scope.launch {
                                busy = true
                                if (session.setBotApprovalMode(liveBot(), mode) != null) choosingSafeApproval = false
                                busy = false
                            }
                        }) { Text(if (approvalAccessLabel(current.approvalMode, current.autoApprove).equals(mode, ignoreCase = true)) "✓ $label" else label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosingSafeApproval = false }) { Text(stringResource(R.string.ui_cancel_77dfd21)) } },
        )
    }
    if (confirmingAutoOnComputer) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmingAutoOnComputer = false },
            title = { Text(stringResource(R.string.ui_allow_automatic_use_of_this_computer_f2feeaf)) },
            text = { Text(stringResource(R.string.ui_auto_approval_on_this_computer_lets_this_b_0d85a61)) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        if (session.setBotApprovalMode(liveBot(), "auto", acknowledgeLocalAuto = true) != null) confirmingAutoOnComputer = false
                        busy = false
                    }
                }) { Text(stringResource(R.string.ui_allow_3ad0e36)) }
            },
            dismissButton = { TextButton(onClick = { confirmingAutoOnComputer = false }) { Text(stringResource(R.string.ui_cancel_77dfd21)) } },
        )
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { if (!busy) confirmDelete = false },
        title = { Text(stringResource(R.string.ui_dynamic_delete_1_s_cd24016, current.name)) },
        text = { Text(stringResource(R.string.ui_this_permanently_deletes_the_bot_and_its_c_0c94492)) },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    deleteError = null
                    try {
                        session.deleteBot(opened.id)
                        confirmDelete = false
                        onDismiss()
                    } catch (error: Exception) {
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        deleteError = error.message ?: deleteBotError
                        confirmDelete = false
                    } finally { busy = false }
                }
            }) { Text(stringResource(R.string.ui_delete_bot_f633250)) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = { confirmDelete = false }) { Text(stringResource(R.string.ui_cancel_77dfd21)) } },
    )
}

@Composable
private fun localizedComputerAccess(computer: String?): String = stringResource(when (computer) {
    "browser" -> R.string.android_profile_browser
    "local" -> R.string.android_profile_this_computer
    "vm" -> R.string.ui_local_vm_7f61d89
    "cloud" -> R.string.android_profile_cloud_computer
    "off" -> R.string.android_profile_off
    null -> R.string.android_profile_automatic
    else -> R.string.android_profile_not_reported
})

@Composable
private fun localizedApprovalAccess(mode: String?, autoApprove: Boolean?): String = stringResource(when (mode) {
    "ask" -> R.string.android_profile_ask
    "auto" -> R.string.android_profile_auto
    "full" -> R.string.android_settings_full_access
    "custom" -> R.string.ui_custom_081ae3f
    else -> when (autoApprove) {
        true -> R.string.android_profile_auto
        false -> R.string.android_profile_ask
        null -> R.string.android_profile_not_reported
    }
})

@Composable
private fun localizedTaskSurface(surface: String?, botDefault: String?): String = when (surface) {
    null -> stringResource(R.string.android_profile_follow_bot_default_detail, localizedComputerAccess(botDefault))
    "browser" -> stringResource(R.string.android_profile_browser)
    "local" -> stringResource(R.string.android_profile_this_computer)
    "cloud" -> stringResource(R.string.android_profile_cloud_computer)
    "vm" -> stringResource(R.string.ui_local_vm_7f61d89)
    else -> surface
}

@OptIn(ExperimentalMaterial3Api::class)
/** A SwiftUI `Picker` as Material draws one: a read-only field that opens a menu. */
@Composable
internal fun ChoicePicker(
    label: String,
    choices: List<VoiceChoice>,
    selected: String,
    onSelect: (String) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = choices.firstOrNull { it.id == selected }?.label.orEmpty()
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(choice.label)
                            choice.detail?.let {
                                Text(it, fontSize = 13.sp, color = secondaryTint)
                            }
                        }
                    },
                    enabled = choice.enabled,
                    onClick = {
                        expanded = false
                        onSelect(choice.id)
                    },
                )
            }
        }
    }
}

/**
 * The Voice section: its words, and which of them stand in for the picker.
 *
 * Both come from one [ProfileRules.voiceCopy] call, so the branch and the
 * sentence explaining it cannot disagree. This exists as its own composable
 * because the defect it guards lives in the composition rather than in any
 * value a rule returns — the screen once drew a correct sentence in the wrong
 * slot with the whole suite green — and, like `DataTableCard`, the assertion
 * has to be over what is mounted. `VoiceSectionWiringTest` mounts exactly this.
 *
 * The engine picker rides above the branch: like the desktop's Voice engine
 * group it is drawn in every state, because switching away is how you repair
 * an engine whose credential is missing.
 */
@Composable
private fun localizedProfileCopy(text: String): String =
    (ProfileRules.copyResourceId(text) ?: ModelRules.copyResourceId(text))?.let { stringResource(it) } ?: text

@Composable
private fun localizedVoiceChoices(choices: List<VoiceChoice>): List<VoiceChoice> {
    val localized = ArrayList<VoiceChoice>(choices.size)
    for (choice in choices) {
        localized += choice.copy(label = localizedProfileCopy(choice.label))
    }
    return localized
}

@Composable
internal fun VoiceSection(
    config: ConfigStatus?,
    switching: Boolean = false,
    onSwitchEngine: (VoiceProvider) -> Unit = {},
    canSpeak: @Composable () -> Unit,
) {
    val copy = ProfileRules.voiceCopy(config)
    FormSection(header = stringResource(R.string.ui_voice_3091c84), footer = localizedProfileCopy(copy.footer)) {
        ChoicePicker(
            label = stringResource(R.string.ui_voice_engine_3b4d8de),
            choices = localizedVoiceChoices(ProfileRules.providerChoices()),
            selected = (config?.voiceProvider ?: VoiceProvider.ELEVENLABS).wire,
            onSelect = { next -> onSwitchEngine(VoiceProvider.fromWire(next)) },
            enabled = !switching,
        )
        if (copy.unconfiguredNotice != null) {
            IconNote(text = localizedProfileCopy(copy.unconfiguredNotice), painter = R.drawable.ic_volume_off)
        } else {
            canSpeak()
        }
    }
}

/**
 * A SwiftUI `Form` section, as Material draws one: a quiet heading, a rule, the
 * rows, and the explanatory line underneath.
 */
@Composable
internal fun FormSection(
    header: String?,
    footer: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        header?.let {
            Text(
                text = it.uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = secondaryTint,
            )
        }
        HorizontalDivider()
        content()
        footer?.let { Text(text = it, fontSize = 13.sp, color = secondaryTint) }
    }
}

/** A `Button` row of a Form: full width, left aligned, hit at 48 dp. */
@Composable
internal fun ActionRow(
    text: String,
    enabled: Boolean = true,
    destructive: Boolean = false,
    icon: ImageVector? = null,
    painter: Int? = null,
    onClick: () -> Unit,
) {
    val tint = when {
        !enabled -> secondaryTint.copy(alpha = 0.5f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MIN_TOUCH_TARGET)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            icon != null -> Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
            painter != null -> Icon(
                painter = painterResource(painter),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(text = text, fontSize = 15.sp, color = tint)
    }
}

/** A `Toggle` row: the whole line switches, and it is 48 dp tall. */
@Composable
internal fun SwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MIN_TOUCH_TARGET)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = if (enabled) Color.Unspecified else secondaryTint.copy(alpha = 0.5f),
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** SwiftUI's `Label(_, systemImage:)` in its quiet, informational form. */
@Composable
internal fun IconNote(
    text: String,
    icon: ImageVector? = null,
    painter: Int? = null,
    tint: Color = Color.Unspecified,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val resolved = if (tint == Color.Unspecified) secondaryTint else tint
        when {
            icon != null -> Icon(
                imageVector = icon,
                contentDescription = null,
                tint = resolved,
                modifier = Modifier.size(18.dp),
            )
            painter != null -> Icon(
                painter = painterResource(painter),
                contentDescription = null,
                tint = resolved,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(text = text, fontSize = 13.sp, color = resolved)
    }
}

/** Rotation must not throw away a half-typed profile. */
private val ProfileFormSaver = listSaver<ProfileForm, Any>(
    save = {
        listOf(
            it.name,
            it.title,
            it.description,
            it.notifications,
            it.crop.name,
            it.voice,
            it.speakReplies,
        )
    },
    restore = {
        ProfileForm(
            name = it[0] as String,
            title = it[1] as String,
            description = it[2] as String,
            notifications = it[3] as Boolean,
            crop = AvatarCrop.valueOf(it[4] as String),
            voice = it[5] as String,
            speakReplies = it[6] as Boolean,
        )
    },
)

@Composable
internal fun AvatarCropChoices(selected: AvatarCrop, onSelect: (AvatarCrop) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AvatarCrop.entries.chunked(2).forEach { options ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    androidx.compose.material3.OutlinedButton(
                        onClick = { onSelect(option) },
                        modifier = Modifier.weight(1f).height(80.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                        border = androidx.compose.foundation.BorderStroke(if (selected == option) 2.dp else 1.dp,
                            if (selected == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    ) { Text(localizedProfileCopy(ProfileRules.cropLabel(option)), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                }
            }
        }
    }
}
