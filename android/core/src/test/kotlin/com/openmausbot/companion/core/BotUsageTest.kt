package com.openmausbot.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BotUsageTest {
    private fun task(id: String, usage: TaskUsage?) = BotTask(id, id, 1.0, usage = usage)

    @Test
    fun sumsTurnsTokensCacheAndCostAcrossThreads() {
        val summary = summarizeBotUsage(listOf(
            task("one", TaskUsage(1_000, 40, cachedInput = 600, costUsd = 0.01, turns = 2)),
            task("two", TaskUsage(2_000, 80, cachedInput = 1_500, costUsd = 0.02, turns = 3)),
            task("empty", null),
        ))
        assertEquals(3_000, summary.usage.input)
        assertEquals(120, summary.usage.output)
        assertEquals(2_100, summary.usage.cachedInput)
        assertEquals(5, summary.usage.turns)
        assertEquals(0.03, summary.usage.costUsd!!, 0.000001)
        assertFalse(summary.hasUnpricedTurns)
    }

    @Test
    fun partialAccountingDoesNotClaimMissingCacheOrPriceAreZero() {
        val summary = summarizeBotUsage(listOf(
            task("known", TaskUsage(1_000, 40, cachedInput = 600, costUsd = 0.01, turns = 1)),
            task("unknown", TaskUsage(900, 20, turns = 1)),
        ))
        assertNull(summary.usage.cachedInput)
        assertEquals(0.01, summary.usage.costUsd)
        assertTrue(summary.hasUnpricedTurns)
    }
}
