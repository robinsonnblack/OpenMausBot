package com.openmausbot.companion.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.openmausbot.companion.R
import com.openmausbot.companion.core.Routine
import com.openmausbot.companion.core.RoutineRun
import com.openmausbot.companion.core.RoutineRunLocation
import com.openmausbot.companion.core.RoutineSchedule
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun localizedRoutineSubtitle(routine: Routine, botName: String?): String {
    val schedule = routine.schedule
    val date = when (schedule.type) {
        RoutineSchedule.Kind.ONCE -> schedule.at?.let { RelativeStamp.dateAndTime(it) }
            ?: stringResource(R.string.android_routine_once_unavailable)
        RoutineSchedule.Kind.INTERVAL -> {
            val minutes = schedule.everyMinutes
            if (minutes == null) stringResource(R.string.android_routine_interval_unavailable)
            else {
                val cadence = stringResource(R.string.android_routine_every_minutes, minutes)
                schedule.anchorAt?.let {
                    stringResource(R.string.android_routine_starting_at, cadence, RelativeStamp.dateAndTime(it.toDouble()))
                } ?: cadence
            }
        }
        RoutineSchedule.Kind.UNKNOWN -> stringResource(R.string.android_routine_newer_schedule)
        RoutineSchedule.Kind.DAILY -> {
            val days = schedule.weekdays.orEmpty()
            val dayText = when {
                days.size == 7 -> stringResource(R.string.android_routine_every_day)
                days == listOf(1, 2, 3, 4, 5) -> stringResource(R.string.android_routine_weekdays)
                else -> days.mapNotNull { day ->
                    if (day !in 0..6) null else DayOfWeek.of(if (day == 0) 7 else day)
                        .getDisplayName(TextStyle.SHORT, Locale.getDefault())
                }.joinToString(", ")
            }
            stringResource(R.string.android_routine_days_at, dayText, schedule.time ?: "—")
        }
    }
    val location = stringResource(if (routine.runLocation == RoutineRunLocation.MAUS)
        R.string.android_routine_this_computer else R.string.android_routine_cloud_vm)
    return "${botName ?: stringResource(R.string.android_routine_deleted_agent)} · $date · $location"
}

@Composable
internal fun localizedRunSubtitle(run: RoutineRun, botName: String?): String =
    "${botName ?: stringResource(R.string.android_routine_deleted_agent)} · ${RelativeStamp.dateAndTime(run.scheduledFor)}"

@Composable
internal fun localizedRunStatus(status: String): String = stringResource(when (RoutineRules.runStatus(status)) {
    RoutineRules.RunStatus.RUNNING -> R.string.android_routine_running
    RoutineRules.RunStatus.COMPLETED -> R.string.android_routine_completed
    RoutineRules.RunStatus.WAITING -> R.string.android_routine_needs_you
    RoutineRules.RunStatus.FAILED -> R.string.android_routine_failed
    RoutineRules.RunStatus.CANCELLED -> R.string.android_routine_cancelled
    RoutineRules.RunStatus.PENDING -> R.string.android_routine_pending
})
