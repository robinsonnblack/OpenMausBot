package com.openmausbot.companion.ui

import androidx.compose.ui.res.stringResource
import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.ActivityDetail
import com.openmausbot.companion.core.Connection
import com.openmausbot.companion.core.Session
import com.openmausbot.companion.core.ProviderConnection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Phone settings and paired-computer administration. Remote mutations require
 * an admin pairing; the unpaired screen remains usable for phone preferences.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenRoutines: (() -> Unit)? = null,
    onOpenConnectedApps: (() -> Unit)? = null,
    /** Offered instead of the computer's details when there is no pairing. */
    onConnect: (() -> Unit)? = null,
) {
    val environment = LocalCompanion.current
    val session = environment.session
    val connection by session.connection.collectAsState()
    val connections by session.connections.collectAsState()
    val status by session.status.collectAsState()
    val notifications by environment.notifications.access.collectAsState()
    val activityDetail by environment.chatPreferences.activityDetail.collectAsState()
    val showThreads by environment.chatPreferences.showThreads.collectAsState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val haptics = rememberHaptics()

    var editingAddress by remember { mutableStateOf(false) }
    var addressText by remember { mutableStateOf("") }
    var addressError by remember { mutableStateOf<String?>(null) }
    var showingFullAddress by remember { mutableStateOf(false) }
    var addressCopied by remember { mutableStateOf(false) }
    var reconnecting by remember { mutableStateOf(false) }
    var confirmingUnpair by remember { mutableStateOf(false) }
    var pendingComputerRemoval by remember { mutableStateOf<Connection?>(null) }
    var choosingActivity by remember { mutableStateOf(false) }
    var editingQuickReplies by remember { mutableStateOf(false) }
    var editingTheme by remember { mutableStateOf(false) }
    var showingUsage by remember { mutableStateOf(false) }
    var budgetEntitled by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf(false) }
    var billingEntitled by remember { mutableStateOf(false) }
    var editingBilling by remember { mutableStateOf(false) }
    var editingAboutMe by remember { mutableStateOf(false) }
    var aboutMeText by remember { mutableStateOf("") }
    var aboutMeOriginal by remember { mutableStateOf("") }
    var profileName by remember { mutableStateOf("") }
    var profileEmail by remember { mutableStateOf("") }
    var profileNameOriginal by remember { mutableStateOf("") }
    var profileEmailOriginal by remember { mutableStateOf("") }
    var aboutMeLoading by remember { mutableStateOf(false) }
    var aboutMeSaving by remember { mutableStateOf(false) }
    var aboutMeError by remember { mutableStateOf<String?>(null) }
    var managingTeams by remember { mutableStateOf(false) }
    var creatingBotForTeam by remember { mutableStateOf<String?>(null) }
    val teamDraft = remember { TeamManagementDraft() }
    var configuringProvider by remember { mutableStateOf<ProviderConnection?>(null) }
    var managingEngines by remember { mutableStateOf(false) }
    var managingThreads by remember { mutableStateOf(false) }
    var editingRoomTurnTimeout by remember { mutableStateOf(false) }
    var viewingAdminActivity by remember { mutableStateOf(false) }
    var managingLocalVm by remember { mutableStateOf(false) }
    var exportingBackup by remember { mutableStateOf(false) }
    var restoringBackup by remember { mutableStateOf(false) }
    var editingDefaultBotModel by remember { mutableStateOf(false) }
    var editingNewBotEffort by remember { mutableStateOf(false) }
    var managingBrowserProfiles by remember { mutableStateOf(false) }
    var editingHostBrowser by remember { mutableStateOf(false) }
    var editingSkillAuthoring by remember { mutableStateOf(false) }

    LaunchedEffect(connection) {
        budgetEntitled = false
        billingEntitled = false
        if (connection?.serverScopes?.contains("admin") == true) {
            val features = session.configStatus()?.edition?.features.orEmpty()
            budgetEntitled = "budgets" in features
            billingEntitled = "billing" in features
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderBackButton(onBack)
            Text(stringResource(R.string.ui_settings_c7f73bb), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        HorizontalDivider()

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsSection("Computer") {
                val bound = connection
                if (bound != null) {
                    val address = SettingsPolicy.addressText(bound)
                    SettingsRow("Name", bound.name)
                    AddressRow(
                        address = address,
                        expanded = showingFullAddress,
                        copied = addressCopied,
                        onToggle = { showingFullAddress = !showingFullAddress },
                        onCopy = {
                            scope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(ClipData.newPlainText(ADDRESS_CLIP_LABEL, address)),
                                )
                                addressCopied = true
                                delay(COPIED_LABEL_MILLIS)
                                addressCopied = false
                            }
                        },
                    )
                    // The stored address can simply go stale. Editing it here
                    // keeps the pairing and its token (§7).
                    SettingsButton("Edit address") {
                        addressText = address
                        addressError = null
                        editingAddress = true
                    }
                } else if (onConnect != null) {
                    SettingsButton("Connect a computer", onClick = onConnect)
                }
                SettingsRow("Connection", SettingsPolicy.statusText(status))
                if (bound != null) {
                    SettingsRow("Pairing access", SettingsPolicy.pairingAccessText(bound))
                    Footnote("This is the access granted to this phone for the selected computer. Bots may still ask before using the computer.")
                    SettingsButton("Connect another computer") {
                        haptics.play(TactileAction.CONNECT_ANOTHER_COMPUTER)
                        session.beginPairing()
                    }
                }
            }

            val otherComputers = connections.filter { it.id != connection?.id }
            if (otherComputers.isNotEmpty()) {
                SettingsSection("Other computers") {
                    otherComputers.forEach { computer ->
                        SettingsButton("Use ${computer.name}") {
                            haptics.play(TactileAction.SWITCH_COMPUTER)
                            session.switchComputer(computer.id)
                        }
                        SettingsButton("Remove ${computer.name}", destructive = true) {
                            pendingComputerRemoval = computer
                        }
                    }
                    Footnote("Each computer is paired separately. Only the selected computer is active at a time.")
                }
            }

            if (connection != null) {
                SettingsSection("Troubleshooting") {
                    Footnote(troubleshootingText(status))
                    SettingsButton(
                        text = stringResource(R.string.ui_try_reconnecting_8310b02),
                        enabled = !reconnecting,
                        trailing = {
                            if (reconnecting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        },
                    ) {
                        scope.launch {
                            reconnecting = true
                            // Waits until the stream leaves connecting (or 10s),
                            // so the spinner means what it appears to mean.
                            session.refresh()
                            reconnecting = false
                        }
                    }
                }
            }

            SettingsSection("Notifications") {
                SettingsRow(
                    "Status",
                    NotificationPermissionController.statusText(notifications),
                )
                SettingsButton(
                    text = NotificationPermissionController.buttonText(notifications),
                    enabled = NotificationPermissionController.buttonEnabled(notifications),
                    onClick = environment.notifications::act,
                )
                Footnote(SettingsPolicy.NOTIFICATIONS_FOOTER)
            }

            SettingsSection("Appearance") {
                val themeId by environment.chatPreferences.themeId.collectAsState()
                SettingsButton("Theme: ${themeId.replaceFirstChar(Char::uppercase)}") { editingTheme = true }
            }

            if (connection?.serverScopes?.contains("admin") == true) {
                SettingsSection("Shared profile") {
                    SettingsButton("Name, email and About me") {
                        editingAboutMe = true
                        aboutMeLoading = true
                        aboutMeError = null
                        scope.launch {
                            try {
                                val profile = session.configStatus()?.profile
                                    ?: throw IllegalStateException("Could not load the shared profile.")
                                aboutMeText = profile.aboutMe.orEmpty()
                                aboutMeOriginal = aboutMeText
                                profileName = profile.name
                                profileNameOriginal = profile.name
                                profileEmail = profile.email
                                profileEmailOriginal = profile.email
                            } catch (error: Exception) {
                                aboutMeError = error.message ?: "Could not load the shared profile."
                            } finally {
                                aboutMeLoading = false
                            }
                        }
                    }
                    Footnote("Shared with bots on this computer. Editing requires an admin pairing.")
                }
            }

            SettingsSection("Background connection") {
                val alwaysOnEnabled by environment.alwaysOnEnabled.collectAsState()
                SettingsRow("Status", if (alwaysOnEnabled) "Always on" else "Only while open")
                SettingsButton(
                    text = stringResource(if (alwaysOnEnabled) R.string.ui_turn_off else R.string.ui_turn_on),
                    onClick = environment.onToggleAlwaysOn,
                )
                Footnote(
                    if (alwaysOnEnabled) {
                        "OpenMausBot keeps a permanent notification while this is on, so scheduled " +
                            "reminders and routine results reach you even with the app fully closed."
                    } else {
                        "Notifications only arrive while the app is open or was recently backgrounded. " +
                            "Turn this on if you rely on scheduled routines to notify you later — it adds " +
                            "a permanent low-priority notification and uses a little more battery."
                    },
                )
            }

            SettingsSection("Chat") {
                SettingsRow("Activity", activityDetail.label)
                SettingsButton("Change activity detail") { choosingActivity = true }
                SettingsButton("Quick replies") { editingQuickReplies = true }
                SettingsRow("Thread lists", if (showThreads) "Shown" else "Hidden")
                SettingsButton(if (showThreads) "Hide thread lists" else "Show thread lists") {
                    environment.chatPreferences.setShowThreads(!showThreads)
                }
                Footnote("This only changes the bot list. You can still open and manage threads from each bot, and search finds them.")
                Footnote(activityDetail.caption)
            }

            if (connection != null) SettingsSection("Usage") {
                SettingsButton(if (showingUsage) "Hide workspace usage" else "Show workspace usage") {
                    showingUsage = !showingUsage
                }
                if (showingUsage) WorkspaceUsageSection()
                if (budgetEntitled) SettingsButton("Monthly spending limit") { editingBudget = true }
                if (billingEntitled) SettingsButton("Model prices") { editingBilling = true }
            }

            if (connection?.serverScopes?.contains("admin") == true) {
                SettingsSection("Bot defaults") {
                    SettingsButton("Default model for new bots") { editingDefaultBotModel = true }
                    SettingsButton("Default reasoning for new bots") { editingNewBotEffort = true }
                }
                SettingsSection("Teams") {
                    SettingsButton("Manage teams") { managingTeams = true }
                }
                SettingsSection("Providers") {
                    ProviderConnection.entries.forEach { provider ->
                        SettingsButton("${provider.label} API and models") { configuringProvider = provider }
                    }
                }
                SettingsSection("Engines") {
                    SettingsButton("Manage engines on this computer") { managingEngines = true }
                }
                SettingsSection("Threads") {
                    SettingsButton("Concurrency and log retention") { managingThreads = true }
                }
                SettingsSection("Room turns") {
                    SettingsButton("Turn timeout") { editingRoomTurnTimeout = true }
                }
                SettingsSection("Activity") {
                    SettingsButton("Changes and approvals") { viewingAdminActivity = true }
                }
                SettingsSection("Computer") {
                    SettingsButton("Manage Local VM on this computer") { managingLocalVm = true }
                }
                SettingsSection("Backups") {
                    SettingsButton("Export encrypted workspace backup") { exportingBackup = true }
                    SettingsButton("Import and restore a workspace backup") { restoringBackup = true }
                }
                SettingsSection("Browser") {
                    SettingsButton("Built-in browser on this computer") { editingHostBrowser = true }
                    SettingsButton("Manage browser profiles") { managingBrowserProfiles = true }
                }
                SettingsSection("Experimental") {
                    SettingsButton("Bot skill authoring") { editingSkillAuthoring = true }
                }
            }

            // Routine schedules live on the computer this phone is bound to.
            // With no binding there is nothing to schedule against, so the row
            // is absent rather than present and dead.
            if (onOpenRoutines != null || onOpenConnectedApps != null) {
                SettingsSection("Workspace") {
                    onOpenRoutines?.let { openRoutines ->
                        SettingsButton(
                            text = stringResource(R.string.ui_threads_routines_65d7efc),
                            icon = R.drawable.ic_schedule,
                            onClick = openRoutines,
                        )
                    }
                    onOpenConnectedApps?.let { openConnectedApps ->
                        SettingsButton(
                            text = stringResource(R.string.ui_connected_apps_8ab72a8),
                            onClick = openConnectedApps,
                        )
                    }
                    Footnote(SettingsPolicy.WORKSPACE_FOOTER)
                }
            }

            if (connection != null) {
                SettingsSection(null) {
                    SettingsButton(
                        text = stringResource(if (connections.size > 1) R.string.ui_remove_this_computer
                            else R.string.ui_unpair_this_phone),
                        destructive = true,
                    ) { confirmingUnpair = true }
                    Footnote(SettingsPolicy.UNPAIR_FOOTER)
                }
            }

            SettingsSection("Not here") {
                Footnote(SettingsPolicy.NOT_HERE)
            }
        }
    }

    if (editingAddress) {
        AlertDialog(
            onDismissRequest = { editingAddress = false },
            title = { Text(stringResource(R.string.ui_edit_address_31fe67f)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(SettingsPolicy.EDIT_ADDRESS_MESSAGE, fontSize = 14.sp)
                    OutlinedTextField(
                        value = addressText,
                        onValueChange = {
                            addressText = it
                            addressError = null
                        },
                        placeholder = { Text(stringResource(R.string.ui_https_mac_example_or_192_168_1_42_8810_e277eb2)) },
                        singleLine = true,
                        isError = addressError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    addressError?.let {
                        Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Session re-parses, re-dials and persists; the walk and
                        // promote semantics stay its job. The form only refuses
                        // what it can already tell is not an address.
                        if (session.updateAddress(addressText)) {
                            editingAddress = false
                        } else {
                            addressError = AddressEdit.INVALID
                        }
                    },
                ) { Text(stringResource(R.string.ui_save_efc007a)) }
            },
            dismissButton = {
                TextButton(onClick = { editingAddress = false }) { Text(stringResource(R.string.ui_cancel_77dfd21)) }
            },
        )
    }

    if (confirmingUnpair) {
        AlertDialog(
            onDismissRequest = { confirmingUnpair = false },
            title = { Text(if (connections.size > 1) stringResource(R.string.ui_remove_computer_confirm, connection?.name)
                else stringResource(R.string.ui_unpair_phone_confirm)) },
            text = {
                Text(
                    if (connections.size > 1) {
                        stringResource(R.string.ui_remove_saved_connection_explanation)
                    } else {
                        stringResource(R.string.ui_unpair_phone_explanation)
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingUnpair = false
                        // Local token and connection only. Revoking the device
                        // itself is Settings → Phone on the computer (§6).
                        session.signOut()
                    },
                ) {
                    // With another computer saved this removes one of them; the
                    // phone stays paired, so "Unpair" would be the wrong promise.
                    Text(
                        text = stringResource(if (connections.size > 1) R.string.ui_remove_action else R.string.ui_unpair_action),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingUnpair = false }) { Text(stringResource(R.string.ui_cancel_77dfd21)) }
            },
        )
    }

    pendingComputerRemoval?.let { computer ->
        AlertDialog(
            onDismissRequest = { pendingComputerRemoval = null },
            title = { Text(stringResource(R.string.ui_dynamic_remove_1_s_c8e14e6, computer.name)) },
            text = { Text(stringResource(R.string.ui_this_removes_the_saved_connection_from_thi_54db681)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingComputerRemoval = null
                        session.forgetConnection(computer.id)
                    },
                ) { Text(stringResource(R.string.ui_remove_e963907), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingComputerRemoval = null }) { Text(stringResource(R.string.ui_cancel_77dfd21)) }
            },
        )
    }

    if (choosingActivity) {
        AlertDialog(
            onDismissRequest = { choosingActivity = false },
            title = { Text(stringResource(R.string.ui_activity_detail_049f3bd)) },
            text = {
                // iOS draws a Picker (SettingsView.swift:67-78), which marks the
                // choice already in force; three plain buttons do not.
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ActivityDetail.entries.forEach { detail ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = MIN_TOUCH_TARGET)
                                .selectable(
                                    selected = detail == activityDetail,
                                    role = Role.RadioButton,
                                    onClick = {
                                        environment.chatPreferences.setActivityDetail(detail)
                                        choosingActivity = false
                                    },
                                )
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = detail == activityDetail, onClick = null)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(detail.label, textAlign = TextAlign.Start)
                                Text(detail.caption, fontSize = 12.sp, color = secondaryTint)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosingActivity = false }) { Text(stringResource(R.string.ui_cancel_77dfd21)) } },
        )
    }

    if (editingQuickReplies) {
        QuickRepliesEditor(
            preferences = environment.chatPreferences,
            onDismiss = { editingQuickReplies = false },
        )
    }
    if (editingTheme) ThemeEditor(environment.chatPreferences) { editingTheme = false }
    if (editingAboutMe) {
        AlertDialog(
            onDismissRequest = { if (!aboutMeSaving) editingAboutMe = false },
            title = { Text(stringResource(R.string.ui_shared_profile_ff09ab4)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.ui_this_profile_is_shared_with_every_bot_on_t_1199512))
                    if (aboutMeLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    else if (aboutMeError == null || aboutMeText.isNotEmpty() || aboutMeOriginal.isNotEmpty()) {
                        OutlinedTextField(value = profileName, onValueChange = { profileName = it.take(200); aboutMeError = null },
                            label = { Text(stringResource(R.string.ui_your_name_ab42293)) }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = profileEmail, onValueChange = { profileEmail = it.take(320); aboutMeError = null },
                            label = { Text(stringResource(R.string.ui_email_84add5b)) }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(
                            value = aboutMeText,
                            onValueChange = {
                                if (it.length <= 24_000) aboutMeText = it
                                aboutMeError = null
                            },
                            label = { Text(stringResource(R.string.ui_what_should_bots_know_about_you_d3abb57)) },
                            minLines = 5,
                            maxLines = 12,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("${aboutMeText.length} / 24,000", color = secondaryTint, fontSize = 12.sp)
                    }
                    aboutMeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !aboutMeLoading && !aboutMeSaving && aboutMeError == null &&
                        (aboutMeText != aboutMeOriginal || profileName != profileNameOriginal || profileEmail != profileEmailOriginal),
                    onClick = {
                        scope.launch {
                            aboutMeSaving = true
                            try {
                                val current = session.configStatus()?.profile
                                    ?: throw IllegalStateException("Could not verify the current shared profile.")
                                if (current.aboutMe.orEmpty() != aboutMeOriginal || current.name != profileNameOriginal ||
                                    current.email != profileEmailOriginal) {
                                    throw IllegalStateException("The profile changed on the computer. Close and reopen to review it.")
                                }
                                val saved = session.updateSharedProfile(profileName, profileEmail, aboutMeText).profile
                                if (saved?.aboutMe.orEmpty() != aboutMeText || saved?.name != profileName || saved?.email != profileEmail)
                                    throw IllegalStateException("The computer did not confirm the saved profile.")
                                editingAboutMe = false
                            } catch (error: Exception) {
                                aboutMeError = error.message ?: "Could not save the shared profile."
                            } finally {
                                aboutMeSaving = false
                            }
                        }
                    },
                ) { Text(stringResource(if (aboutMeSaving) R.string.ui_saving else R.string.ui_save_action)) }
            },
            dismissButton = { TextButton(onClick = { editingAboutMe = false }) { Text(stringResource(R.string.ui_cancel_77dfd21)) } },
        )
    }
    if (managingTeams) TeamManagementSheet(
        draft = teamDraft,
        onDismiss = { managingTeams = false; teamDraft.clear() },
        onCreateBot = { team ->
            managingTeams = false
            creatingBotForTeam = team
        },
    )
    creatingBotForTeam?.let { team ->
        NewBotModelSheet(
            initialSection = team,
            onCreated = { bot ->
                teamDraft.includeCreatedBot(bot.id)
                creatingBotForTeam = null
                managingTeams = true
            },
            onDismiss = { creatingBotForTeam = null; managingTeams = true },
        )
    }
    configuringProvider?.let { provider -> ProviderSetupSheet(provider) { configuringProvider = null } }
    if (managingEngines) EngineManagementSheet { managingEngines = false }
    if (managingThreads) ThreadSettingsSheet { managingThreads = false }
    if (editingRoomTurnTimeout) RoomTurnTimeoutSheet { editingRoomTurnTimeout = false }
    if (editingNewBotEffort) NewBotEffortSheet { editingNewBotEffort = false }
    if (viewingAdminActivity) AdminActivitySheet { viewingAdminActivity = false }
    if (managingLocalVm) LocalVmManagementSheet { managingLocalVm = false }
    if (exportingBackup) WorkspaceBackupExportSheet { exportingBackup = false }
    if (restoringBackup) WorkspaceBackupRestoreSheet { restoringBackup = false }
    if (editingBudget) WorkspaceBudgetSheet { editingBudget = false }
    if (editingBilling) WorkspaceBillingSheet { editingBilling = false }
    if (editingDefaultBotModel) DefaultBotModelSheet { editingDefaultBotModel = false }
    if (managingBrowserProfiles) BrowserProfilesSheet { managingBrowserProfiles = false }
    if (editingHostBrowser) HostBrowserFeatureSheet { editingHostBrowser = false }
    if (editingSkillAuthoring) SkillAuthoringSheet { editingSkillAuthoring = false }
}

