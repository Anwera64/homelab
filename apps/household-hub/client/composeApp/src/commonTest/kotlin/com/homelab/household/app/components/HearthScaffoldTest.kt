package com.homelab.household.app.components

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.theme.HearthTheme
import kotlin.test.Test

/** Screens scroll: the content region moves, header and navigation stay pinned (design notes §2). */
@OptIn(ExperimentalTestApi::class)
class HearthScaffoldTest {

    @Test
    fun header_and_bottom_bar_stay_while_the_content_scrolls() = runComposeUiTest {
        setContent {
            HearthTheme(darkTheme = false) {
                HearthScaffold(
                    header = { Text("HyggeHub · Household") },
                    bottomBar = { Text("Bottom bar") }
                ) {
                    repeat(120) { Text("Row $it") }
                }
            }
        }

        onNodeWithText("Row 119").performScrollTo().assertIsDisplayed()

        onNodeWithText("Row 0").assertIsNotDisplayed()
        onNodeWithText("HyggeHub · Household").assertIsDisplayed()
        onNodeWithText("Bottom bar").assertIsDisplayed()
    }
}
