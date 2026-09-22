package com.homelab.household.app.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The scale itself, not any screen's use of it: space and size are a 4dp grid, so a screen can
 * only ever pick a step. `DesignSystemTokenTest` in `:shared` is what stops a screen writing a
 * raw dp instead.
 */
class HearthSpacingTest {
    private val spacingSteps =
        listOf(
            "none" to DefaultSpacing.none,
            "xxs" to DefaultSpacing.xxs,
            "xs" to DefaultSpacing.xs,
            "sm" to DefaultSpacing.sm,
            "md" to DefaultSpacing.md,
            "lg" to DefaultSpacing.lg,
            "xl" to DefaultSpacing.xl,
            "xxl" to DefaultSpacing.xxl,
            "xxxl" to DefaultSpacing.xxxl,
            "huge" to DefaultSpacing.huge,
        )

    @Test
    fun spacing_steps_only_climb() {
        spacingSteps.zipWithNext { (smallerName, smaller), (largerName, larger) ->
            assertTrue(larger > smaller, "$largerName ($larger) must be a bigger step than $smallerName ($smaller)")
        }
        assertEquals(0.dp, DefaultSpacing.none)
        assertEquals(40.dp, DefaultSpacing.huge)
    }

    @Test
    fun spacing_steps_sit_on_the_four_dp_grid_apart_from_the_hairline_half_step() {
        assertEquals(2.dp, DefaultSpacing.xxs, "xxs is the one half-step, for hairline insets")
        spacingSteps.filterNot { (name, _) -> name == "xxs" }.forEach { (name, step) ->
            assertOnGrid(step, 4, "spacing $name")
        }
    }

    @Test
    fun icon_sizes_climb_in_four_dp_steps() {
        val icons =
            listOf(
                "iconSm" to DefaultSizes.iconSm,
                "iconMd" to DefaultSizes.iconMd,
                "iconLg" to DefaultSizes.iconLg,
                "iconXl" to DefaultSizes.iconXl,
                "iconXxl" to DefaultSizes.iconXxl,
                "iconHero" to DefaultSizes.iconHero,
            )
        icons.forEach { (name, size) -> assertOnGrid(size, 4, "size $name") }
        icons.zipWithNext { (smallerName, smaller), (largerName, larger) ->
            assertTrue(larger > smaller, "$largerName ($larger) must be a bigger icon than $smallerName ($smaller)")
        }
    }

    @Test
    fun anything_bigger_than_an_icon_sits_on_the_eight_dp_grid() {
        listOf(
            "touchTarget" to DefaultSizes.touchTarget,
            "control" to DefaultSizes.control,
            "tile" to DefaultSizes.tile,
            "tileHero" to DefaultSizes.tileHero,
            "swatch" to DefaultSizes.swatch,
            "avatarHero" to DefaultSizes.avatarHero,
            "readingWidth" to DefaultSizes.readingWidth,
            "progressTrack" to DefaultSizes.progressTrack,
        ).forEach { (name, size) -> assertOnGrid(size, 8, "size $name") }
    }

    @Test
    fun the_pin_dot_sits_on_the_four_dp_grid() {
        assertOnGrid(DefaultSizes.pinDot, 4, "size pinDot")
    }

    @Test
    fun a_tappable_thing_is_never_smaller_than_the_accessibility_floor() {
        assertTrue(
            DefaultSizes.touchTarget >= 48.dp,
            "touchTarget is ${DefaultSizes.touchTarget}, under the 48dp floor",
        )
        assertTrue(
            DefaultSizes.control >= DefaultSizes.touchTarget,
            "control (${DefaultSizes.control}) must clear touchTarget (${DefaultSizes.touchTarget})",
        )
    }

    @Test
    fun strokes_and_elevation_are_not_layout_so_they_are_off_the_grid() {
        assertEquals(1.dp, DefaultSizes.hairline)
        assertEquals(2.dp, DefaultSizes.emphasis)
        assertTrue(DefaultSizes.raised > 0.dp, "raised is a shadow blur, and a shadow needs one")
    }

    @Test
    fun icon_tiles_reuse_the_bento_radius_instead_of_inventing_one() {
        assertEquals(HearthShapes.bento, HearthShapes.tile)
    }

    private fun assertOnGrid(
        value: Dp,
        grid: Int,
        what: String,
    ) {
        assertTrue(value.value % grid == 0f, "$what is $value, off the ${grid}dp grid")
    }
}
