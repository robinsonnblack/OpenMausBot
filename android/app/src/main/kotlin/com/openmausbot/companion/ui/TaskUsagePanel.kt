package com.openmausbot.companion.ui

import com.openmausbot.companion.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.core.TaskUsage
import java.text.NumberFormat
import java.util.Locale

/** Keep the provider's three token classes separate; unknown cache accounting is not zero. */
internal fun taskUsageLines(usage: TaskUsage, locale: Locale = Locale.getDefault()): List<String> {
    val number = NumberFormat.getIntegerInstance(locale)
    val lines = mutableListOf<String>()
    val cached = usage.cachedInput?.takeIf { it >= 0 && it <= usage.input }
    if (cached == null) {
        lines += "Input: ${number.format(usage.input)} (cache split unavailable)"
    } else {
        lines += "Uncached input: ${number.format(usage.input - cached)}"
        lines += "Cached input: ${number.format(cached)}"
    }
    lines += "Output: ${number.format(usage.output)}"
    usage.costUsd?.takeIf { it.isFinite() && it >= 0 }?.let {
        lines += "Reported cost: ${String.format(Locale.US, "$%.4f", it)}"
    }
    usage.context?.let { context ->
        val window = context.window
        if (window != null && window > 0) {
            lines += "Context: ${number.format(context.tokens)} / ${number.format(window)}"
        }
    }
    return lines
}

@Composable
private fun localizedTaskUsageLines(usage: TaskUsage): List<String> {
    val number = NumberFormat.getIntegerInstance(Locale.getDefault())
    val cached = usage.cachedInput?.takeIf { it >= 0 && it <= usage.input }
    val lines = mutableListOf<String>()
    if (cached == null) lines += stringResource(R.string.android_usage_input_unknown, number.format(usage.input))
    else {
        lines += stringResource(R.string.android_usage_uncached_input, number.format(usage.input - cached))
        lines += stringResource(R.string.android_usage_cached_input, number.format(cached))
    }
    lines += stringResource(R.string.android_usage_output, number.format(usage.output))
    usage.costUsd?.takeIf { it.isFinite() && it >= 0 }?.let {
        lines += stringResource(R.string.android_usage_reported_cost, String.format(Locale.US, "$%.4f", it))
    }
    usage.context?.let { context ->
        val window = context.window
        if (window != null && window > 0) lines += stringResource(R.string.android_usage_context,
            number.format(context.tokens), number.format(window))
    }
    return lines
}

@Composable
internal fun TaskUsagePanel(usage: TaskUsage) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(stringResource(R.string.ui_this_thread_s_usage_9245e98), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        localizedTaskUsageLines(usage).forEach { line ->
            Text(line, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
