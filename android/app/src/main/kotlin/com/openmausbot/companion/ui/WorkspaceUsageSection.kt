package com.openmausbot.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.core.WorkspaceUsage
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/** The paired computer's read-only usage ledger. Admin pairing scope is required. */
@Composable
internal fun WorkspaceUsageSection() {
    val session = LocalCompanion.current.session
    var period by remember { mutableStateOf("This month") }
    var grouping by remember { mutableStateOf("bot") }
    var report by remember { mutableStateOf<WorkspaceUsage?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(period, grouping) {
        loading = true
        error = null
        try {
            val today = LocalDate.now(ZoneOffset.UTC)
            val from = when (period) {
                "Last month" -> today.minusMonths(1).withDayOfMonth(1)
                "Last 30 days" -> today.minusDays(29)
                else -> today.withDayOfMonth(1)
            }
            val to = if (period == "Last month") today.withDayOfMonth(1).minusDays(1) else today
            report = session.workspaceUsage(from.toString(), to.toString(), grouping)
        } catch (failure: Exception) {
            report = null
            error = failure.message ?: "Could not load usage. This view requires admin access to the paired computer."
        } finally { loading = false }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Usage recorded by the paired computer. Costs may be reported or estimated; unpriced turns have no cost estimate.")
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf("This month", "Last month", "Last 30 days").forEach { choice ->
                TextButton(onClick = { period = choice }) {
                    Text(if (period == choice) "✓ $choice" else choice)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf("bot", "model", "day").forEach { choice ->
                TextButton(onClick = { grouping = choice }) {
                    Text(if (grouping == choice) "✓ $choice" else choice)
                }
            }
        }
        if (loading) CircularProgressIndicator()
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!loading) report?.let { usage ->
            Text("Total: ${usage.total.turns} turns · ${usage.total.input} input (${usage.total.cachedInput} cached) · ${usage.total.output} output", style = MaterialTheme.typography.titleSmall)
            Text("Cost: ${usage.total.costUsd.money()}${if ((usage.total.estimatedUsd ?: 0.0) > 0) " (includes estimates)" else ""}")
            if (usage.total.unpriced > 0) Text("${usage.total.unpriced} unpriced turn(s) are excluded from cost.")
            usage.budget?.takeIf { it.monthlyUsd > 0 }?.let { budget ->
                Text("Monthly limit: ${budget.spentUsd.money()} of ${budget.monthlyUsd.money()}")
            }
            usage.groups.forEach { group ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(group.label, style = MaterialTheme.typography.titleSmall)
                    Text("${group.turns} turns · ${group.input} input (${group.cachedInput} cached) · ${group.output} output")
                    Text("Cost: ${group.costUsd.money()}${if (group.unpriced > 0) " · ${group.unpriced} unpriced" else ""}")
                }
            }
        }
    }
}

private fun Double?.money(): String = this?.let { String.format(Locale.US, "$%.4f", it) } ?: "unknown"