@Composable
private fun SettingsSection(title: String?, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        title?.let {
            Text(
                text = it.uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = secondaryTint,
            )
        }
        HorizontalDivider()
        content()
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 15.sp, color = secondaryTint)
        Text(
            text = value,
            fontSize = 15.sp,
            textAlign = TextAlign.End,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        )
    }
}

/**
 * The address, short enough to read at a glance and long enough to copy — the
 * port of the connection details in `ios/App/SettingsView.swift:346-371`.
 */
@Composable
private fun AddressRow(
    address: String,
    expanded: Boolean,
    copied: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.ui_address_d70f93d), fontSize = 15.sp, color = secondaryTint)
        if (expanded) {
            // Selectable, because the reason to show it in full is to take it away.
            SelectionContainer {
                Text(
                    text = address,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = secondaryTint,
                )
            }
        } else {
            Text(
                text = shortenedAddress(address),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = secondaryTint,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onToggle) {
                Text(stringResource(if (expanded) R.string.ui_hide_full_address else R.string.ui_show_full_address))
            }
            TextButton(onClick = onCopy) {
                Text(stringResource(if (copied) R.string.ui_copied else R.string.ui_copy_action))
            }
        }
    }
}

@Composable
private fun SettingsButton(
    text: String,
    enabled: Boolean = true,
    destructive: Boolean = false,
    icon: Int? = null,
    /** Drawn at the end of the row — a progress indicator while one is running. */
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val tint = if (destructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Icon(
                    painter = painterResource(it),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
                color = tint,
            )
            trailing?.invoke()
        }
    }
}

