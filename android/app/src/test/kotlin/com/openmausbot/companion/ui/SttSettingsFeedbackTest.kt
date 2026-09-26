package com.openmausbot.companion.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.input.key.Key
import kotlin.test.assertTrue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.openmausbot.companion.dictation.*
import com.openmausbot.companion.core.CompanionJson
import java.io.File
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFails

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "de-w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class SttSettingsFeedbackTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun importHasPendingAndPersistentSuccessFeedbackAndEditingResetsIt() {
        val result=CompletableDeferred<SttConfig>()
        compose.setContent { CompanionTheme(darkTheme=false) { SttSettingsEditor(SttConfig(),onSave={},onImport={result.await()}) } }
        compose.onNodeWithText("Vom Desktop übernehmen").performClick()
        compose.onNodeWithText("Übernehmen… Am Desktop bestätigen").assertIsDisplayed().assertIsNotEnabled()
        compose.runOnIdle { result.complete(SttConfig("groq",profiles=mapOf("groq" to SttProfile("synthetic")))) }
        compose.waitForIdle()
        compose.onNodeWithText("✓ Vom Desktop übernommen").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("✓ Gespeichert").assertIsDisplayed().assertIsNotEnabled()
        val label=compose.onNodeWithText("API-Schlüssel");label.assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction())[0].performScrollTo().performTextReplacement("synthetic-edited")
        compose.onNodeWithText("Speichern").assertIsEnabled()
        screenshot("stt-editor-german.png")
    }
    @Test fun saveHasPendingAndPersistentSuccessFeedbackAndEditingResetsIt() {
        val done = CompletableDeferred<Unit>()
        compose.setContent { CompanionTheme(darkTheme=false) { SttSettingsEditor(SttConfig("groq"),onSave={done.await()},onImport={it}) } }
        compose.onNodeWithText("Speichern").performClick()
        compose.onNodeWithText("Speichern…").assertIsDisplayed().assertIsNotEnabled()
        compose.runOnIdle { done.complete(Unit) }
        compose.waitForIdle()
        compose.onNodeWithText("✓ Gespeichert").assertIsDisplayed().assertIsNotEnabled()
        compose.onAllNodes(hasSetTextAction())[0].performScrollTo().performTextReplacement("synthetic-edited")
        compose.onNodeWithText("Speichern").assertIsEnabled()
    }
    @Test fun failedImportIsVisibleAndCanBeRetried() {
        compose.setContent { CompanionTheme(darkTheme=false) { SttSettingsEditor(SttConfig(),onSave={},onImport={error("Import declined on the desktop.")}) } }
        compose.onNodeWithText("Vom Desktop übernehmen").performClick()
        compose.onNodeWithText("Übernahme am Desktop abgelehnt.").assertIsDisplayed()
        compose.onNodeWithText("Vom Desktop übernehmen").assertIsEnabled()
    }
    @Test fun nodeEnvelopeDecryptsOnAndroidAndRejectsAlteredBinding() {
        val text=javaClass.getResource("/stt-import-node-fixture.json")!!.readText()
        val fixture=Json.parseToJsonElement(text).jsonObject
        val key=KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(fixture.getValue("privateKey").jsonPrimitive.content)))
        val envelope=fixture.getValue("envelope").jsonObject
        val imported=decryptSttImport(envelope,key)
        assertEquals("synthetic-interoperability-key",imported.profiles["openrouter"]!!.key)
        assertFails { decryptSttImport(JsonObject(envelope + ("requestId" to JsonPrimitive("altered"))),key) }
    }
    @Test fun recordingAndSendOptionsSaveTogetherAndKeepSuccessFeedback() {
        var saved: SttConfig? = null
        compose.setContent { CompanionTheme(darkTheme=false) { SttSettingsEditor(SttConfig(),onSave={saved=it.validated()},onImport={it}) } }
        compose.onNodeWithText("Sprechpause").performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("750")
        compose.onNodeWithText("Direkt senden").performScrollTo().performClick()
        compose.onNodeWithText("Speichern").performClick()
        compose.onNodeWithText("✓ Gespeichert").assertIsNotEnabled()
        assertEquals("silence",saved!!.stopMode); assertEquals(750,saved!!.silenceMs); assertEquals("send",saved!!.afterAction)
        compose.onNodeWithText("Erneutes Tippen").performScrollTo().performClick()
        compose.onNodeWithText("Speichern").assertIsEnabled()
        compose.onNodeWithText("Spracheingabe").performScrollTo()
        screenshot("stt-options-german.png")
    }
    @Test fun deletingJustTheLeadingDigitPreservesZerosAndTheCursor() {
        var saved: SttConfig? = null
        compose.setContent { CompanionTheme(darkTheme=false) { SttSettingsEditor(SttConfig(stopMode="silence"),onSave={saved=it.validated()},onImport={it}) } }
        val input = compose.onAllNodes(hasSetTextAction())[0]
        input.performScrollTo().assertTextContains("5000")
        input.performClick().performTextInputSelection(TextRange(1))
        input.performKeyInput { pressKey(Key.Backspace) }
        input.assertTextContains("000")
        input.performTextInput("2")
        input.assertTextContains("2000")
        compose.onNodeWithText("Speichern").performClick()
        assertEquals(2000, saved!!.silenceMs)
        compose.onNodeWithText("✓ Gespeichert").assertIsNotEnabled()
        input.performTextInputSelection(TextRange(2))
        compose.onNodeWithText("✓ Gespeichert").assertIsNotEnabled()
    }
    @Test @Config(sdk=[34], qualifiers="de-w360dp-h800dp-mdpi")
    fun expandedPhoneEditorShowsBothCompletionChoicesWithoutScrolling() {
        compose.setContent { CompanionTheme(darkTheme=false) { Column(Modifier.fillMaxSize()) {
            SttSettingsEditor(SttConfig(stopMode="silence"),onSave={},onImport={it}, modifier=Modifier.weight(1f))
            TextButton(onClick={}) { Text("Schließen") }
        } } }
        val row = compose.onNodeWithTag("stt-after-actions")
        val clipped = row.fetchSemanticsNode().boundsInRoot
        val full = row.getUnclippedBoundsInRoot()
        assertTrue(clipped.height >= (full.bottom.value - full.top.value) - 1, "The completion choices must not be clipped: $clipped / $full")
        compose.onNodeWithText("Nur einfügen").assertIsDisplayed()
        compose.onNodeWithText("Direkt senden").assertIsDisplayed()
        compose.onNodeWithText("Speichern").assertIsDisplayed()
        compose.onNodeWithText("Vom Desktop übernehmen").assertIsDisplayed()
        screenshot("stt-pause-editor-german.png")
    }
    private fun screenshot(name:String) {
        compose.waitForIdle(); compose.runOnUiThread {
            val view=compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(android.graphics.Canvas(bitmap))
            val folder=File("build/outputs/stt-screenshots").apply { mkdirs() }
            File(folder,name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        }
    }
}
