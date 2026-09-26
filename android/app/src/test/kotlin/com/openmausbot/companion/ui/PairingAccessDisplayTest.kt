package com.openmausbot.companion.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.openmausbot.companion.core.PairingAccess
import com.openmausbot.companion.core.PairingAccessState
import com.openmausbot.companion.core.PairingPermissions
import com.openmausbot.companion.core.PairingCapability
import kotlinx.coroutines.CompletableDeferred
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
    private val rights = PairingAccess("custom", listOf("admin", "client"), PairingPermissions(true, true, false, false, false, true),
        listOf(PairingCapability("threadDelete", "Threads löschen", "Delete threads", "Threads dauerhaft löschen.", "Permanently delete threads.", false),
            PairingCapability("approvals", "Bot-Aktionen bestätigen", "Approve bot actions", "Bestätigungsanfragen beantworten.", "Answer bot approval requests.", true)))

    @Test fun compactScreenHidesCatalogueAndExplanationsUntilRequested() {
        compose.setContent { CompanionTheme(darkTheme = false) { Column { PairingAccessDetails(PairingAccessState.Ready(rights)) { PairingAccessState.Ready(rights) } } } }
        compose.onNodeWithText("Custom").assertExists()
        compose.onNodeWithText("Delete threads").assertDoesNotExist()
        compose.onNodeWithText("Permanently delete threads.").assertDoesNotExist()
        compose.onNodeWithText("View all permissions").performClick()
        compose.onNodeWithText("Delete threads").assertExists()
        compose.onNodeWithText("Approve bot actions").assertExists()
        compose.onNodeWithText("Blocked").assertExists()
        compose.onNodeWithText("Allowed").assertExists()
        compose.onNodeWithText("Permanently delete threads.").assertDoesNotExist()
        compose.onNodeWithContentDescription("Explanation: Delete threads").performClick()
        compose.onNodeWithText("Permanently delete threads.").assertExists()
    }

    @Test fun refreshShowsBusyAndConfirmsAnIdenticalResponseOnEveryTap() {
        val replies = listOf(CompletableDeferred<PairingAccessState>(), CompletableDeferred<PairingAccessState>())
        var requests = 0
        compose.setContent { CompanionTheme(darkTheme = false) { Column { PairingAccessDetails(PairingAccessState.Ready(rights)) { replies[requests++].await() } } } }
        compose.onNodeWithText("Refresh rights").performClick()
        compose.onNodeWithText("Refreshing rights …").assertIsNotEnabled()
        assertEquals(1, requests)
        compose.runOnIdle { replies[0].complete(PairingAccessState.Ready(rights)) }
        compose.waitForIdle()
        compose.onNodeWithText("rights unchanged", substring = true).assertExists()
        compose.onNodeWithText("Refresh rights").performClick()
        compose.onNodeWithText("Refreshing rights …").assertIsNotEnabled()
        assertEquals(2, requests)
        compose.runOnIdle { replies[1].complete(PairingAccessState.Ready(rights)) }
        compose.waitForIdle()
        compose.onNodeWithText("rights unchanged", substring = true).assertExists()
    }

    @Test fun failureShowsConcreteReasonAndNeverClaimsSuccess() {
        compose.setContent { CompanionTheme(darkTheme = false) { Column { PairingAccessDetails(PairingAccessState.Ready(rights)) { PairingAccessState.Failed("Desktop refused: token revoked") } } } }
        compose.onNodeWithText("Refresh rights").performClick()
        compose.onNodeWithText("token revoked", substring = true).assertExists()
        compose.onNodeWithText("Rights refreshed", substring = true).assertDoesNotExist()
    }
}
