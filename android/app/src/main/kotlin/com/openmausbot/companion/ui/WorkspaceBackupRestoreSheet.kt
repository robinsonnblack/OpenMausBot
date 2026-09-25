package com.openmausbot.companion.ui

import com.openmausbot.companion.R
import androidx.compose.ui.res.stringResource
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.core.WorkspaceBackupPreview
import com.openmausbot.companion.core.WorkspaceBackupStatus
import kotlinx.coroutines.launch

/** The phone stages an archive on the paired computer; only REPLACE commits a restore. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkspaceBackupRestoreSheet(onDismiss: () -> Unit) {
    val session = LocalCompanion.current.session
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<WorkspaceBackupStatus?>(null) }
    var selected by remember { mutableStateOf<Uri?>(null) }
    var filename by remember { mutableStateOf("") }
    var fileBytes by remember { mutableStateOf<Long?>(null) }
    var uploadedId by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<WorkspaceBackupPreview?>(null) }
    var confirmation by remember { mutableStateOf("") }
    var confirmingRestore by remember { mutableStateOf(false) }
    var pendingRestart by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try { status = session.workspaceBackupStatus() }
        catch (failure: Exception) { error = failure.message ?: "Could not load backup status." }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        selected = uri
        preview = null
        uploadedId = null
        confirmation = ""
        password = ""
        error = null
        filename = ""
        fileBytes = null
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    filename = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else ""
                    fileBytes = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
                }
            }
        } catch (failure: Exception) { error = failure.message ?: "Could not inspect selected file." }
        if (!filename.endsWith(".ombbackup", ignoreCase = true)) error = "Choose an .ombbackup file."
        if (fileBytes == null || fileBytes!! <= 0L) error = "The selected document did not report a usable file size."
    }

    fun startPreview() {
        val uri = selected ?: return
        val bytes = fileBytes ?: return
        busy = true
        error = null
        scope.launch {
            try {
                val current = session.workspaceBackupStatus()
                if (current.busy || current.pendingRestore) throw IllegalStateException("The computer is busy with a backup or restore.")
                val id = uploadedId ?: session.uploadWorkspaceBackup(bytes) {
                    context.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Could not open the selected backup file.")
                }.id.also { uploadedId = it }
                preview = session.previewWorkspaceBackup(id, password)
                uploadedId = null
                password = ""
            } catch (failure: Exception) {
                error = failure.message ?: "Could not validate the backup. Select it again if the upload expired."
            } finally { busy = false }
        }
    }

    fun restore() {
        val stage = preview ?: return
        confirmingRestore = false
        busy = true
        error = null
        scope.launch {
            try {
                val result = session.restoreWorkspaceBackup(stage.id)
                if (!result.restartRequired || result.id != stage.id) throw IllegalStateException("The computer did not confirm the restore.")
                preview = null
                confirmation = ""
                pendingRestart = true
            } catch (failure: Exception) {
                error = failure.message ?: "Could not restore the backup."
            } finally { busy = false }
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.ui_import_workspace_backup_7a4b7cc), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.ui_this_can_replace_all_workspace_data_on_the_d5973d7),
                style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.ui_use_https_or_tailscale_keep_this_screen_op_08e5ec2),
                style = MaterialTheme.typography.bodySmall)
            if (status?.pendingRestore == true || pendingRestart) {
                Text(stringResource(R.string.ui_restore_committed_restart_openmausbot_on_t_2cf7bbd))
            } else {
                TextButton(enabled = !busy && status?.busy == false, onClick = {
                    picker.launch(arrayOf("application/octet-stream", "*/*"))
                }) { Text(stringResource(R.string.ui_choose_ombbackup_file_ea3cf5a)) }
                if (selected != null) Text(if (fileBytes == null) {
                    stringResource(R.string.ui_backup_selected_file, filename)
                } else {
                    stringResource(R.string.ui_backup_selected_file_bytes, filename, fileBytes ?: 0L)
                })
                if (preview == null) {
                    OutlinedTextField(password, onValueChange = { password = it.take(1024); error = null },
                        label = { Text(stringResource(R.string.ui_backup_password_d96a607)) }, visualTransformation = PasswordVisualTransformation(),
                        enabled = !busy, modifier = Modifier.fillMaxWidth())
                    TextButton(enabled = !busy && selected != null && fileBytes != null &&
                        filename.endsWith(".ombbackup", ignoreCase = true) && password.length in 12..1024,
                        onClick = ::startPreview) { Text(stringResource(R.string.ui_upload_and_validate_cb162f1)) }
                }
                preview?.let { staged ->
                    val summary = staged.summary
                    Text(stringResource(R.string.ui_dynamic_backup_from_1_s_openmausbot_2_s_2e6a715, summary.createdAt, summary.appVersion), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.ui_dynamic_1_s_bots_2_s_groups_3_s_threads_4_s_me_8a1f5f2, summary.bots, summary.groups, summary.threads, summary.messages))
                    Text(stringResource(R.string.ui_dynamic_1_s_files_2_s_bytes_3ebbf28, summary.files, summary.bytes))
                    summary.exclusions.forEach { Text(stringResource(R.string.ui_dynamic_excluded_1_s_0335dd8, it), style = MaterialTheme.typography.bodySmall) }
                    summary.warnings.forEach { Text(stringResource(R.string.ui_dynamic_warning_1_s_c0ecd56, it), color = MaterialTheme.colorScheme.error) }
                    Text(stringResource(R.string.ui_restoring_replaces_the_computer_s_workspac_218a6c6),
                        color = MaterialTheme.colorScheme.error)
                    OutlinedTextField(confirmation, onValueChange = { confirmation = it.take(7) },
                        label = { Text(stringResource(R.string.ui_type_replace_to_confirm_0eae810)) }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth())
                    TextButton(enabled = !busy && confirmation == "REPLACE", onClick = { confirmingRestore = true }) {
                        Text(stringResource(R.string.ui_replace_computer_workspace_8bf7494), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (busy) { CircularProgressIndicator(); Text(stringResource(R.string.ui_working_on_the_paired_computer_635f578)) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.ui_done_e9b450d)) }
        }
    }

    if (confirmingRestore) AlertDialog(
        onDismissRequest = { confirmingRestore = false },
        title = { Text(stringResource(R.string.ui_replace_this_computer_s_workspace_7be159d)) },
        text = { Text(stringResource(R.string.ui_the_staged_backup_will_replace_existing_wo_eadcd90)) },
        confirmButton = { TextButton(onClick = ::restore) { Text(stringResource(R.string.ui_restore_now_1e4932d), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmingRestore = false }) { Text(stringResource(R.string.ui_cancel_77dfd21)) } },
    )
}
