package com.openmausbot.companion.ui

import com.openmausbot.companion.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.core.WorkspaceBackupStatus
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** The archive stays encrypted; the transfer streams straight into a user-chosen document. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkspaceBackupExportSheet(onDismiss: () -> Unit) {
    val session = LocalCompanion.current.session
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loadError = stringResource(R.string.android_backup_load_error)
    val busyError = stringResource(R.string.android_backup_busy_error)
    val createFileError = stringResource(R.string.android_backup_create_file_error)
    val incompleteError = stringResource(R.string.android_backup_incomplete_error)
    val exportError = stringResource(R.string.android_backup_export_error)
    var status by remember { mutableStateOf<WorkspaceBackupStatus?>(null) }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var savedBytes by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        try { status = session.workspaceBackupStatus() }
        catch (failure: Exception) { error = failure.message ?: loadError }
    }
    val createFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        error = null
        savedBytes = null
        scope.launch {
            try {
                val current = session.workspaceBackupStatus()
                if (current.busy || current.pendingRestore) throw IllegalStateException(busyError)
                val exported = session.createWorkspaceBackup(password)
                password = ""
                confirmation = ""
                val output = context.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException(createFileError)
                val written = output.use { session.downloadWorkspaceBackup(exported.id, it) }
                if (written != exported.bytes) throw IllegalStateException(incompleteError)
                savedBytes = written
                status = session.workspaceBackupStatus()
            } catch (failure: Exception) {
                runCatching { context.contentResolver.delete(uri, null, null) }
                if (failure is CancellationException) throw failure
                error = failure.message ?: exportError
            } finally {
                password = ""
                confirmation = ""
                busy = false
            }
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 660.dp)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.ui_export_workspace_backup_7f9b140), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.ui_creates_an_encrypted_copy_of_the_paired_co_3b6819c),
                style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.ui_use_a_secure_https_or_tailscale_connection_0418ff3),
                style = MaterialTheme.typography.bodySmall)
            status?.let { current ->
                if (current.pendingRestore) Text(stringResource(R.string.ui_a_restore_is_pending_restart_the_computer_f6b7f8f))
                else if (current.busy) Text(stringResource(R.string.ui_another_backup_operation_is_in_progress_on_de27387))
            }
            OutlinedTextField(password, onValueChange = { password = it.take(1024) },
                label = { Text(stringResource(R.string.ui_new_backup_password_at_least_12_characters_6be1ab3)) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(), enabled = !busy)
            OutlinedTextField(confirmation, onValueChange = { confirmation = it.take(1024) },
                label = { Text(stringResource(R.string.ui_confirm_password_4a7c565)) }, visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(), enabled = !busy)
            if (busy) {
                CircularProgressIndicator()
                Text(stringResource(R.string.ui_creating_or_downloading_the_backup_keep_th_6e4ab95))
            }
            savedBytes?.let { Text(stringResource(R.string.ui_dynamic_backup_saved_1_s_bytes_b0e8870, it)) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(
                enabled = !busy && status?.busy == false && status?.pendingRestore == false &&
                    password.length in 12..1024 && password == confirmation,
                onClick = { createFile.launch("OpenMausBot-${LocalDate.now()}.ombbackup") },
            ) { Text(stringResource(R.string.ui_choose_file_and_export_fed6a25)) }
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.ui_done_e9b450d)) }
        }
    }
}
