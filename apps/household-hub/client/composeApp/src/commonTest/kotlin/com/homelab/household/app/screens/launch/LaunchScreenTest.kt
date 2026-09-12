package com.homelab.household.app.screens.launch

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.testing.ScreenTest
import com.homelab.household.app.testing.TestApp
import com.homelab.household.domain.model.AuthStatus
import com.homelab.household.domain.exception.ServerOfflineException
import kotlin.test.Test
import kotlin.test.assertEquals

/** The screen owns its ViewModel: give it a hub, and it decides where the user goes next. */
@OptIn(ExperimentalTestApi::class)
class LaunchScreenTest : ScreenTest() {

    @Test
    fun a_hub_with_members_sends_the_user_to_sign_in() = runComposeUiTest {
        var signIn = 0
        var firstRun = 0

        setContent {
            TestApp(authStatus = { AuthStatus(isInitialized = true, memberCount = 2) }) {
                LaunchScreen(onSignIn = { signIn++ }, onFirstRun = { firstRun++ })
            }
        }
        waitForIdle()

        assertEquals(1, signIn)
        assertEquals(0, firstRun)
    }

    @Test
    fun an_empty_hub_sends_the_user_to_first_run() = runComposeUiTest {
        var signIn = 0
        var firstRun = 0

        setContent {
            TestApp(authStatus = { AuthStatus(isInitialized = false, memberCount = 0) }) {
                LaunchScreen(onSignIn = { signIn++ }, onFirstRun = { firstRun++ })
            }
        }
        waitForIdle()

        assertEquals(0, signIn)
        assertEquals(1, firstRun)
    }

    @Test
    fun an_unreachable_hub_keeps_the_user_here_with_something_to_retry() = runComposeUiTest {
        var moved = 0

        setContent {
            TestApp(authStatus = { throw ServerOfflineException() }) {
                LaunchScreen(onSignIn = { moved++ }, onFirstRun = { moved++ })
            }
        }
        waitForIdle()

        onNodeWithText("Can't reach your hub").assertIsDisplayed()
        onNodeWithText("Try again").assertIsDisplayed()
        assertEquals(0, moved)
    }
}
