package com.homelab.household.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Space, size and type are decided once, in `app/theme`, and every screen picks a step
 * (`HearthSpacing`, `HearthSize`, `HearthShapes`, `HearthTypography`). A dp or an sp written
 * anywhere else is how a design system drifts: 79 screens are designed, and the first 13dp invented
 * at a call site is the one the next screen copies. Type had already drifted that way — the canvas
 * drew 28 font sizes and the components wrote their own `sp` — which is what these rules end.
 *
 * Test sources are exempt: a test that restates a token through the token proves nothing, so
 * assertions are free to name real numbers.
 */
class DesignSystemTokenTest {

    private val clientRootDir = File(System.getProperty("user.dir")).let { dir ->
        if (dir.name == "shared") dir.parentFile else dir
    }

    private val uiDir = File(clientRootDir, "composeApp/src/commonMain/kotlin")

    /** The one package allowed to write a dp: it is where the scale is defined. */
    private val themePackage = File(uiDir, "com/homelab/household/app/theme")

    /**
     * The scale reaches a screen through `HearthTheme.spacing` / `HearthTheme.size`, so a later
     * size class can provide a different one and every screen follows. Reading `DefaultSpacing`
     * or `DefaultSizes` directly steps around that.
     *
     * `HearthIcon` is the one exception, and not a grudging one: it builds an `ImageVector`
     * outside composition, and 24 units is the icon set's own drawing grid rather than a
     * layout decision a theme gets to change.
     */
    @Test
    fun screens_read_the_scale_through_the_theme_not_the_default_instance() {
        val allowed = setOf("HearthIcon.kt")
        val defaultInstance = Regex("""\bDefault(Spacing|Sizes)\b""")

        val violations = uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.startsWith(themePackage) || it.name in allowed }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    defaultInstance.find(line)?.let {
                        "${file.toRelativeString(clientRootDir)}:${index + 1} reads '${it.value}' " +
                            "— read HearthTheme.spacing or HearthTheme.size instead"
                    }
                }
            }
            .toList()

        assertTrue(
            violations.isEmpty(),
            "The scale was read past the theme (${violations.size}):\n" + violations.joinToString("\n")
        )
    }

    @Test
    fun no_screen_or_component_writes_a_raw_dp() {
        assertTrue(uiDir.exists(), "UI directory must exist at: ${uiDir.absolutePath}")
        assertTrue(themePackage.exists(), "Theme package must exist at: ${themePackage.absolutePath}")

        val literal = Regex("""(?<![\w.])\d+(\.\d+)?\.dp\b""")

        val violations = uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.startsWith(themePackage) }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    literal.find(line)?.let {
                        "${file.toRelativeString(clientRootDir)}:${index + 1} writes '${it.value}' " +
                            "— take the step from HearthSpacing or HearthSize instead"
                    }
                }
            }
            .toList()

        assertTrue(
            violations.isEmpty(),
            "Raw dp outside app/theme (${violations.size}):\n" + violations.joinToString("\n")
        )
    }

    /**
     * The same rule for type. A screen takes a role off `HearthTheme.typography` — the role carries
     * its family, size, weight, line height and tracking — so the 14.5sp a component would
     * otherwise invent has nowhere to be written.
     */
    @Test
    fun no_screen_or_component_writes_a_raw_sp() {
        val literal = Regex("""(?<![\w.])\d+(\.\d+)?\.sp\b""")

        val violations = uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.startsWith(themePackage) }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    literal.find(line)?.let {
                        "${file.toRelativeString(clientRootDir)}:${index + 1} writes '${it.value}' " +
                            "— take the role from HearthTheme.typography instead"
                    }
                }
            }
            .toList()

        assertTrue(
            violations.isEmpty(),
            "Raw sp outside app/theme (${violations.size}):\n" + violations.joinToString("\n")
        )
    }

    /**
     * A weight is part of a role, not a decision a call site makes: `bodyStrong` is already the
     * semibold one. Reaching for `FontWeight` at a call site is how a screen ends up with a
     * medium-weight body that no other screen has.
     */
    @Test
    fun no_screen_or_component_applies_a_font_weight_by_hand() {
        val weight = Regex("""\bFontWeight\.""")

        val violations = uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.startsWith(themePackage) }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    weight.find(line)?.let {
                        "${file.toRelativeString(clientRootDir)}:${index + 1} applies a FontWeight " +
                            "by hand — the role already carries one"
                    }
                }
            }
            .toList()

        assertTrue(
            violations.isEmpty(),
            "FontWeight outside app/theme (${violations.size}):\n" + violations.joinToString("\n")
        )
    }
}
