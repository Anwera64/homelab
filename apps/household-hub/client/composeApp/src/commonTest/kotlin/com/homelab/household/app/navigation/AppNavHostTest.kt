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
    fun a_phone_the_hub_signs_out_starts_over_at_who_is_here() =
        runComposeUiTest {
            backStack[0] = Destination.Home
            setContent { AppNavHost(screens = StubScreens(), backStack = backStack, session = session) }
            onNodeWithText(StubScreens.HOME).assertIsDisplayed()

            session.hubSignsThisPhoneOut()

            onNodeWithText(StubScreens.SIGN_IN).assertIsDisplayed()
            assertEquals(listOf(Destination.SignIn), backStack.toList())
        }

    @Test
    fun the_session_is_renewed_once_when_the_app_opens() =
        runComposeUiTest {
            backStack[0] = Destination.Home
            setContent { AppNavHost(screens = StubScreens(), backStack = backStack, session = session) }

            waitForIdle()
            assertEquals(1, session.renewals)
        }

    @Test
    fun it_starts_on_launch() =
        runComposeUiTest {
            setContent { AppNavHost(screens = StubScreens(), backStack = backStack, session = session) }

            onNodeWithText(StubScreens.LAUNCH).assertIsDisplayed()
        }

    @Test
    fun launch_sends_the_user_to_sign_in() =
        runComposeUiTest {
            setContent { AppNavHost(screens = StubScreens(), backStack = backStack, session = session) }

            onNodeWithText(StubScreens.GO_TO_SIGN_IN).performClick()

            onNodeWithText(StubScreens.SIGN_IN).assertIsDisplayed()
            // Launch is answered once, so it leaves the stack rather than hiding under the answer.
            assertEquals(listOf(Destination.SignIn), backStack.toList())
        }

    @Test
    fun launch_sends_the_user_to_first_run() =
        runComposeUiTest {
            setContent { AppNavHost(screens = StubScreens(), backStack = backStack, session = session) }

            onNodeWithText(StubScreens.GO_TO_FIRST_RUN).performClick()

            onNodeWithText(StubScreens.FIRST_RUN).assertIsDisplayed()
            assertEquals(listOf(Destination.FirstRun), backStack.toList())
        }

    @Test
    fun first_run_goes_home_once_the_household_exists() =
        runComposeUiTest {
            setContent { AppNavHost(screens = StubScreens(), backStack = backStack, session = session) }

            onNodeWithText(StubScreens.GO_TO_FIRST_RUN).performClick()
            onNodeWithText(StubScreens.CREATED).performClick()

            onNodeWithText(StubScreens.HOME).assertIsDisplayed()
            // The form is done with; back from home doesn't return to it.
            assertEquals(listOf(Destination.Home), backStack.toList())
        }

    @Test
    fun first_run_on_a_hub_already_set_up_goes_to_sign_in() =
        runComposeUiTest {
            setContent { AppNavHost(screens = StubScreens(), backStack = backStack, session = session) }

            onNodeWithText(StubScreens.GO_TO_FIRST_RUN).performClick()
            onNodeWithText(StubScreens.SIGN_IN_INSTEAD).performClick()

            onNodeWithText(StubScreens.SIGN_IN).assertIsDisplayed()
            assertEquals(listOf(Destination.SignIn), backStack.toList())
        }

    @Test
    fun tapping_a_face_opens_that_members_pin_over_the_picker() =
        runComposeUiTest {
            setContent { AppNavHost(screens = StubScreens(), backStack = backStack, session = session) }

            onNodeWithText(StubScreens.GO_TO_SIGN_IN).performClick()
            onNodeWithText(StubScreens.PICK_EMMA).performClick()

            onNodeWithText(StubScreens.pinOf(StubScreens.EMMA)).assertIsDisplayed()
            assertEquals(listOf(Destination.SignIn, Destination.Pin(StubScreens.EMMA)), backStack.toList())
        }

    @Test
    fun back_from_the_pin_returns_to_the_picker() =
        runComposeUiTest {
            setContent { AppNavHost(screens = StubScreens(), backStack = backStack, session = session) }

            onNodeWithText(StubScreens.GO_TO_SIGN_IN).performClick()
            onNodeWithText(StubScreens.PICK_EMMA).performClick()
            onNodeWithText(StubScreens.BACK).performClick()

            onNodeWithText(StubScreens.SIGN_IN).assertIsDisplayed()
            assertEquals(listOf(Destination.SignIn), backStack.toList())
        }

    @Test
    fun the_right_pin_goes_home_and_leaves_sign_in_behind() =
        runComposeUiTest {
            setContent { AppNavHost(screens = StubScreens(), backStack = backStack, session = session) }

            onNodeWithText(StubScreens.GO_TO_SIGN_IN).performClick()
            onNodeWithText(StubScreens.PICK_EMMA).performClick()
            onNodeWithText(StubScreens.SIGNED_IN).performClick()

            onNodeWithText(StubScreens.HOME).assertIsDisplayed()
            assertEquals(listOf(Destination.Home), backStack.toList())
        }

    @Test
    fun someone_with_an_invite_types_the_code_and_joins() =
        runComposeUiTest {
            setContent { AppNavHost(screens = StubScreens(), backStack = backStack, session = session) }

            onNodeWithText(StubScreens.GO_TO_SIGN_IN).performClick()
            onNodeWithText(StubScreens.HAVE_AN_INVITE).performClick()
            onNodeWithText(StubScreens.CODE_ACCEPTED).performClick()

            onNodeWithText(StubScreens.joinOf(StubScreens.INVITE)).assertIsDisplayed()
            onNodeWithText(StubScreens.JOINED).performClick()

            onNodeWithText(StubScreens.HOME).assertIsDisplayed()
            assertEquals(listOf(Destination.Home), backStack.toList())
        }

    @Test
    fun a_code_that_has_gone_sends_them_back_to_typing_one() =
        runComposeUiTest {
            setContent { AppNavHost(screens = StubScreens(), backStack = backStack, session = session) }

            onNodeWithText(StubScreens.GO_TO_SIGN_IN).performClick()
            onNodeWithText(StubScreens.HAVE_AN_INVITE).performClick()
            onNodeWithText(StubScreens.CODE_ACCEPTED).performClick()
            onNodeWithText(StubScreens.CODE_EXPIRED).performClick()

            onNodeWithText(StubScreens.INVITE_CODE).assertIsDisplayed()
            assertEquals(listOf(Destination.InviteCode), backStack.toList())
        }

    @Test
    fun a_forgotten_pin_goes_through_a_reset_code_to_a_new_one() =
        runComposeUiTest {
            setContent { AppNavHost(screens = StubScreens(), backStack = backStack, session = session) }

            onNodeWithText(StubScreens.GO_TO_SIGN_IN).performClick()
            onNodeWithText(StubScreens.PICK_EMMA).performClick()
            onNodeWithText(StubScreens.FORGOTTEN).performClick()
            onNodeWithText(StubScreens.forgotOf(StubScreens.EMMA)).assertIsDisplayed()

            onNodeWithText(StubScreens.HAVE_A_RESET_CODE).performClick()
            onNodeWithText(StubScreens.RESET_CODE_TYPED).performClick()
            onNodeWithText(StubScreens.newPinFor("P4XN7T")).assertIsDisplayed()
            onNodeWithText(StubScreens.SIGNED_IN).performClick()

            onNodeWithText(StubScreens.HOME).assertIsDisplayed()
            assertEquals(listOf(Destination.Home), backStack.toList())
        }

    @Test
    fun the_profile_opens_from_home_and_leads_to_members() =
        runComposeUiTest {
            backStack[0] = Destination.Home
            setContent { AppNavHost(screens = StubScreens(), backStack = backStack, session = session) }

            onNodeWithText(StubScreens.GO_TO_PROFILE).performClick()
            onNodeWithText(StubScreens.GO_TO_MEMBERS).performClick()

            onNodeWithText(StubScreens.MEMBERS).assertIsDisplayed()
            assertEquals(listOf(Destination.Home, Destination.Profile, Destination.Members), backStack.toList())
        }

    @Test
    fun members_leads_to_inviting_approving_and_removing() =
        runComposeUiTest {
            backStack[0] = Destination.Members
            setContent { AppNavHost(screens = StubScreens(), backStack = backStack, session = session) }

            onNodeWithText(StubScreens.GO_TO_INVITE).performClick()
            onNodeWithText(StubScreens.INVITE_CREATE).assertIsDisplayed()
            onNodeWithText(StubScreens.BACK).performClick()

            onNodeWithText(StubScreens.RESET_LIAMS_PIN).performClick()
            onNodeWithText(StubScreens.approveOf(StubScreens.LIAM)).assertIsDisplayed()
            onNodeWithText(StubScreens.BACK).performClick()

            onNodeWithText(StubScreens.REMOVE_LIAM).performClick()
            onNodeWithText(StubScreens.removeOf(StubScreens.LIAM)).assertIsDisplayed()
            onNodeWithText(StubScreens.REMOVED).performClick()

            onNodeWithText(StubScreens.MEMBERS).assertIsDisplayed()
            assertEquals(listOf(Destination.Members), backStack.toList())
        }

    @Test
    fun leaving_the_household_ends_at_who_is_here() =
        runComposeUiTest {
            backStack[0] = Destination.Profile
            setContent { AppNavHost(screens = StubScreens(), backStack = backStack, session = session) }

            onNodeWithText(StubScreens.GO_TO_LEAVE).performClick()
            onNodeWithText(StubScreens.LEFT).performClick()

            onNodeWithText(StubScreens.SIGN_IN).assertIsDisplayed()
            assertEquals(listOf(Destination.SignIn), backStack.toList())
        }

    @Test
    fun signing_out_ends_at_who_is_here() =
        runComposeUiTest {
            backStack[0] = Destination.Profile
            setContent { AppNavHost(screens = StubScreens(), backStack = backStack, session = session) }

            onNodeWithText(StubScreens.SIGNED_OUT).performClick()

            onNodeWithText(StubScreens.SIGN_IN).assertIsDisplayed()
            assertEquals(listOf(Destination.SignIn), backStack.toList())
        }

    @Test
    fun a_changed_pin_returns_to_the_profile() =
        runComposeUiTest {
            backStack[0] = Destination.Profile
            setContent { AppNavHost(screens = StubScreens(), backStack = backStack, session = session) }

            onNodeWithText(StubScreens.GO_TO_CHANGE_PIN).performClick()
            onNodeWithText(StubScreens.PIN_CHANGED).performClick()

            onNodeWithText(StubScreens.PROFILE).assertIsDisplayed()
            assertEquals(listOf(Destination.Profile), backStack.toList())
        }
}
