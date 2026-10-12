package com.homelab.household.app.components

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.testing.StillTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class BentoCardTest {
    @Test
    fun shows_its_label_and_content() =
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
    fun label_is_optional() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    BentoCard { Text("Dinner together") }
                }
            }

            onBentoCard { seesText("Dinner together") }
        }
}
