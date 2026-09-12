package com.homelab.household.app.navigation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.testing.ScreenTest
import com.homelab.household.app.testing.TestApp
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.AuthStatus
import kotlin.test.Test

/**
 * The back stack is the navigation host's only state; the screens decide when it moves.
 * `LaunchViewModelTest` covers how each decision is reached.
 */
@OptIn(ExperimentalTestApi::class)
class AppNavHostTest : ScreenTest() {

    @Test
    fun a_hub_with_members_lands_on_sign_in() = runComposeUiTest {
        setContent {
            TestApp(authStatus = { AuthStatus(isInitialized = true, memberCount = 2) }) {
                AppNavHost()
            }
        }
        waitForIdle()

        onNodeWithText("Sign in").assertIsDisplayed()
    }

    @Test
    fun a_hub_with_nobody_on_it_lands_on_first_run() = runComposeUiTest {
        setContent {
            TestApp(authStatus = { AuthStatus(isInitialized = false, memberCount = 0) }) {
                AppNavHost()
            }
        }
        waitForIdle()

        onNodeWithText("First run").assertIsDisplayed()
    }

    @Test
    fun an_unreachable_hub_stays_on_launch() = runComposeUiTest {
        setContent {
            TestApp(authStatus = { throw ServerOfflineException() }) {
                AppNavHost()
            }
        }
        waitForIdle()

        onNodeWithText("Can't reach your hub").assertIsDisplayed()
        onNodeWithText("Try again").assertIsDisplayed()
    }
}
