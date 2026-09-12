package com.homelab.household.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** Values come from the palette proof, sections 04 (Copenhagen Day) and 05 (Midnight Espresso). */
@OptIn(ExperimentalTestApi::class)
class HearthThemeTest {

    @Test
    fun day_palette_matches_the_palette_proof() {
        with(DayColors) {
            assertColor(0xF5F2EB, canvas, "canvas")
            assertColor(0xFFFFFF, surface, "surface")
            assertColor(0xFAF8F3, surfaceAlt, "surfaceAlt")
            assertColor(0xFFFFFF, surface3, "surface3")
            assertColor(0x3C6E4E, primary, "primary")
            assertColor(0xFFFFFF, onPrimary, "onPrimary")
            assertColor(0xD0DFD5, primaryContainer, "primaryContainer")
            assertColor(0x102318, onPrimaryContainer, "onPrimaryContainer")
            assertColor(0xC05638, secondary, "secondary")
            assertColor(0xFFFFFF, onSecondary, "onSecondary")
            assertColor(0xFFD5C7, secondaryContainer, "secondaryContainer")
            assertColor(0x541A0B, onSecondaryContainer, "onSecondaryContainer")
            assertColor(0xA33B2A, error, "error")
            assertColor(0xFBDDD6, errorContainer, "errorContainer")
            assertColor(0x5C1508, onErrorContainer, "onErrorContainer")
            assertColor(0x3A7455, success, "success")
            assertColor(0xD2E7DA, successContainer, "successContainer")
            assertColor(0x0F2E1F, onSuccessContainer, "onSuccessContainer")
            assertColor(0x6B655F, secret, "secret")
            assertColor(0x221F1E, textPrimary, "textPrimary")
            assertColor(0x514B47, textMuted, "textMuted")
            assertColor(0xDED7CA, outline, "outline")
            assertColor(0xEFEBE4, outlineSoft, "outlineSoft")
            assertEquals(0.20f, ghostTint, "ghostTint")
            assertEquals(0.69f, ghostEdge, "ghostEdge")
            assertEquals(0.66f, ghostFrame, "ghostFrame")
        }
    }

    @Test
    fun night_palette_matches_the_palette_proof() {
        with(NightColors) {
            assertColor(0x100F0E, canvas, "canvas")
            assertColor(0x191715, surface, "surface")
            assertColor(0x211E1B, surfaceAlt, "surfaceAlt")
            assertColor(0x2A2622, surface3, "surface3")
            assertColor(0x7FB894, primary, "primary")
            assertColor(0x100F0E, onPrimary, "onPrimary")
            assertColor(0x26352D, primaryContainer, "primaryContainer")
            assertColor(0x9BC2AC, onPrimaryContainer, "onPrimaryContainer")
            assertColor(0xE5855E, secondary, "secondary")
            assertColor(0x100F0E, onSecondary, "onSecondary")
            assertColor(0x381E15, secondaryContainer, "secondaryContainer")
            assertColor(0xFFB091, onSecondaryContainer, "onSecondaryContainer")
            assertColor(0xE8705C, error, "error")
            assertColor(0x4A160C, errorContainer, "errorContainer")
            assertColor(0xFFB4A4, onErrorContainer, "onErrorContainer")
            assertColor(0x6FB58E, success, "success")
            assertColor(0x16342A, successContainer, "successContainer")
            assertColor(0xA5D6BC, onSuccessContainer, "onSuccessContainer")
            assertColor(0xB8B2AC, secret, "secret")
            assertColor(0xE8E2DC, textPrimary, "textPrimary")
            assertColor(0x9C948D, textMuted, "textMuted")
            assertColor(0x282420, outline, "outline")
            assertColor(0x272523, outlineSoft, "outlineSoft")
            assertEquals(0.14f, ghostTint, "ghostTint")
            assertEquals(0.55f, ghostEdge, "ghostEdge")
            assertEquals(0.45f, ghostFrame, "ghostFrame")
        }
    }

    @Test
    fun dark_theme_provides_night_tokens_to_hearth_and_material() = runComposeUiTest {
        var hearth: HearthColors? = null
        var materialPrimary: Color? = null
        var materialBackground: Color? = null

        setContent {
            HearthTheme(darkTheme = true) {
                hearth = HearthTheme.colors
                materialPrimary = MaterialTheme.colorScheme.primary
                materialBackground = MaterialTheme.colorScheme.background
            }
        }

        waitForIdle()
        assertEquals(NightColors, hearth)
        assertEquals(NightColors.primary, materialPrimary)
        assertEquals(NightColors.canvas, materialBackground)
    }

    @Test
    fun light_theme_provides_day_tokens_to_hearth_and_material() = runComposeUiTest {
        var hearth: HearthColors? = null
        var materialPrimary: Color? = null

        setContent {
            HearthTheme(darkTheme = false) {
                hearth = HearthTheme.colors
                materialPrimary = MaterialTheme.colorScheme.primary
            }
        }

        waitForIdle()
        assertEquals(DayColors, hearth)
        assertEquals(DayColors.primary, materialPrimary)
    }

    @Test
    fun the_theme_hands_out_the_scale_beside_the_palette() = runComposeUiTest {
        var spacing: HearthSpacing? = null
        var size: HearthSizes? = null

        setContent {
            HearthTheme(darkTheme = false) {
                spacing = HearthTheme.spacing
                size = HearthTheme.size
            }
        }

        waitForIdle()
        assertEquals(DefaultSpacing, spacing)
        assertEquals(DefaultSizes, size)
    }

    /** Space doesn't change with the palette: a screen reads the same step in either theme. */
    @Test
    fun the_scale_is_the_same_in_both_palettes() = runComposeUiTest {
        var day: HearthSpacing? = null
        var night: HearthSpacing? = null

        setContent {
            HearthTheme(darkTheme = false) { day = HearthTheme.spacing }
            HearthTheme(darkTheme = true) { night = HearthTheme.spacing }
        }

        waitForIdle()
        assertEquals(day, night)
    }

    /**
     * The point of putting the scale behind a local: a denser or looser variant — a tablet, a
     * later size class — can be provided once, and every screen follows without being touched.
     */
    @Test
    fun a_provided_scale_reaches_the_screens_under_it() = runComposeUiTest {
        val roomier = DefaultSpacing.copy(xl = 32.dp)
        var seen: Dp? = null

        setContent {
            HearthTheme(darkTheme = false) {
                CompositionLocalProvider(LocalHearthSpacing provides roomier) {
                    seen = HearthTheme.spacing.xl
                }
            }
        }

        waitForIdle()
        assertEquals(32.dp, seen)
    }

    private fun assertColor(expectedRgb: Long, actual: Color, token: String) {
        assertEquals(Color(0xFF000000 or expectedRgb), actual, "Token '$token'")
    }
}
