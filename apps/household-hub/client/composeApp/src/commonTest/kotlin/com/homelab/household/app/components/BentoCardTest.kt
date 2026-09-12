package com.homelab.household.app.components

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.theme.HearthTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class BentoCardTest {

    @Test
    fun shows_its_label_and_content() = runComposeUiTest {
        setContent {
            HearthTheme(darkTheme = false) {
                BentoCard(label = "Today · merged") { Text("Dinner together") }
            }
        }

        onNodeWithText("TODAY · MERGED", ignoreCase = true).assertIsDisplayed()
        onNodeWithText("Dinner together").assertIsDisplayed()
    }

    @Test
    fun label_is_optional() = runComposeUiTest {
        setContent {
            HearthTheme(darkTheme = false) {
                BentoCard { Text("Dinner together") }
            }
        }

        onNodeWithText("Dinner together").assertIsDisplayed()
    }
}