@Composable
private fun Footnote(text: String) {
    Text(text = text, fontSize = 13.sp, color = secondaryTint)
}

private const val ADDRESS_CLIP_LABEL = "OpenMausMobile computer address"

/** Long enough for "Copied" to be read, short enough not to linger (iOS `:363-367`). */
private const val COPIED_LABEL_MILLIS = 2_000L

/**
 * What the Troubleshooting section says before offering to reconnect — the port
 * of `ConnectionSecurityView.troubleshootingText` (`ios/App/SettingsView.swift:445-458`).
 */
internal fun troubleshootingText(status: Session.Status): String = when (status) {
    Session.Status.Live -> "This computer is connected and responding normally."
    Session.Status.Connecting -> "OpenMausBot is trying the saved connection automatically."
    Session.Status.Unauthorized -> "This phone was removed from the computer. Pair it again to reconnect."
    Session.Status.Unpaired -> "This phone is not paired with a computer."
    is Session.Status.Offline -> status.message
}

/**
 * The address with its middle taken out, so a long tailnet name still shows the
 * host and the port it ends in — `shortened` in `ios/App/SettingsView.swift:460-464`.
 */
internal fun shortenedAddress(address: String): String {
    if (address.length <= 14) return address
    val leading = minOf(20, maxOf(8, address.length - 8))
    return address.take(leading) + "…" + address.takeLast(6)
}
