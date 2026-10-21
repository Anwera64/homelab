package com.homelab.household.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * On iOS nothing hides the keyboard but the app: there is no back button, and the numeric keypad
 * has no return key. `HearthScaffold` hides it on a tap that no control takes, for every screen it
 * frames, so a screen with a field must be framed by it, and no screen keeps a copy of its own.
 */
class KeyboardDismissTest {
    private val clientRootDir =
        File(System.getProperty("user.dir")).let { dir ->
            if (dir.name == "shared") dir.parentFile else dir
        }

    private fun read(path: String): String = File(clientRootDir, path).readText()

    private val appDir = File(clientRootDir, "composeApp/src/commonMain/kotlin/com/homelab/household/app")

    private val sources by lazy {
        appDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private val textField = Regex("""\b(HearthTextField|PinField|CodeBoxes|MessageComposer|BasicTextField)\(""")

    @Test
    fun `GIVEN a screen with a field WHEN it is drawn THEN HearthScaffold frames it`() {
        val screens =
            sources.filter {
                it.name.endsWith("Content.kt") && "${File.separator}screens${File.separator}" in it.path
            }
        assertTrue(screens.isNotEmpty(), "Expected screens under ${appDir.absolutePath}")

        val unframed =
            screens
                .filter { textField.containsMatchIn(it.readText()) && !it.readText().contains("HearthScaffold(") }
                .map { it.toRelativeString(clientRootDir) }
        assertEquals(emptyList<String>(), unframed, "these screens have a field but no HearthScaffold")
    }

    @Test
    fun `GIVEN the app sources WHEN they are scanned THEN only HearthScaffold hides the keyboard on a tap`() {
        val copies =
            sources
                .filter { it.name != "HearthScaffold.kt" && it.readText().contains("detectTapGestures") }
                .filter { it.readText().contains("clearFocus()") }
                .map { it.toRelativeString(clientRootDir) }
        assertEquals(emptyList<String>(), copies, "the scaffold already hides the keyboard on a tap")
    }

    @Test
    fun `GIVEN the two apps WHEN they start THEN only iOS hides the keyboard on a scroll`() {
        val ios = read("iosApp/src/iosMain/kotlin/com/homelab/household/MainViewController.kt")
        val android = read("androidApp/src/main/kotlin/com/homelab/household/MainActivity.kt")
        assertTrue(ios.contains("dragHidesKeyboard = true"), "iOS has no back button: a scroll hides the keyboard")
        assertFalse(android.contains("dragHidesKeyboard"), "Android keeps the keyboard while a form scrolls")
        assertTrue(
            File(appDir, "App.kt").readText().contains("LocalDragHidesKeyboard provides dragHidesKeyboard"),
            "App() hands the choice to every screen",
        )
    }
}
