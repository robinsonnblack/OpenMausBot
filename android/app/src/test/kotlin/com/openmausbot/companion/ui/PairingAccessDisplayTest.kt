package com.openmausbot.companion.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.openmausbot.companion.core.PairingAccess
import com.openmausbot.companion.core.PairingAccessState
import com.openmausbot.companion.core.PairingPermissions
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PairingAccessDisplayTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun fullAccessShowsEachAllowedActionAndTheSeparateBlockedDesktopPermission() {
        val rights = PairingAccess("admin", listOf("admin", "client"), PairingPermissions(true, true, true, true, true, false))
        compose.setContent { CompanionTheme(darkTheme = false) { Column { PairingAccessDetails(PairingAccessState.Ready(rights)) {} } } }
        compose.onNodeWithText("Full access").assertExists()
        compose.onNodeWithText("Advanced bot management").assertExists()
        compose.onNodeWithText("Workspace administration").assertExists()
        compose.onNodeWithText("Cloud desktop control").assertExists()
        compose.onAllNodesWithText("Allowed").assertCountEquals(5)
        compose.onAllNodesWithText("Blocked").assertCountEquals(1)
    }

    @Test fun restrictedAccessShowsBlockedAdministrationRatherThanUnknownPairing() {
        val rights = PairingAccess("client", listOf("client"), PairingPermissions(true, true, true, false, false, false))
        compose.setContent { CompanionTheme(darkTheme = false) { Column { PairingAccessDetails(PairingAccessState.Ready(rights)) {} } } }
        compose.onNodeWithText("Full access").assertDoesNotExist()
        compose.onNodeWithText("Unknown (older pairing)").assertDoesNotExist()
        compose.onAllNodesWithText("Allowed").assertCountEquals(3)
        compose.onAllNodesWithText("Blocked").assertCountEquals(3)
    }

    @Test fun fetchFailureShowsTheCauseAndRefreshButtonCallsItsAction() {
        var requests = 0
        compose.setContent { CompanionTheme(darkTheme = false) { Column { PairingAccessDetails(PairingAccessState.Failed("Desktop refused the connection: token revoked")) { requests += 1 } } } }
        compose.onNodeWithText("Rights could not be retrieved").assertExists()
        compose.onNodeWithText("token revoked", substring = true).assertExists()
        compose.onNodeWithText("Full access").assertDoesNotExist()
        compose.onNodeWithText("Refresh rights").performClick()
        assertEquals(1, requests)
    }
}
