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
    fun `GIVEN the spacing scale WHEN its steps are compared THEN each step is bigger than the one before and the ends are 0dp and 40dp`() {
        spacingSteps.zipWithNext { (smallerName, smaller), (largerName, larger) ->
            assertTrue(larger > smaller, "$largerName ($larger) must be a bigger step than $smallerName ($smaller)")
        }
        assertEquals(0.dp, DefaultSpacing.none)
        assertEquals(40.dp, DefaultSpacing.huge)
    }

    @Test
    fun `GIVEN the spacing scale WHEN its steps are checked THEN all but the hairline half-step sit on the 4dp grid`() {
        assertEquals(2.dp, DefaultSpacing.xxs, "xxs is the one half-step, for hairline insets")
        spacingSteps.filterNot { (name, _) -> name == "xxs" }.forEach { (name, step) ->
            assertOnGrid(step, 4, "spacing $name")
        }
    }

    @Test
    fun `GIVEN the icon sizes WHEN they are checked THEN each sits on the 4dp grid and is bigger than the one before`() {
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
    fun `GIVEN the sizes bigger than an icon WHEN they are checked THEN each sits on the 8dp grid`() {
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
    fun `GIVEN the PIN dot size WHEN it is checked THEN it sits on the 4dp grid`() {
        assertOnGrid(DefaultSizes.pinDot, 4, "size pinDot")
    }

    @Test
    fun `GIVEN the touch target and control sizes WHEN they are compared with the floor THEN the touch target is at least 48dp and the control clears it`() {
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
    fun `GIVEN the stroke and elevation sizes WHEN they are read THEN they are the off-grid hairline emphasis and a raised blur`() {
        assertEquals(1.dp, DefaultSizes.hairline)
        assertEquals(2.dp, DefaultSizes.emphasis)
        assertTrue(DefaultSizes.raised > 0.dp, "raised is a shadow blur, and a shadow needs one")
    }

    @Test
    fun `GIVEN the shape set WHEN the tile shape is read THEN it is the bento shape`() {
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
