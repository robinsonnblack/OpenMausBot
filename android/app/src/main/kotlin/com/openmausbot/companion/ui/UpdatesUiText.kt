package com.openmausbot.companion.ui

import android.content.Context
import com.openmausbot.companion.R

internal fun updateHeadline(context: Context, updates: List<ChatUpdate>): String {
    val first = updates.firstOrNull() ?: return context.getString(R.string.android_updates_all_quiet)
    val resource = when (first.kind) {
        UpdateKind.NEEDS_YOU -> R.string.android_updates_needs_you_name
        UpdateKind.WORKING -> R.string.android_updates_working_name
        UpdateKind.TO_REVIEW -> R.string.android_updates_has_update_name
    }
    return context.getString(resource, first.chat.name)
}

internal fun updateSubline(context: Context, updates: List<ChatUpdate>): String {
    val first = updates.firstOrNull() ?: return context.getString(R.string.android_updates_empty_title)
    val rest = updates.size - 1
    return when (rest) {
        0 -> updateLine(context, first.line).ifEmpty { " " }
        1 -> context.getString(R.string.android_updates_one_more)
        else -> context.getString(R.string.android_updates_more_count, rest)
    }
}

internal fun updateCount(context: Context, updates: List<ChatUpdate>): String =
    if (updates.isEmpty()) context.getString(R.string.android_updates_all_quiet)
    else context.getString(R.string.android_updates_active_count, updates.size)

internal fun updateSection(context: Context, kind: UpdateKind): String = context.getString(when (kind) {
    UpdateKind.NEEDS_YOU -> R.string.android_updates_section_needs_you
    UpdateKind.WORKING -> R.string.android_updates_section_working
    UpdateKind.TO_REVIEW -> R.string.android_updates_section_review
})

internal fun updateLine(context: Context, line: String): String = when {
    line == "Waiting on you" -> context.getString(R.string.android_updates_waiting_on_you)
    line == "Queued — waiting for an available slot" -> context.getString(R.string.android_updates_queued_one)
    line == "Working…" -> context.getString(R.string.android_updates_working)
    line == "Screenshot" -> context.getString(R.string.android_updates_screenshot)
    line.endsWith(" messages queued") && line.substringBefore(' ').toIntOrNull() != null ->
        context.getString(R.string.android_updates_queued_many, line.substringBefore(' ').toInt())
    else -> line
}
