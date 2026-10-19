package com.homelab.household.app.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.testing.StillTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class HearthTopBarTest {
    @Test
    fun `GIVEN a top bar with a back button WHEN back is tapped THEN back is reported once`() =
        runComposeUiTest {
            var back = 0
            setContent {
                StillTheme {
                    HearthTopBar(onBack = { back++ }, backDescription = "Back")
                }
            }

            onHearthTopBar { tapsBack("Back") }

            assertEquals(1, back)
        }

    @Test
    fun `GIVEN a top bar with a title WHEN it is shown THEN the title and the back button are visible`() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    HearthTopBar(onBack = {}, backDescription = "Back", title = "Members")
                }
            }

            onHearthTopBar {
                seesTitle("Members")
                seesBack("Back")
            }
        }
}
