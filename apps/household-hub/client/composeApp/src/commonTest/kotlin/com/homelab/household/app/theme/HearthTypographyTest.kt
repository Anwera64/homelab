package com.homelab.household.app.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Values come from the type proof — artboard `0 · The type scale` on the design canvas. A role
 * carries its family, size, weight, line height and tracking; a screen picks one and writes
 * nothing else.
 */
@OptIn(ExperimentalTestApi::class)
class HearthTypographyTest {

    /** Three families that are merely distinct: the test is about the roles, not the fonts. */
    private val fonts = HearthFonts(
        outfit = FontFamily.SansSerif,
        inter = FontFamily.Default,
        jetBrainsMono = FontFamily.Monospace
    )
    private val type = hearthTypography(fonts)

    @Test
    fun display_roles_match_the_type_proof() {
        type.hero.assertIs(fonts.outfit, 28.sp, FontWeight.SemiBold, 32.sp, (-0.015).em)
        type.title.assertIs(fonts.outfit, 21.sp, FontWeight.SemiBold, 25.sp, (-0.012).em)
        type.heading.assertIs(fonts.outfit, 17.sp, FontWeight.SemiBold, 22.sp, (-0.01).em)
    }

    @Test
    fun text_roles_match_the_type_proof() {
        type.bodyLarge.assertIs(fonts.inter, 15.sp, FontWeight.Normal, 23.sp)
        type.body.assertIs(fonts.inter, 14.sp, FontWeight.Normal, 21.sp)
        type.bodyStrong.assertIs(fonts.inter, 14.sp, FontWeight.SemiBold, 19.sp)
        type.label.assertIs(fonts.inter, 13.sp, FontWeight.Medium, 18.sp)
        type.labelStrong.assertIs(fonts.inter, 12.sp, FontWeight.SemiBold, 16.sp)
        type.caption.assertIs(fonts.inter, 12.sp, FontWeight.Normal, 17.sp)
        type.overline.assertIs(fonts.inter, 11.sp, FontWeight.SemiBold, 13.sp, 0.11.em)
        type.micro.assertIs(fonts.inter, 10.sp, FontWeight.SemiBold, 12.sp)
    }

    @Test
    fun mono_roles_match_the_type_proof() {
        type.monoSm.assertIs(fonts.jetBrainsMono, 10.sp, FontWeight.Medium, 14.sp)
        type.mono.assertIs(fonts.jetBrainsMono, 12.sp, FontWeight.Medium, 17.sp)
        type.monoLg.assertIs(fonts.jetBrainsMono, 15.sp, FontWeight.Medium, 21.sp, 0.06.em)
        type.codeHero.assertIs(fonts.jetBrainsMono, 24.sp, FontWeight.Medium, 26.sp, 0.1.em)
    }

    /**
     * A glyph — an emoji, an avatar's initial — is sized by the circle it sits in, one step per
     * diameter: 20 32 40 48 56 64 96. Line height is the glyph's own cap height, so the circle
     * centres it.
     */
    @Test
    fun glyph_roles_climb_one_step_per_circle() {
        val steps = listOf(
            type.glyphXs to 10, type.glyphSm to 14, type.glyphMd to 18, type.glyphLg to 22,
            type.glyphXl to 26, type.glyphXxl to 30, type.glyphHero to 36
        )
        steps.forEach { (style, size) ->
            assertEquals(size.sp, style.fontSize, "glyph $size size")
            assertEquals(1f.em, style.lineHeight, "glyph $size line height")
            assertEquals(FontWeight.SemiBold, style.fontWeight, "glyph $size weight")
            assertTrue(style.fontFamily === fonts.outfit, "glyph $size family")
        }
    }

    /** Material's own components read the same roles, so a Card or a Dialog cannot drift. */
    @Test
    fun material_reads_the_same_roles() {
        val m = type.material
        assertEquals(type.hero, m.displaySmall, "displaySmall")
        assertEquals(type.title, m.headlineMedium, "headlineMedium")
        assertEquals(type.heading, m.titleLarge, "titleLarge")
        assertEquals(type.bodyStrong, m.titleMedium, "titleMedium")
        assertEquals(type.body, m.bodyMedium, "bodyMedium")
        assertEquals(type.caption, m.bodySmall, "bodySmall")
        // Material puts a button's label in labelLarge.
        assertEquals(type.bodyStrong, m.labelLarge, "labelLarge")
        assertEquals(type.micro, m.labelSmall, "labelSmall")
    }

