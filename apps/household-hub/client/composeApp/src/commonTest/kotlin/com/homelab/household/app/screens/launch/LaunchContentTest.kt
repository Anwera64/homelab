package com.homelab.household.app.screens.launch

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.presentation.launch.HubStatus
import com.homelab.household.presentation.launch.LaunchUiState
import kotlin.test.Test
import kotlin.test.assertEquals

/** Launch is the first thing a hub does: call GET /auth/status, then say what it found. */
@OptIn(ExperimentalTestApi::class)
class LaunchContentTest {

    private val hubAddress = "hub.spicy-llama.duckdns.org"

    private fun ComposeUiTest.launch(status: HubStatus, onRetry: () -> Unit = {}) {
        setContent {
            HearthTheme(darkTheme = false) {
                LaunchContent(
                    state = LaunchUiState(hubAddress = hubAddress, status = status),
                    onRetry = onRetry
                )
            }
        }
    }

    @Test
    fun checking_names_the_hub_it_is_reaching() = runComposeUiTest {
        launch(HubStatus.Checking)

        onNodeWithText("Reaching your hub…").assertIsDisplayed()
        onNodeWithText(hubAddress).assertIsDisplayed()
    }

    @Test
    fun ready_shows_how_many_members_the_hub_has() = runComposeUiTest {
        launch(HubStatus.Ready(memberCount = 2))

        onNodeWithText("Your hub is ready").assertIsDisplayed()
        onNodeWithText("2 members").assertIsDisplayed()
    }

    @Test
    fun first_run_says_the_hub_has_nobody_yet() = runComposeUiTest {
        launch(HubStatus.FirstRun)

        onNodeWithText("Nobody lives here yet").assertIsDisplayed()
    }

    @Test
    fun unreachable_explains_and_offers_to_try_again() = runComposeUiTest {
        var retries = 0
        launch(HubStatus.Unreachable) { retries++ }

        onNodeWithText("Can't reach your hub").assertIsDisplayed()
        onNodeWithText("Try again").assertIsDisplayed().performClick()

        assertEquals(1, retries)
    }

    @Test
    fun a_failure_keeps_the_hub_s_own_words() = runComposeUiTest {
        launch(HubStatus.Failed(message = "Unexpected status 500"))

        onNodeWithText("Unexpected status 500").assertIsDisplayed()
        onNodeWithText("Try again").assertIsDisplayed()
    }

    /** The preview provider is the list of states launch can be in; each one has to draw. */
    @Test
    fun every_previewed_state_draws() {
        val states = LaunchUiStateProvider().values.toList()

        states.forEach { state ->
            runComposeUiTest {
                setContent {
                    HearthTheme(darkTheme = false) {
                        LaunchContent(state = state, onRetry = {})
                    }
                }

                onNodeWithText(state.hubAddress).assertIsDisplayed()
            }
        }

        assertEquals(5, states.size)
    }
}
