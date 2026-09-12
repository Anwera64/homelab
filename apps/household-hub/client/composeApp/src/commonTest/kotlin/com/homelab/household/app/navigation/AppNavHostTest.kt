package com.homelab.household.app.navigation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.screens.launch.FakeLaunchHub
import com.homelab.household.app.testing.TestApp
import com.homelab.household.app.testing.runScreenTest
import kotlin.test.Test

/**
 * The back stack is the navigation host's only state; the screens decide when it moves.
 * `LaunchViewModelTest` covers how each decision is reached.
 */
@OptIn(ExperimentalTestApi::class)
class AppNavHostTest {

    @Test
    fun a_hub_with_members_lands_on_sign_in() = runScreenTest {
        val hub = FakeLaunchHub().apply { respondsWith(initialized = true, members = 2) }

        setContent {
            TestApp(hub.engine) {
                AppNavHost()
            }
        }
        waitUntilExactlyOneExists(hasText("Sign in"), timeoutMillis = 5_000)

        onNodeWithText("Sign in").assertIsDisplayed()
    }

    @Test
    fun a_hub_with_nobody_on_it_lands_on_first_run() = runScreenTest {
        val hub = FakeLaunchHub().apply { respondsWith(initialized = false, members = 0) }

        setContent {
            TestApp(hub.engine) {
                AppNavHost()
            }
        }
        waitUntilExactlyOneExists(hasText("First run"), timeoutMillis = 5_000)

        onNodeWithText("First run").assertIsDisplayed()
    }

    @Test
    fun an_unreachable_hub_stays_on_launch() = runScreenTest {
        val hub = FakeLaunchHub().apply { isOffline() }

        setContent {
            TestApp(hub.engine) {
                AppNavHost()
            }
        }
        waitUntilExactlyOneExists(hasText("Can't reach your hub"), timeoutMillis = 5_000)

        onNodeWithText("Can't reach your hub").assertIsDisplayed()
        onNodeWithText("Try again").assertIsDisplayed()
    }
}
