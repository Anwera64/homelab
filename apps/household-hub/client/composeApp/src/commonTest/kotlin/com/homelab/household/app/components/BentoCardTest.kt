package com.homelab.household.app.components

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.testing.StillTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class BentoCardTest {
    @Test
    fun `GIVEN a card with a label and content WHEN it is shown THEN the label is upper-cased and the content is visible`() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    BentoCard(label = "Today · merged") { Text("Dinner together") }
                }
            }

            onBentoCard {
                seesLabel("TODAY · MERGED")
                seesText("Dinner together")
            }
        }

    @Test
    fun `GIVEN a card without a label WHEN it is shown THEN the content is still visible`() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    BentoCard { Text("Dinner together") }
                }
            }

            onBentoCard { seesText("Dinner together") }
        }
}
