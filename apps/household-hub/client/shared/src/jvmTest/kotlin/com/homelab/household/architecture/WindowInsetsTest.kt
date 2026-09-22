package com.homelab.household.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * A bar pads its own window insets.
 *
 * That is `HearthScaffold`'s contract, in its own words: "Bars pad their own window insets, as
 * Material 3's do; the content gets the insets only on a side with no bar." `Scaffold` hands the
 * content the *bar's* height on a side that has one, and nothing else — so a bar that forgets sits
 * under the status bar or the gesture bar, and nothing about the layout looks wrong until it is on
 * a real phone.
 *
 * It only reads wrong on a device, which is exactly why it is worth a guard: a Compose UI test
 * renders with no insets at all and would pass either way.
 */
class WindowInsetsTest {
    private val clientRootDir =
        File(System.getProperty("user.dir")).let { dir ->
            if (dir.name == "shared") dir.parentFile else dir
        }

    private val uiDir = File(clientRootDir, "composeApp/src/commonMain/kotlin/com/homelab/household")

    /**
     * Every component handed to `HearthScaffold` as a `header` or `bottomBar`, and how it pays for
     * its insets. Material 3's own bars do it themselves, so a file built on `TopAppBar` is exempt
     * and says so here rather than repeating the padding.
     */
    private val bars =
        listOf(
            "app/components/HearthBottomNav.kt",
            "app/components/MessageComposer.kt",
            "app/screens/chats/ChatsContent.kt",
        )

    @Test
    fun every_bar_pads_its_own_window_insets() {
        val offenders =
            bars.filter { path ->
                val source = File(uiDir, path)
                assertTrue(source.exists(), "Bar not found: ${source.absolutePath}")
                !source.readText().contains("windowInsetsPadding")
            }

        assertTrue(
            offenders.isEmpty(),
            "These draw a scaffold bar but never pad the window insets, so they sit under the " +
                "system bars on a real phone: $offenders",
        )
    }

    @Test
    fun a_bar_built_on_material3_does_not_pad_the_insets_twice() {
        // TopAppBar already applies TopAppBarDefaults.windowInsets; padding again would double it.
        val topBar = File(uiDir, "app/components/HearthTopBar.kt")
        assertTrue(topBar.exists(), "HearthTopBar not found: ${topBar.absolutePath}")

        val source = topBar.readText()
        assertTrue(
            source.contains("TopAppBar(") && !source.contains("windowInsetsPadding"),
            "HearthTopBar should lean on Material 3's own insets rather than adding its own",
        )
    }
}
