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
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.key
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.CancellationException
import java.util.Locale
import java.text.DateFormat
import java.util.Date
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.openmausbot.companion.core.PairingAccessState
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
import com.openmausbot.companion.core.allows
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
    val pairingAccess by session.pairingAccess.collectAsState()
    LaunchedEffect(connection?.id, status) {
        if (connection != null) {
            do {
                session.refreshPairingAccess()
                delay(5_000)
            } while (status == Session.Status.Live)
        }
    }
    val notifications by environment.notifications.access.collectAsState()
    val activityDetail by environment.chatPreferences.activityDetail.collectAsState()
    val showThreads by environment.chatPreferences.showThreads.collectAsState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val haptics = rememberHaptics()
    val invalidAddressError = stringResource(R.string.android_settings_invalid_address)
    val loadProfileError = stringResource(R.string.android_settings_load_profile_error)
    val verifyProfileError = stringResource(R.string.android_settings_verify_profile_error)
    val profileChangedError = stringResource(R.string.android_settings_profile_changed_error)
    val profileNotConfirmedError = stringResource(R.string.android_settings_profile_not_confirmed_error)
    val saveProfileError = stringResource(R.string.android_settings_save_profile_error)

    var editingAddress by remember { mutableStateOf(false) }
    var addressText by remember { mutableStateOf("") }
    var addressError by remember { mutableStateOf<String?>(null) }
    var showingFullAddress by remember { mutableStateOf(false) }
    var addressCopied by remember { mutableStateOf(false) }
    var reconnecting by remember { mutableStateOf(false) }
    var reconnectResult by remember(connection?.id) { mutableStateOf<Session.Status?>(null) }
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

    LaunchedEffect(connection, pairingAccess) {
        budgetEntitled = false
        billingEntitled = false
        if (connection?.serverScopes?.contains("admin") == true) {
            val features = session.configStatus()?.edition?.features.orEmpty()
            budgetEntitled = "budgets" in features && pairingAccess.allows("budgets")
            billingEntitled = "billing" in features && pairingAccess.allows("budgets")
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
            SettingsSection(stringResource(R.string.android_settings_computer_924645)) {
                val bound = connection
                if (bound != null) {
                    val address = SettingsPolicy.addressText(bound)
                    SettingsRow(stringResource(R.string.android_settings_name_709a23), bound.name)
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
                    SettingsButton(stringResource(R.string.android_settings_edit_address_31fe67)) {
                        addressText = address
                        addressError = null
                        editingAddress = true
                    }
                } else if (onConnect != null) {
                    SettingsButton(stringResource(R.string.android_settings_connect_a_computer_08ad5a), onClick = onConnect)
                }
                SettingsRow(stringResource(R.string.android_settings_connection_6512ee), localizedConnectionStatus(status))
                if (bound != null) {
                    key(bound.id) { PairingAccessDetails(pairingAccess) { session.refreshPairingAccess() } }
                    SettingsButton(stringResource(R.string.android_settings_connect_another_computer_2a3942)) {
                        haptics.play(TactileAction.CONNECT_ANOTHER_COMPUTER)
                        session.beginPairing()
                    }
                }
            }

            val otherComputers = connections.filter { it.id != connection?.id }
            if (otherComputers.isNotEmpty()) {
                SettingsSection(stringResource(R.string.android_settings_other_computers_a46a75)) {
                    otherComputers.forEach { computer ->
                        SettingsButton(stringResource(R.string.android_settings_use_computer_name_199816, computer.name)) {
                            haptics.play(TactileAction.SWITCH_COMPUTER)
                            session.switchComputer(computer.id)
                        }
                        SettingsButton(stringResource(R.string.android_settings_remove_computer_name_23f432, computer.name), destructive = true) {
                            pendingComputerRemoval = computer
                        }
                    }
                    Footnote(stringResource(R.string.android_settings_each_computer_is_paired_separately_only_th_d4064b))
                }
            }

            if (connection != null) {
                SettingsSection(stringResource(R.string.android_settings_troubleshooting_285ec8)) {
                    Footnote(localizedTroubleshooting(status))
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
                        reconnecting = true
                        reconnectResult = null
                        scope.launch {
                            // Waits until the stream leaves connecting (or 10s),
                            // so the spinner means what it appears to mean.
                            try {
                                session.refresh()
                                reconnectResult = session.status.value
                            } finally { reconnecting = false }
                        }
                    }
                    reconnectResult?.let { result ->
                        Text(
                            if (result == Session.Status.Live) stringResource(R.string.pairing_reconnect_success)
                            else localizedConnectionStatus(result),
                            fontSize = 13.sp,
                            color = if (result is Session.Status.Offline || result == Session.Status.Unauthorized) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            SettingsSection(stringResource(R.string.android_settings_notifications_753a22)) {
                SettingsRow(
                    stringResource(R.string.android_settings_status_bae7d5),
                    stringResource(when (notifications) {
                        NotificationAccess.GRANTED -> R.string.android_settings_notifications_allowed
                        NotificationAccess.ASKABLE -> R.string.android_settings_notifications_not_allowed
                        NotificationAccess.BLOCKED -> R.string.android_settings_notifications_system_off
                    }),
                )
                SettingsButton(
                    text = stringResource(when (notifications) {
                        NotificationAccess.GRANTED -> R.string.android_settings_notifications_on
                        NotificationAccess.ASKABLE -> R.string.android_onboarding_enable_notifications
                        NotificationAccess.BLOCKED -> R.string.android_settings_open_notification_settings
                    }),
                    enabled = NotificationPermissionController.buttonEnabled(notifications),
                    onClick = environment.notifications::act,
                )
                Footnote(stringResource(R.string.android_onboarding_notifications_body))
            }

            SettingsSection(stringResource(R.string.android_settings_appearance_41def7)) {
                val themeId by environment.chatPreferences.themeId.collectAsState()
                SettingsButton(stringResource(R.string.android_settings_theme_themeid_replacefirstchar_char_upperc_700698, themeId.replaceFirstChar(Char::uppercase))) { editingTheme = true }
            }

            if (pairingAccess.allows("profile")) {
                SettingsSection(stringResource(R.string.android_settings_shared_profile_ff09ab)) {
                    SettingsButton(stringResource(R.string.android_settings_name_email_and_about_me_b2337b)) {
                        editingAboutMe = true
                        aboutMeLoading = true
                        aboutMeError = null
                        scope.launch {
                            try {
                                val profile = session.configStatus()?.profile
                                    ?: throw IllegalStateException(loadProfileError)
                                aboutMeText = profile.aboutMe.orEmpty()
                                aboutMeOriginal = aboutMeText
                                profileName = profile.name
                                profileNameOriginal = profile.name
                                profileEmail = profile.email
                                profileEmailOriginal = profile.email
                            } catch (error: Exception) {
                                aboutMeError = error.message ?: loadProfileError
                            } finally {
                                aboutMeLoading = false
                            }
                        }
                    }
                    Footnote(stringResource(R.string.android_settings_shared_with_bots_on_this_computer_editing_382ab3))
                }
            }

            SettingsSection(stringResource(R.string.android_settings_background_connection_570dc0)) {
                val alwaysOnEnabled by environment.alwaysOnEnabled.collectAsState()
                SettingsRow(stringResource(R.string.android_settings_status_bae7d5), stringResource(
                    if (alwaysOnEnabled) R.string.android_settings_always_on else R.string.android_settings_only_while_open,
                ))
                SettingsButton(
                    text = stringResource(if (alwaysOnEnabled) R.string.ui_turn_off else R.string.ui_turn_on),
                    onClick = environment.onToggleAlwaysOn,
                )
                Footnote(
                    if (alwaysOnEnabled) {
                        stringResource(R.string.android_settings_background_on_help)
                    } else {
                        stringResource(R.string.android_settings_background_off_help)
                    },
                )
            }

            SettingsSection(stringResource(R.string.android_settings_chat_2ced57)) {
                SettingsRow(stringResource(R.string.android_settings_activity_81c0d9), activityDetail.label)
                SettingsButton(stringResource(R.string.android_settings_change_activity_detail_f396fa)) { choosingActivity = true }
                SettingsButton(stringResource(R.string.android_settings_quick_replies_c14223)) { editingQuickReplies = true }
                SettingsRow(stringResource(R.string.android_settings_thread_lists_f64d31), stringResource(
                    if (showThreads) R.string.android_settings_shown else R.string.android_settings_hidden,
                ))
                SettingsButton(stringResource(if (showThreads) R.string.android_settings_hide_thread_lists else R.string.android_settings_show_thread_lists)) {
                    environment.chatPreferences.setShowThreads(!showThreads)
                }
                Footnote(stringResource(R.string.android_settings_this_only_changes_the_bot_list_you_can_sti_d94a78))
                Footnote(activityDetail.caption)
            }

            if (connection != null && (pairingAccess.allows("usage") || budgetEntitled || billingEntitled)) SettingsSection(stringResource(R.string.android_settings_usage_0bb186)) {
                if (pairingAccess.allows("usage")) {
                    SettingsButton(stringResource(if (showingUsage) R.string.android_settings_hide_usage else R.string.android_settings_show_usage)) {
                        showingUsage = !showingUsage
                    }
                    if (showingUsage) WorkspaceUsageSection()
                }
                if (budgetEntitled) SettingsButton(stringResource(R.string.android_settings_monthly_spending_limit_4a8a9e)) { editingBudget = true }
                if (billingEntitled) SettingsButton(stringResource(R.string.android_settings_model_prices_5f9f78)) { editingBilling = true }
            }

            if (connection?.serverScopes?.contains("admin") == true) {
                if (pairingAccess.allows("providers")) SettingsSection(stringResource(R.string.android_settings_bot_defaults_f064df)) {
                    SettingsButton(stringResource(R.string.android_settings_default_model_for_new_bots_e47907)) { editingDefaultBotModel = true }
                    SettingsButton(stringResource(R.string.android_settings_default_reasoning_for_new_bots_a4425b)) { editingNewBotEffort = true }
                }
                if (pairingAccess.allows("teams")) SettingsSection(stringResource(R.string.android_settings_teams_cbfd44)) {
                    SettingsButton(stringResource(R.string.android_settings_manage_teams_c99bf8)) { managingTeams = true }
                }
                if (pairingAccess.allows("providers")) SettingsSection(stringResource(R.string.android_settings_providers_87b7c0)) {
                    ProviderConnection.entries.forEach { provider ->
                        SettingsButton(stringResource(R.string.android_settings_provider_label_api_and_models_d95ab7, provider.label)) { configuringProvider = provider }
                    }
                }
                if (pairingAccess.allows("engines")) SettingsSection(stringResource(R.string.android_settings_engines_7f5d63)) {
                    SettingsButton(stringResource(R.string.android_settings_manage_engines_on_this_computer_a0a12c)) { managingEngines = true }
                }
                if (pairingAccess.allows("workspace")) SettingsSection(stringResource(R.string.android_settings_threads_bb12e8)) {
                    SettingsButton(stringResource(R.string.android_settings_concurrency_and_log_retention_ea73fb)) { managingThreads = true }
                }
                if (pairingAccess.allows("workspace")) SettingsSection(stringResource(R.string.android_settings_room_turns_6062aa)) {
                    SettingsButton(stringResource(R.string.android_settings_turn_timeout_70c2c0)) { editingRoomTurnTimeout = true }
                }
                if (pairingAccess.allows("usage")) SettingsSection(stringResource(R.string.android_settings_activity_81c0d9)) {
                    SettingsButton(stringResource(R.string.android_settings_changes_and_approvals_1afb99)) { viewingAdminActivity = true }
                }
                if (pairingAccess.allows("localVm")) SettingsSection(stringResource(R.string.android_settings_computer_924645)) {
                    SettingsButton(stringResource(R.string.android_settings_manage_local_vm_on_this_computer_906520)) { managingLocalVm = true }
                }
                if (pairingAccess.allows("backups")) SettingsSection(stringResource(R.string.android_settings_backups_530cc2)) {
                    SettingsButton(stringResource(R.string.android_settings_export_encrypted_workspace_backup_484354)) { exportingBackup = true }
                    SettingsButton(stringResource(R.string.android_settings_import_and_restore_a_workspace_backup_291447)) { restoringBackup = true }
                }
                if (pairingAccess.allows("browser")) SettingsSection(stringResource(R.string.android_settings_browser_54a2cf)) {
                    SettingsButton(stringResource(R.string.android_settings_built_in_browser_on_this_computer_fca83a)) { editingHostBrowser = true }
                    SettingsButton(stringResource(R.string.android_settings_manage_browser_profiles_b7690f)) { managingBrowserProfiles = true }
                }
                if (pairingAccess.allows("skills")) SettingsSection(stringResource(R.string.android_settings_experimental_b718f8)) {
                    SettingsButton(stringResource(R.string.android_settings_bot_skill_authoring_25b0fc)) { editingSkillAuthoring = true }
                }
            }

            // Routine schedules live on the computer this phone is bound to.
            // With no binding there is nothing to schedule against, so the row
            // is absent rather than present and dead.
            if (onOpenRoutines != null || onOpenConnectedApps != null) {
                SettingsSection(stringResource(R.string.android_settings_workspace_4ca0a7)) {
                    onOpenRoutines?.takeIf { pairingAccess.allows("routines") || pairingAccess.allows("routineRun") }?.let { openRoutines ->
                        SettingsButton(
                            text = stringResource(R.string.ui_threads_routines_65d7efc),
                            icon = R.drawable.ic_schedule,
                            onClick = openRoutines,
                        )
                    }
                    onOpenConnectedApps?.takeIf { pairingAccess.allows("connectors") }?.let { openConnectedApps ->
                        SettingsButton(
                            text = stringResource(R.string.ui_connected_apps_8ab72a8),
                            onClick = openConnectedApps,
                        )
                    }
                    Footnote(stringResource(R.string.android_settings_workspace_help))
                }
            }

            if (connection != null) {
                SettingsSection(null) {
                    SettingsButton(
                        text = stringResource(if (connections.size > 1) R.string.ui_remove_this_computer
                            else R.string.ui_unpair_this_phone),
                        destructive = true,
                    ) { confirmingUnpair = true }
                    Footnote(stringResource(R.string.android_settings_unpair_help))
                }
            }

            SettingsSection(stringResource(R.string.android_settings_not_here_1f3909)) {
                Footnote(stringResource(R.string.android_settings_not_here_help))
            }
        }
    }

    if (editingAddress) {
        AlertDialog(
            onDismissRequest = { editingAddress = false },
            title = { Text(stringResource(R.string.ui_edit_address_31fe67f)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.android_settings_edit_address_help), fontSize = 14.sp)
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
                            addressError = invalidAddressError
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
            title = { Text(if (connections.size > 1) stringResource(R.string.ui_remove_computer_confirm, connection?.name.orEmpty())
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
                                    ?: throw IllegalStateException(verifyProfileError)
                                if (current.aboutMe.orEmpty() != aboutMeOriginal || current.name != profileNameOriginal ||
                                    current.email != profileEmailOriginal) {
                                    throw IllegalStateException(profileChangedError)
                                }
                                val saved = session.updateSharedProfile(profileName, profileEmail, aboutMeText).profile
                                if (saved?.aboutMe.orEmpty() != aboutMeText || saved?.name != profileName || saved?.email != profileEmail)
                                    throw IllegalStateException(profileNotConfirmedError)
                                editingAboutMe = false
                            } catch (error: Exception) {
                                aboutMeError = error.message ?: saveProfileError
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
internal fun PairingAccessDetails(pairingAccess: PairingAccessState, onRefresh: suspend () -> PairingAccessState) {
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var showingDetails by remember { mutableStateOf(false) }
    val updated = stringResource(R.string.pairing_access_updated)
    val changed = stringResource(R.string.pairing_access_updated_changed)
    val unchanged = stringResource(R.string.pairing_access_updated_unchanged)
    val ready = (pairingAccess as? PairingAccessState.Ready)?.access
    val role = when (ready?.role) {
        "admin" -> stringResource(R.string.android_settings_full_access)
        "client" -> stringResource(R.string.android_settings_chat_approvals)
        "custom" -> stringResource(R.string.pairing_access_custom)
        else -> stringResource(if (pairingAccess is PairingAccessState.Failed) R.string.pairing_access_failed else R.string.pairing_access_checking)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.android_settings_pairing_access_a10032), modifier = Modifier.weight(1f), fontSize = 15.sp)
        Text(role, fontSize = 15.sp)
        PermissionHelp(stringResource(R.string.pairing_access_mode_help))
    }
    SettingsButton(stringResource(R.string.pairing_access_details)) { showingDetails = true }
    SettingsButton(
        text = if (refreshing) stringResource(R.string.pairing_access_refreshing) else stringResource(R.string.pairing_access_refresh),
        enabled = !refreshing,
        trailing = { if (refreshing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) },
    ) {
        // Set busy synchronously, before the coroutine starts or a second tap arrives.
        refreshing = true
        confirmation = null
        failure = null
        val previous = ready
        scope.launch {
            try {
                when (val result = onRefresh()) {
                    is PairingAccessState.Ready -> confirmation = (if (result.access == previous) unchanged else changed) + " · " + DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date())
                    is PairingAccessState.Failed -> failure = result.reason
                    PairingAccessState.Checking -> failure = "No confirmed permission response was received."
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { failure = error.message ?: "Permission refresh failed." }
            finally { refreshing = false }
        }
    }
    AnimatedContent(confirmation, label = "permission-refresh-feedback") { message ->
        if (message != null) Text("✓ $updated: $message", fontSize = 13.sp)
    }
    val error = failure ?: (pairingAccess as? PairingAccessState.Failed)?.reason
    if (error != null) Text(stringResource(R.string.pairing_access_error, error), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
    if (showingDetails) Dialog(onDismissRequest = { showingDetails = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.material3.Surface(modifier = Modifier.fillMaxSize().padding(16.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.pairing_access_details), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    TextButton(onClick = { showingDetails = false }) { Text(stringResource(R.string.pairing_access_close)) }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (ready == null) Text(role)
                    else if (ready.capabilities.isEmpty()) Text(stringResource(R.string.pairing_access_catalog_missing))
                    else ready.capabilities.forEach { grant ->
                        val de = Locale.getDefault().language == "de"
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (de) grant.labelDe else grant.labelEn, modifier = Modifier.weight(1f), fontSize = 15.sp)
                            Text(stringResource(if (grant.allowed) R.string.pairing_access_allowed else R.string.pairing_access_denied), modifier = Modifier.padding(start = 8.dp), fontSize = 14.sp)
                            PermissionHelp(if (de) grant.descriptionDe else grant.descriptionEn, if (de) grant.labelDe else grant.labelEn)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionHelp(explanation: String, title: String? = null) {
    var open by remember { mutableStateOf(false) }
    val description = stringResource(R.string.pairing_access_help) + (title?.let { ": $it" } ?: "")
    TextButton(onClick = { open = true }, modifier = Modifier.semantics { contentDescription = description }) { Text("?") }
    if (open) AlertDialog(onDismissRequest = { open = false }, text = { Text(explanation) }, confirmButton = {
        TextButton(onClick = { open = false }) { Text(stringResource(R.string.pairing_access_close)) }
    })
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
    PermissionHelp(text)
}

private const val ADDRESS_CLIP_LABEL = "OpenMausMobile computer address"

/** Long enough for "Copied" to be read, short enough not to linger (iOS `:363-367`). */
private const val COPIED_LABEL_MILLIS = 2_000L

@Composable
private fun localizedConnectionStatus(status: Session.Status): String = when (status) {
    Session.Status.Live -> stringResource(R.string.android_settings_connected)
    Session.Status.Connecting -> stringResource(R.string.android_settings_connecting)
    Session.Status.Unpaired -> stringResource(R.string.android_settings_not_paired)
    Session.Status.Unauthorized -> stringResource(R.string.android_settings_unpaired_on_computer)
    is Session.Status.Offline -> status.message
}

@Composable
private fun localizedTroubleshooting(status: Session.Status): String {
    if (status is Session.Status.Offline) return status.message
    return stringResource(when (status) {
    Session.Status.Live -> R.string.android_settings_troubleshoot_live
    Session.Status.Connecting -> R.string.android_settings_troubleshoot_connecting
    Session.Status.Unauthorized -> R.string.android_settings_troubleshoot_unauthorized
    Session.Status.Unpaired -> R.string.android_settings_troubleshoot_unpaired
    is Session.Status.Offline -> error("handled above")
    })
}

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
