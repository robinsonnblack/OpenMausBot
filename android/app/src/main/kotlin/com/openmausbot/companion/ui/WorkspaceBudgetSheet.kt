package com.openmausbot.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.core.WorkspaceBudgetConfig
import kotlinx.coroutines.launch

/** Mirrors the entitled host budget; this is a spend cap, not a phone-local setting. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkspaceBudgetSheet(onDismiss: () -> Unit) {
    val session = LocalCompanion.current.session
    val scope = rememberCoroutineScope()
    var baseline by remember { mutableStateOf<WorkspaceBudgetConfig?>(null) }
    var monthly by remember { mutableStateOf("") }
    var warnAt by remember { mutableStateOf("80") }
    var busy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val status = session.configStatus() ?: throw IllegalStateException("Could not load the computer's settings.")
            if (status.edition?.features?.contains("budgets") != true) {
                throw IllegalStateException("This computer does not have the workspace budget feature.")
            }
            val current = status.budgets ?: WorkspaceBudgetConfig()
            baseline = current
            monthly = if (current.monthlyUsd == 0.0) "" else current.monthlyUsd.toString()
            warnAt = current.warnAtPercent.toString()
        } catch (failure: Exception) { error = failure.message ?: "Could not load the spending limit." }
        finally { loading = false }
    }

    fun save() {
        val amount = if (monthly.isBlank()) 0.0 else monthly.toDoubleOrNull()
        val percent = warnAt.toIntOrNull()
        if (amount == null || !amount.isFinite() || amount !in 0.0..1_000_000.0 || percent == null || percent !in 1..100) {
            error = "Enter a monthly USD limit from 0 to 1,000,000 and a warning threshold from 1 to 100%."
            return
        }
        val expected = baseline ?: return
        busy = true
        error = null
        scope.launch {
            try {
                val latest = session.configStatus() ?: throw IllegalStateException("Could not recheck the computer's settings.")
                if (latest.edition?.features?.contains("budgets") != true ||
                    (latest.budgets ?: WorkspaceBudgetConfig()) != expected) {
                    throw IllegalStateException("The budget changed on the computer. Reopen this screen before saving.")
                }
                val requested = WorkspaceBudgetConfig(amount, percent)
                val saved = session.updateWorkspaceBudget(requested)
                if (saved.budgets != requested) throw IllegalStateException("The computer did not confirm the saved limit.")
                baseline = requested
                onDismiss()
            } catch (failure: Exception) { error = failure.message ?: "Could not save the spending limit." }
            finally { busy = false }
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Monthly spending limit", style = MaterialTheme.typography.titleLarge)
            Text("This limit applies to the paired computer's entire workspace. It uses costs recorded by the host; turns without a price may not count toward it.",
                style = MaterialTheme.typography.bodySmall)
            if (loading || busy) CircularProgressIndicator()
            if (!loading && baseline != null) {
                OutlinedTextField(monthly, onValueChange = { monthly = it.take(20); error = null },
                    label = { Text("Monthly limit in USD (blank = no limit)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = !busy, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(warnAt, onValueChange = { warnAt = it.take(3); error = null },
                    label = { Text("Warn at percent") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !busy, modifier = Modifier.fillMaxWidth())
                TextButton(enabled = !busy, onClick = ::save) { Text("Save workspace limit") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Done") }
        }
    }
}
