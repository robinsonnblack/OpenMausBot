package com.openmausbot.companion.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PairingCapabilityPolicyTest {
    @Test fun customNeverTreatsItsTransportAdminScopeAsPermissionToDelete() {
        val access = PairingAccessState.Ready(PairingAccess("custom", listOf("admin", "client"), PairingPermissions(true, true, false, false, false, false),
            listOf(PairingCapability("threadDelete", "Threads löschen", "Delete threads", "", "", false), PairingCapability("messageDelete", "Nachrichten löschen", "Delete messages", "", "", true))))
        assertFalse(access.allows("threadDelete"))
        assertTrue(access.allows("messageDelete"))
        assertFalse(access.allows("missing-future-capability"))
        assertFalse(PairingAccessState.Checking.allows("threadDelete"))
        assertFalse(PairingAccessState.Failed("Desktop unreachable").allows("threadDelete"))
    }
    @Test fun legacyChatGrantDoesNotExposeDestructiveActions() {
        val access = PairingAccessState.Ready(PairingAccess("client", listOf("client"), PairingPermissions(true, true, true, false, false, false)))
        assertTrue(access.allows("chatSend"))
        assertTrue(access.allows("approvals"))
        assertFalse(access.allows("messageDelete"))
        assertFalse(access.allows("threadDelete"))
    }
}
