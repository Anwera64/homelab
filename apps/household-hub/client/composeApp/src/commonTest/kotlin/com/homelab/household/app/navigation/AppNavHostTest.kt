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
    private val session = StubSession()

    @Test
    fun a_phone_the_hub_signs_out_starts_over_at_who_is_here() = runComposeUiTest {
        backStack[0] = Destination.Home
        setContent { AppNavHost(screens = StubScreens(), backStack = backStack, session = session) }
        onNodeWithText(StubScreens.HOME).assertIsDisplayed()

        session.hubSignsThisPhoneOut()

        onNodeWithText(StubScreens.SIGN_IN).assertIsDisplayed()
        assertEquals(listOf(Destination.SignIn), backStack.toList())
    }

    @Test
    fun the_session_is_renewed_once_when_the_app_opens() = runComposeUiTest {
        backStack[0] = Destination.Home
        setContent { AppNavHost(screens = StubScreens(), backStack = backStack, session = session) }

        waitForIdle()
        assertEquals(1, session.renewals)
    }

    @Test
    fun it_starts_on_launch() = runComposeUiTest {
        setContent { AppNavHost(screens = StubScreens(), backStack = backStack, session = session) }

        onNodeWithText(StubScreens.LAUNCH).assertIsDisplayed()
    }

    @Test
    fun launch_sends_the_user_to_sign_in() = runComposeUiTest {
        setContent { AppNavHost(screens = StubScreens(), backStack = backStack, session = session) }

        onNodeWithText(StubScreens.GO_TO_SIGN_IN).performClick()

        onNodeWithText(StubScreens.SIGN_IN).assertIsDisplayed()
        // Launch is answered once, so it leaves the stack rather than hiding under the answer.
        assertEquals(listOf(Destination.SignIn), backStack.toList())
    }

    @Test
    fun launch_sends_the_user_to_first_run() = runComposeUiTest {
        setContent { AppNavHost(screens = StubScreens(), backStack = backStack, session = session) }

        onNodeWithText(StubScreens.GO_TO_FIRST_RUN).performClick()

        onNodeWithText(StubScreens.FIRST_RUN).assertIsDisplayed()
        assertEquals(listOf(Destination.FirstRun), backStack.toList())
    }

    @Test
    fun first_run_goes_home_once_the_household_exists() = runComposeUiTest {
        setContent { AppNavHost(screens = StubScreens(), backStack = backStack, session = session) }

        onNodeWithText(StubScreens.GO_TO_FIRST_RUN).performClick()
        onNodeWithText(StubScreens.CREATED).performClick()

        onNodeWithText(StubScreens.HOME).assertIsDisplayed()
        // The form is done with; back from home doesn't return to it.
        assertEquals(listOf(Destination.Home), backStack.toList())
    }

    @Test
    fun first_run_on_a_hub_already_set_up_goes_to_sign_in() = runComposeUiTest {
        setContent { AppNavHost(screens = StubScreens(), backStack = backStack, session = session) }

        onNodeWithText(StubScreens.GO_TO_FIRST_RUN).performClick()
        onNodeWithText(StubScreens.SIGN_IN_INSTEAD).performClick()

        onNodeWithText(StubScreens.SIGN_IN).assertIsDisplayed()
        assertEquals(listOf(Destination.SignIn), backStack.toList())
    }

    @Test
    fun tapping_a_face_opens_that_members_pin_over_the_picker() = runComposeUiTest {
        setContent { AppNavHost(screens = StubScreens(), backStack = backStack, session = session) }

        onNodeWithText(StubScreens.GO_TO_SIGN_IN).performClick()
        onNodeWithText(StubScreens.PICK_EMMA).performClick()

        onNodeWithText(StubScreens.pinOf(StubScreens.EMMA)).assertIsDisplayed()
        assertEquals(listOf(Destination.SignIn, Destination.Pin(StubScreens.EMMA)), backStack.toList())
    }

    @Test
    fun back_from_the_pin_returns_to_the_picker() = runComposeUiTest {
        setContent { AppNavHost(screens = StubScreens(), backStack = backStack, session = session) }

        onNodeWithText(StubScreens.GO_TO_SIGN_IN).performClick()
        onNodeWithText(StubScreens.PICK_EMMA).performClick()
        onNodeWithText(StubScreens.BACK).performClick()

        onNodeWithText(StubScreens.SIGN_IN).assertIsDisplayed()
        assertEquals(listOf(Destination.SignIn), backStack.toList())
    }

    @Test
    fun the_right_pin_goes_home_and_leaves_sign_in_behind() = runComposeUiTest {
        setContent { AppNavHost(screens = StubScreens(), backStack = backStack, session = session) }

        onNodeWithText(StubScreens.GO_TO_SIGN_IN).performClick()
        onNodeWithText(StubScreens.PICK_EMMA).performClick()
        onNodeWithText(StubScreens.SIGNED_IN).performClick()

        onNodeWithText(StubScreens.HOME).assertIsDisplayed()
        assertEquals(listOf(Destination.Home), backStack.toList())
    }
}
