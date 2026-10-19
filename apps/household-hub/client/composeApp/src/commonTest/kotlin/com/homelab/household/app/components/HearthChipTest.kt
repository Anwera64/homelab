package com.homelab.household.app.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.theme.DayColors
import com.homelab.household.app.theme.NightColors
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class HearthChipTest {
    @Test
    fun `GIVEN a chip with a label WHEN it is shown THEN the label is visible`() =
        runComposeUiTest {
            setContent { StillTheme { HearthChip(label = "Read only") } }

            onHearthChip { seesLabel("Read only") }
        }

    @Test
    fun `GIVEN the day and night palettes WHEN the secret chip colours are built THEN each is a ghost tuned to its palette`() {
        listOf(DayColors, NightColors).forEach { palette ->
            val colors = chipColors(ChipVariant.Secret, palette)
            assertEquals(palette.secret.copy(alpha = palette.ghostTint), colors.container)
            assertEquals(palette.secret, colors.content)
            assertEquals(palette.secret.copy(alpha = palette.ghostEdge), colors.border)
        }
    }

    @Test
    fun `GIVEN the day palette WHEN chip colours are built for each variant THEN each uses its own container and content pair`() {
        with(chipColors(ChipVariant.Primary, DayColors)) {
            assertEquals(DayColors.primaryContainer, container)
            assertEquals(DayColors.onPrimaryContainer, content)
        }
        with(chipColors(ChipVariant.Secondary, DayColors)) {
            assertEquals(DayColors.secondaryContainer, container)
            assertEquals(DayColors.onSecondaryContainer, content)
        }
        with(chipColors(ChipVariant.Attention, DayColors)) {
            assertEquals(DayColors.tertiaryContainer, container)
            assertEquals(DayColors.onTertiaryContainer, content)
        }
        with(chipColors(ChipVariant.Error, DayColors)) {
            assertEquals(DayColors.errorContainer, container)
            assertEquals(DayColors.onErrorContainer, content)
        }
        with(chipColors(ChipVariant.Success, DayColors)) {
            assertEquals(DayColors.successContainer, container)
            assertEquals(DayColors.onSuccessContainer, content)
        }
    }
}