    /** Display slots stay Outfit and text slots stay Inter, whichever way Material reaches them. */
    @Test
    fun material_display_slots_are_outfit_and_text_slots_are_inter() {
        val m = type.material
        listOf(
            "displayLarge" to m.displayLarge, "displayMedium" to m.displayMedium,
            "displaySmall" to m.displaySmall, "headlineLarge" to m.headlineLarge,
            "headlineMedium" to m.headlineMedium, "headlineSmall" to m.headlineSmall,
            "titleLarge" to m.titleLarge
        ).forEach { (role, style) ->
            assertTrue(style.fontFamily === fonts.outfit, "$role should use Outfit")
        }
        listOf(
            "titleMedium" to m.titleMedium, "titleSmall" to m.titleSmall,
            "bodyLarge" to m.bodyLarge, "bodyMedium" to m.bodyMedium, "bodySmall" to m.bodySmall,
            "labelLarge" to m.labelLarge, "labelMedium" to m.labelMedium, "labelSmall" to m.labelSmall
        ).forEach { (role, style) ->
            assertTrue(style.fontFamily === fonts.inter, "$role should use Inter")
        }
    }

    @Test
    fun bento_cards_have_24dp_corners() {
        assertEquals(RoundedCornerShape(24.dp), HearthShapes.bento)
    }

    @Test
    fun the_theme_hands_out_the_type_scale() = runComposeUiTest {
        var seen: HearthTypography? = null
        var material: TextStyle? = null

        setContent {
            HearthTheme(darkTheme = false) {
                seen = HearthTheme.typography
                material = MaterialTheme.typography.bodyMedium
            }
        }

        waitForIdle()
        assertEquals(14.sp, seen?.body?.fontSize, "body size off the theme")
        assertEquals(14.sp, material?.fontSize, "Material body size off the theme")
    }

    /** Type doesn't change with the palette: the same role reads the same in either. */
    @Test
    fun the_type_scale_is_the_same_in_both_palettes() = runComposeUiTest {
        var day: HearthTypography? = null
        var night: HearthTypography? = null

        setContent {
            HearthTheme(darkTheme = false) { day = HearthTheme.typography }
            HearthTheme(darkTheme = true) { night = HearthTheme.typography }
        }

        waitForIdle()
        assertEquals(day?.body, night?.body)
        assertEquals(day?.hero, night?.hero)
    }

    /**
     * The point of putting type behind a local: a tighter or looser scale — a tablet, a later size
     * class, an accessibility pass — can be provided once and every screen under it follows.
     */
    @Test
    fun a_provided_type_scale_reaches_the_screens_under_it() = runComposeUiTest {
        var seen: TextStyle? = null

        setContent {
            HearthTheme(darkTheme = false) {
                val roomier = HearthTheme.typography.let { it.copy(body = it.bodyLarge) }
                CompositionLocalProvider(LocalHearthTypography provides roomier) {
                    seen = HearthTheme.typography.body
                }
            }
        }

        waitForIdle()
        assertEquals(15.sp, seen?.fontSize)
    }

    private fun TextStyle.assertIs(
        family: Any?,
        size: TextUnit,
        weight: FontWeight,
        lineHeight: TextUnit,
        tracking: TextUnit = TextUnit.Unspecified
    ) {
        assertTrue(fontFamily === family, "family of the ${size.value.toInt()}sp role")
        assertEquals(size, fontSize, "size")
        assertEquals(weight, fontWeight, "weight of the ${size.value.toInt()}sp role")
        assertEquals(lineHeight, this.lineHeight, "line height of the ${size.value.toInt()}sp role")
        assertEquals(tracking, letterSpacing, "tracking of the ${size.value.toInt()}sp role")
    }
}
