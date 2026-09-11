package com.homelab.household.app.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HearthTypographyTest {

    @Test
    fun display_and_headline_roles_use_outfit_and_text_roles_use_inter() = runComposeUiTest {
        var fonts: HearthFonts? = null
        var typography: Typography? = null

        setContent {
            HearthTheme(darkTheme = false) {
                fonts = HearthTheme.fonts
                typography = MaterialTheme.typography
            }
        }
        waitForIdle()

        val families = assertNotNull(fonts)
        val type = assertNotNull(typography)
        val outfitRoles = with(type) {
            mapOf(
                "displayLarge" to displayLarge, "displayMedium" to displayMedium, "displaySmall" to displaySmall,
                "headlineLarge" to headlineLarge, "headlineMedium" to headlineMedium, "headlineSmall" to headlineSmall,
                "titleLarge" to titleLarge
            )
        }
        val interRoles = with(type) {
            mapOf(
                "titleMedium" to titleMedium, "titleSmall" to titleSmall,
                "bodyLarge" to bodyLarge, "bodyMedium" to bodyMedium, "bodySmall" to bodySmall,
                "labelLarge" to labelLarge, "labelMedium" to labelMedium, "labelSmall" to labelSmall
            )
        }

        outfitRoles.forEach { (role, style) ->
            assertEquals(families.outfit, style.fontFamily, "$role should use Outfit")
            assertWeightIn(style, 500..700, role)
        }
        interRoles.forEach { (role, style) ->
            assertEquals(families.inter, style.fontFamily, "$role should use Inter")
            assertWeightIn(style, 400..600, role)
        }
    }

    @Test
    fun mono_style_is_jetbrains_mono_medium_for_times_and_metadata() = runComposeUiTest {
        var fonts: HearthFonts? = null
        var mono: TextStyle? = null

        setContent {
            HearthTheme(darkTheme = false) {
                fonts = HearthTheme.fonts
                mono = HearthTheme.typography.mono
            }
        }
        waitForIdle()

        val style = assertNotNull(mono)
        assertEquals(assertNotNull(fonts).jetBrainsMono, style.fontFamily)
        assertEquals(FontWeight.Medium, style.fontWeight)
        assertTrue(style.fontSize.value in 11f..12f, "Mono size should be 11–12sp, was ${style.fontSize}")
    }

    @Test
    fun bento_cards_have_24dp_corners() {
        assertEquals(RoundedCornerShape(24.dp), HearthShapes.bento)
    }

    private fun assertWeightIn(style: TextStyle, range: IntRange, role: String) {
        val weight = assertNotNull(style.fontWeight, "$role has no font weight").weight
        assertTrue(weight in range, "$role weight $weight not in $range")
    }
}
