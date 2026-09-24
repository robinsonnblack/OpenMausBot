package com.openmausbot.companion.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BotAccessLabelsTest {
    @Test fun accessLabelsPreserveServerModes() {
        assertEquals("Browser", computerAccessLabel("browser"))
        assertEquals("This computer", computerAccessLabel("local"))
        assertEquals("Cloud computer", computerAccessLabel("cloud"))
        assertEquals("Full access", approvalAccessLabel("full", false))
        assertEquals("Custom", approvalAccessLabel("custom", true))
        assertEquals("Ask", approvalAccessLabel(null, false))
        assertEquals("Not reported by this computer", approvalAccessLabel(null, null))
    }
}
