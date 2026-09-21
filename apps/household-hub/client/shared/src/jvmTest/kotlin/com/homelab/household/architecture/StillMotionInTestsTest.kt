package com.homelab.household.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * A Compose UI test that composes the app's theme without switching the motion off does not fail
 * when it goes wrong — it **hangs**. The test runtime synchronises on idleness, and an infinite
 * animation never lets the composition go idle, so `waitForIdle`, `waitUntil` and every `onNode…`
 * time out instead of reporting anything you can read.
 *
 * That is not hypothetical. Every `every_previewed_state_draws` test mounts its screen's `Content`
 * directly rather than through `TestApp`, so none of them inherited `TestApp`'s `StillMotion`. The
 * first busy entry added to any `*UiStateProvider` would have turned a whole screen test into a
 * timeout, which is the one failure this design exists to prevent.
 *
 * So tests compose [com.homelab.household.app.testing.StillTheme], never `HearthTheme` directly.
 * It is the same theme with the motion off, and `TestApp` is built from it too.
 */
class StillMotionInTestsTest {

    private val clientRootDir = File(System.getProperty("user.dir")).let { dir ->
        if (dir.name == "shared") dir.parentFile else dir
    }

    private val testDir = File(clientRootDir, "composeApp/src/commonTest/kotlin")

    /**
     * `StillTheme` is where the motion is switched off, so it composes the real theme by
     * definition. The theme's own tests are the other exception, and not a grudging one: their
     * whole job is to prove what `HearthTheme` hands out — including that it hands out
     * `DefaultMotion`, which composing the still one would make untestable.
     */
    private val themeOwnTests = File(testDir, "com/homelab/household/app/theme")
    private val allowed = setOf("StillTheme.kt")

    @Test
    fun a_ui_test_composes_the_still_theme_rather_than_the_live_one() {
        assertTrue(testDir.exists(), "Test directory must exist at: ${testDir.absolutePath}")

        val liveTheme = Regex("""\bHearthTheme\s*\(""")

        val violations = testDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.startsWith(themeOwnTests) || it.name in allowed }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    liveTheme.find(line)?.let {
                        "${file.toRelativeString(clientRootDir)}:${index + 1} composes HearthTheme " +
                            "directly — compose StillTheme instead, or the motion stays on and a " +
                            "failing test hangs rather than failing"
                    }
                }
            }
            .toList()

        assertTrue(
            violations.isEmpty(),
            "The live theme was composed in a test (${violations.size}):\n" + violations.joinToString("\n")
        )
    }
}
