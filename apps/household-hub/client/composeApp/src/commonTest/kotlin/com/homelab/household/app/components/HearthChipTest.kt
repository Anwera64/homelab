package com.homelab.household.app.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.theme.DayColors
import com.homelab.household.app.theme.NightColors
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class HearthChipTest {

    @Test
    fun shows_its_label() = runComposeUiTest {
        setContent { StillTheme { HearthChip(label = "Read only") } }

        onNodeWithText("Read only").assertIsDisplayed()
    }

    @Test
    fun secret_chip_is_a_ghost_tuned_per_palette() {
        listOf(DayColors, NightColors).forEach { palette ->
            val colors = chipColors(ChipVariant.Secret, palette)
            assertEquals(palette.secret.copy(alpha = palette.ghostTint), colors.container)
            assertEquals(palette.secret, colors.content)
            assertEquals(palette.secret.copy(alpha = palette.ghostEdge), colors.border)
        }
    }

    @Test
    fun variants_use_their_container_pairs() {
        with(chipColors(ChipVariant.Primary, DayColors)) {
            assertEquals(DayColors.primaryContainer, container)
            assertEquals(DayColors.onPrimaryContainer, content)
        }
        with(chipColors(ChipVariant.Secondary, DayColors)) {
            assertEquals(DayColors.secondaryContainer, container)
            assertEquals(DayColors.onSecondaryContainer, content)
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
