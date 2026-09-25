package com.openmausbot.companion.ui

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
    var status by remember { mutableStateOf<WorkspaceBackupStatus?>(null) }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var savedBytes by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        try { status = session.workspaceBackupStatus() }
        catch (failure: Exception) { error = failure.message ?: "Could not load backup status." }
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
                if (current.busy || current.pendingRestore) throw IllegalStateException("The computer is busy with a backup or restore.")
                val exported = session.createWorkspaceBackup(password)
                password = ""
                confirmation = ""
                val output = context.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Could not create the selected file.")
                val written = output.use { session.downloadWorkspaceBackup(exported.id, it) }
                if (written != exported.bytes) throw IllegalStateException("The backup download was incomplete.")
                savedBytes = written
                status = session.workspaceBackupStatus()
            } catch (failure: Exception) {
                runCatching { context.contentResolver.delete(uri, null, null) }
                if (failure is CancellationException) throw failure
                error = failure.message ?: "Could not export the workspace backup."
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
            Text("Export workspace backup", style = MaterialTheme.typography.titleLarge)
            Text("Creates an encrypted copy of the paired computer's workspace. Save the password separately; without it the backup cannot be restored.",
                style = MaterialTheme.typography.bodySmall)
            Text("Use a secure HTTPS or Tailscale connection. The encrypted file is written directly to the location you choose, without loading it into phone memory.",
                style = MaterialTheme.typography.bodySmall)
            status?.let { current ->
                if (current.pendingRestore) Text("A restore is pending. Restart the computer's app before making another backup.")
                else if (current.busy) Text("Another backup operation is in progress on the computer.")
            }
            OutlinedTextField(password, onValueChange = { password = it.take(1024) },
                label = { Text("New backup password (at least 12 characters)") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(), enabled = !busy)
            OutlinedTextField(confirmation, onValueChange = { confirmation = it.take(1024) },
                label = { Text("Confirm password") }, visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(), enabled = !busy)
            if (busy) {
                CircularProgressIndicator()
                Text("Creating or downloading the backup… Keep this screen open.")
            }
            savedBytes?.let { Text("Backup saved: $it bytes.") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(
                enabled = !busy && status?.busy == false && status?.pendingRestore == false &&
                    password.length in 12..1024 && password == confirmation,
                onClick = { createFile.launch("OpenMausBot-${LocalDate.now()}.ombbackup") },
            ) { Text("Choose file and export") }
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Done") }
        }
    }
}
