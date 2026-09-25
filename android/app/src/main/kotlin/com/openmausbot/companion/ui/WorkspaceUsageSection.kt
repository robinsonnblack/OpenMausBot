package com.openmausbot.companion.ui

import androidx.compose.ui.platform.LocalContext
import com.openmausbot.companion.R
import androidx.compose.ui.res.stringResource
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
    val l10n = LocalContext.current
    val session = LocalCompanion.current.session
    var period by remember { mutableStateOf("this-month") }
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
                "last-month" -> today.minusMonths(1).withDayOfMonth(1)
                "last-30-days" -> today.minusDays(29)
                else -> today.withDayOfMonth(1)
            }
            val to = if (period == "last-month") today.withDayOfMonth(1).minusDays(1) else today
            report = session.workspaceUsage(from.toString(), to.toString(), grouping)
        } catch (failure: Exception) {
            report = null
            error = failure.message ?: l10n.getString(R.string.android_remaining_workspace_usage_section_36879644)
        } finally { loading = false }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.ui_usage_recorded_by_the_paired_computer_cost_c072280))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(
                "this-month" to R.string.android_remaining_workspace_usage_section_1b478533,
                "last-month" to R.string.android_remaining_workspace_usage_section_9cce45bf,
                "last-30-days" to R.string.android_remaining_workspace_usage_section_6b329852,
            ).forEach { (choice, labelId) ->
                val label = stringResource(labelId)
                TextButton(onClick = { period = choice }) {
                    Text(if (period == choice) "✓ $label" else label)
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
            Text(stringResource(R.string.ui_dynamic_total_1_s_turns_2_s_input_3_s_cached_4_71addfc, usage.total.turns, usage.total.input, usage.total.cachedInput, usage.total.output), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.ui_usage_cost, usage.total.costUsd.money()) +
                if ((usage.total.estimatedUsd ?: 0.0) > 0) stringResource(R.string.ui_usage_includes_estimates) else "")
            if (usage.total.unpriced > 0) Text(stringResource(R.string.ui_dynamic_1_s_unpriced_turn_s_are_excluded_from_187562f, usage.total.unpriced))
            usage.budget?.takeIf { it.monthlyUsd > 0 }?.let { budget ->
                Text(stringResource(R.string.ui_dynamic_monthly_limit_1_s_of_2_s_afbe88f, budget.spentUsd.money(), budget.monthlyUsd.money()))
            }
            usage.groups.forEach { group ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(group.label, style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.ui_dynamic_1_s_turns_2_s_input_3_s_cached_4_s_out_8e3f5b3, group.turns, group.input, group.cachedInput, group.output))
                    Text(stringResource(R.string.ui_usage_cost, group.costUsd.money()) +
                        if (group.unpriced > 0) stringResource(R.string.ui_usage_unpriced_count, group.unpriced) else "")
                }
            }
        }
    }
}

private fun Double?.money(): String = this?.let { String.format(Locale.US, "$%.4f", it) } ?: "unknown"
