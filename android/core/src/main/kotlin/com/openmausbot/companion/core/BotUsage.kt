package com.openmausbot.companion.core

/** Totals over the bot's task list, matching the desktop's per-bot Usage view. */
data class BotUsageSummary(val usage: TaskUsage, val hasUnpricedTurns: Boolean)

fun summarizeBotUsage(tasks: List<BotTask>?): BotUsageSummary {
    val usages = tasks.orEmpty().mapNotNull(BotTask::usage)
    val input = usages.sumOf(TaskUsage::input)
    val output = usages.sumOf(TaskUsage::output)
    val turns = usages.sumOf(TaskUsage::turns)
    // A partial split must not be displayed as if the missing cache count were zero.
    val cachedKnown = usages.none { it.input > 0 && (it.cachedInput == null || it.cachedInput !in 0..it.input) }
    val cached = if (cachedKnown) usages.sumOf { it.cachedInput ?: 0 } else null
    val prices = usages.mapNotNull { it.costUsd?.takeIf { cost -> cost.isFinite() && cost >= 0 } }
    val unpriced = usages.any { it.turns > 0 && (it.costUsd == null || !it.costUsd.isFinite() || it.costUsd < 0) }
    return BotUsageSummary(
        usage = TaskUsage(input, output, cached, if (prices.isNotEmpty()) prices.sum() else null, turns),
        hasUnpricedTurns = unpriced,
    )
}
