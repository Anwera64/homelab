package com.homelab.household.app.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The back stack is the navigation host's only state. The screens are stubs here — what each one
 * does before it calls back is its own test's business — so this is about where the app goes,
 * and nothing else.
 */
@OptIn(ExperimentalTestApi::class)
class AppNavHostTest {

    private val backStack = mutableStateListOf<NavKey>(Destination.Launch)

    @Test
    fun it_starts_on_launch() = runComposeUiTest {
        setContent { AppNavHost(screens = StubScreens(), backStack = backStack) }

        onNodeWithText(StubScreens.LAUNCH).assertIsDisplayed()
    }

    @Test
    fun launch_sends_the_user_to_sign_in() = runComposeUiTest {
        setContent { AppNavHost(screens = StubScreens(), backStack = backStack) }

        onNodeWithText(StubScreens.GO_TO_SIGN_IN).performClick()

        onNodeWithText(StubScreens.SIGN_IN).assertIsDisplayed()
        // Launch is answered once, so it leaves the stack rather than hiding under the answer.
        assertEquals(listOf(Destination.SignIn), backStack.toList())
    }

    @Test
    fun launch_sends_the_user_to_first_run() = runComposeUiTest {
        setContent { AppNavHost(screens = StubScreens(), backStack = backStack) }

        onNodeWithText(StubScreens.GO_TO_FIRST_RUN).performClick()

        onNodeWithText(StubScreens.FIRST_RUN).assertIsDisplayed()
        assertEquals(listOf(Destination.FirstRun), backStack.toList())
    }

    @Test
    fun first_run_goes_home_once_the_household_exists() = runComposeUiTest {
        setContent { AppNavHost(screens = StubScreens(), backStack = backStack) }

        onNodeWithText(StubScreens.GO_TO_FIRST_RUN).performClick()
        onNodeWithText(StubScreens.CREATED).performClick()

        onNodeWithText(StubScreens.HOME).assertIsDisplayed()
        // The form is done with; back from home doesn't return to it.
        assertEquals(listOf(Destination.Home), backStack.toList())
    }

    @Test
    fun first_run_on_a_hub_already_set_up_goes_to_sign_in() = runComposeUiTest {
        setContent { AppNavHost(screens = StubScreens(), backStack = backStack) }

        onNodeWithText(StubScreens.GO_TO_FIRST_RUN).performClick()
        onNodeWithText(StubScreens.SIGN_IN_INSTEAD).performClick()

        onNodeWithText(StubScreens.SIGN_IN).assertIsDisplayed()
        assertEquals(listOf(Destination.SignIn), backStack.toList())
    }

    @Test
    fun launch_sends_a_member_still_signed_in_straight_home() = runComposeUiTest {
        setContent { AppNavHost(screens = StubScreens(), backStack = backStack) }

        onNodeWithText(StubScreens.GO_HOME).performClick()

        onNodeWithText(StubScreens.HOME).assertIsDisplayed()
        assertEquals(listOf(Destination.Home), backStack.toList())
    }
}
