package com.homelab.household.app.navigation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.presentation.viewmodel.auth.model.HubStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Navigation renders the destination for the state it is given; `AuthViewModelTest` covers
 * how that state is reached.
 */
@OptIn(ExperimentalTestApi::class)
class AppNavigationTest {

    private val hubAddress = "hub.spicy-llama.duckdns.org"

    @Test
    fun the_app_starts_on_launch() = runComposeUiTest {
        setContent {
            HearthTheme(darkTheme = false) {
                AppNavigation(hubStatus = HubStatus.Checking, hubAddress = hubAddress, onRetry = {})
            }
        }

        onNodeWithText("Reaching your hub…").assertIsDisplayed()
        onNodeWithText(hubAddress).assertIsDisplayed()
    }

    @Test
    fun launch_shows_a_ready_hub() = runComposeUiTest {
        setContent {
            HearthTheme(darkTheme = false) {
                AppNavigation(hubStatus = HubStatus.Ready(memberCount = 2), hubAddress = hubAddress, onRetry = {})
            }
        }

        onNodeWithText("Your hub is ready").assertIsDisplayed()
        onNodeWithText("2 members").assertIsDisplayed()
    }

    @Test
    fun launch_shows_a_hub_with_nobody_on_it() = runComposeUiTest {
        setContent {
            HearthTheme(darkTheme = false) {
                AppNavigation(hubStatus = HubStatus.FirstRun, hubAddress = hubAddress, onRetry = {})
            }
        }

        onNodeWithText("Nobody lives here yet").assertIsDisplayed()
    }

    @Test
    fun retry_from_an_unreachable_hub_reaches_the_caller() = runComposeUiTest {
        var retries = 0
        setContent {
            HearthTheme(darkTheme = false) {
                AppNavigation(hubStatus = HubStatus.Unreachable, hubAddress = hubAddress, onRetry = { retries++ })
            }
        }

        onNodeWithText("Try again").performClick()

        assertEquals(1, retries)
    }
}
