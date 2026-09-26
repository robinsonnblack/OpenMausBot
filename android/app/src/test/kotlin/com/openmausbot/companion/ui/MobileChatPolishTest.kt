package com.openmausbot.companion.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.core.*
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MobileChatPolishTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun composerUsesFullWidthAndKeepsItsHeightUntilTextNeedsAnotherLine() {
        compose.setContent {
            CompanionTheme(darkTheme = false) {
                var draft by remember { mutableStateOf("") }
                Composer(draft = draft, accessory = ComposerAccessory.NONE, commands = emptyList(), plusOpen = false,
                    dictationListening = false, dictationLocked = false, dictationError = null,
                    onTogglePlus = {}, onDraftChange = { draft = it }, onToggleDictation = {}, onSend = {},
                    onToggleHud = {}, onCloseHud = {}, onSelectCommand = {}, chips = emptyList(), onSelectChip = {},
                    attachments = emptyList(), sending = false, preparing = false, busy = false, engineCanSteer = false,
                    queuedSends = emptyList(), steering = false, onSteer = null, onCancelQueued = {}, onEditQueued = {}, openingFileName = null,
                    attachmentError = null, onRemoveAttachment = {}, onDismissError = {})
            }
        }
        compose.onNodeWithText("Your task").assertIsDisplayed()
        val field = compose.onNode(hasSetTextAction())
        val empty = field.fetchSemanticsNode().boundsInRoot
        assertTrue(empty.width >= 300, "buttons must not narrow the text field: $empty")
        screenshot("composer-empty")
        field.performTextInput("Hi")
        val typed = field.fetchSemanticsNode().boundsInRoot
        assertEquals(empty.height, typed.height)
        screenshot("composer-one-line")
        field.performTextReplacement("one\ntwo\nthree")
        assertTrue(field.fetchSemanticsNode().boundsInRoot.height > typed.height)
        screenshot("composer-multiline")
    }

    @Test @Config(sdk = [34], qualifiers = "de-w360dp-h640dp-mdpi")
    fun germanAvatarChoicesHaveEqualCardsAndUnclippedLabels() {
        compose.setContent { CompanionTheme(darkTheme = false) { Box(Modifier.width(320.dp)) { AvatarCropChoices(AvatarCrop.MASCOT) {} } } }
        val cards = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertEquals(4, cards.size)
        assertTrue(cards.all { it.boundsInRoot.width == cards.first().boundsInRoot.width && it.boundsInRoot.height == cards.first().boundsInRoot.height })
        compose.onNodeWithText("Maskottchen").assertIsDisplayed()
        compose.onNodeWithText("Abgerundet").assertIsDisplayed()
        screenshot("avatar-german")
    }

    @Test fun imageLimitEditorAcceptsLargerValuesAndShowsBusyThenPersistentSuccess() {
        val completed = CompletableDeferred<Unit>()
        var requested: ImageAttachmentSettings? = null
        compose.setContent { CompanionTheme(darkTheme = false) { ImageAttachmentSettingsEditor(ImageAttachmentSettings()) { requested = it; completed.await() } } }
        compose.onNodeWithText("These limits", substring = true).assertDoesNotExist()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("100000")
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("1000000")
        compose.onNodeWithText("Save limits").performClick()
        compose.onNodeWithText("Saving…").assertIsNotEnabled()
        assertEquals(ImageAttachmentSettings(100000, 1_000_000_000_000), requested)
        compose.runOnIdle { completed.complete(Unit) }
        compose.onNodeWithText("✓ Limits saved").assertIsNotEnabled()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("100001")
        compose.onNodeWithText("Save limits").assertIsEnabled()
        screenshot("image-limit-editor")
    }

    @Test fun deletionOpensExpandedWithNewestMessageAndFooterVisibleWithoutScrolling() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"ids":[],"allIds":[${(0..99).joinToString(",") { "\"m$it\"" }}]}"""))
        server.start()
        try {
            val scene = WiringScene(connection = Connection(id = "fixture", name = "Fixture", host = "127.0.0.1", port = server.port))
            compose.waitUntil(5000) { scene.session.connection.value != null }
            compose.setContent { CompositionLocalProvider(LocalCompanion provides scene.environment) { CompanionTheme(darkTheme = false) {
                MessageDeletionSheet("thread", (0..99).map { Message("m$it", Message.Role.BOT, Message.Kind.TEXT, it.toDouble(), text = "message $it") }) {}
            } } }
            compose.waitUntil(5000) { compose.onAllNodesWithText("Bot: message 99").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Bot: message 99").assertIsDisplayed()
            compose.onAllNodes(isToggleable())[0].performClick()
            compose.onNodeWithText("Delete 1").assertIsDisplayed().assertIsEnabled()
            screenshot("deletion-expanded")
        } finally { server.shutdown() }
    }

    private fun screenshot(name: String) = compose.runOnIdle {
        val globalClass = Class.forName("android.view.WindowManagerGlobal")
        val global = globalClass.getDeclaredMethod("getInstance").invoke(null)
        val views = globalClass.getDeclaredField("mViews").apply { isAccessible = true }.get(global) as List<*>
        val view = views.lastOrNull() as? android.view.View ?: compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val file = File("build/outputs/mobile-polish-screenshots/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
