package com.homelab.household.app.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.theme.HearthTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class HearthTopBarTest {

    @Test
    fun back_goes_back() = runComposeUiTest {
        var back = 0
        setContent {
            HearthTheme(darkTheme = false) {
                HearthTopBar(onBack = { back++ }, backDescription = "Back")
            }
        }

        onNodeWithContentDescription("Back").performClick()

        assertEquals(1, back)
    }

    @Test
    fun a_title_shows_beside_the_back_button() = runComposeUiTest {
        setContent {
            HearthTheme(darkTheme = false) {
                HearthTopBar(onBack = {}, backDescription = "Back", title = "Members")
            }
        }

        onNodeWithText("Members").assertIsDisplayed()
        onNodeWithContentDescription("Back").assertIsDisplayed()
    }
}
