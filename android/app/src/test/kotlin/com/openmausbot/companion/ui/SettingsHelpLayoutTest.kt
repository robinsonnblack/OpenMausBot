package com.openmausbot.companion.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue
import android.graphics.Bitmap
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "de-w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsHelpLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun helpIsAlignedWithItsSettingAndExplanationOpensOnlyOnTap() {
        compose.setContent { CompanionTheme(darkTheme = false) { Surface { Column(Modifier.padding(20.dp)) {
            SettingsSection("Hintergrundverbindung", "Hintergrund-Hilfe") {
                SettingsRow("Status", "Nur bei geöffneter App")
                TextButton(onClick = {}) { Text("Einschalten") }
            }
            SettingsSection("Chat") {
                SettingsRow("Aktivität", "Vollständig", "Aktivitäts-Hilfe")
                SettingsRow("Thread-Listen", "Angezeigt", "Thread-Hilfe")
            }
            SettingsSection("Workspace", "Workspace-Hilfe") { TextButton(onClick = {}) { Text("Verbundene Apps") } }
        } } } }
        compose.onNodeWithText("Aktivitäts-Hilfe").assertDoesNotExist()
        compose.onAllNodesWithText("?").assertCountEquals(0)
        val label = compose.onNodeWithText("Aktivität").fetchSemanticsNode().boundsInRoot
        val icon = compose.onNodeWithContentDescription("Erklärung: Aktivität").fetchSemanticsNode().boundsInRoot
        assertTrue(icon.left > label.right)
        assertTrue(kotlin.math.abs(icon.center.y - label.center.y) < 1)
        screenshot()
        compose.onNodeWithContentDescription("Erklärung: Aktivität").performClick()
        compose.onNodeWithText("Aktivitäts-Hilfe").assertIsDisplayed()
    }
    private fun screenshot() = compose.runOnIdle {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val file = File("build/outputs/stt-screenshots/settings-help-german.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}
