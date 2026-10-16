package com.homelab.household.app.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation3.runtime.NavKey
import com.homelab.household.domain.model.CalendarProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The back stack is the navigation host's only state.
 *
 * Wrapped in `StillTheme` because the host draws the real bottom bar — the tabs are navigation's
 * business, not a screen's — and an animated component without the theme's off switch would hang
 * `waitForIdle` rather than fail. The screens are stubs here — what each one
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
            onAppNavHost {
                backStack[0] = Destination.Home
                appNavHost(StubScreens(), backStack, session)
                seesHome()

                session.hubSignsThisPhoneOut()

                seesSignIn()
                assertEquals(listOf(Destination.SignIn), backStack.toList())
            }
        }

    @Test
    fun the_session_is_renewed_once_when_the_app_opens() =
        runComposeUiTest {
            onAppNavHost {
                backStack[0] = Destination.Home
                appNavHost(StubScreens(), backStack, session)

                waitForIdle()
                assertEquals(1, session.renewals)
            }
        }

    @Test
    fun it_starts_on_launch() =
        runComposeUiTest {
            onAppNavHost {
                appNavHost(StubScreens(), backStack, session)

                seesLaunch()
            }
        }

    @Test
    fun launch_sends_the_user_to_sign_in() =
        runComposeUiTest {
            onAppNavHost {
                appNavHost(StubScreens(), backStack, session)

                tapsGoToSignIn()

                seesSignIn()
                // Launch is answered once, so it leaves the stack rather than hiding under the answer.
                assertEquals(listOf(Destination.SignIn), backStack.toList())
            }
        }

    @Test
    fun launch_sends_the_user_to_first_run() =
        runComposeUiTest {
            onAppNavHost {
                appNavHost(StubScreens(), backStack, session)

                tapsGoToFirstRun()

                seesFirstRun()
                assertEquals(listOf(Destination.FirstRun), backStack.toList())
            }
        }

    @Test
    fun first_run_goes_home_once_the_household_exists() =
        runComposeUiTest {
            onAppNavHost {
                appNavHost(StubScreens(), backStack, session)

                tapsGoToFirstRun()
                tapsCreatedHousehold()

                seesHome()
                // The form is done with; back from home doesn't return to it.
                assertEquals(listOf(Destination.Home), backStack.toList())
            }
        }

    @Test
    fun first_run_on_a_hub_already_set_up_goes_to_sign_in() =
        runComposeUiTest {
            onAppNavHost {
                appNavHost(StubScreens(), backStack, session)

                tapsGoToFirstRun()
                tapsSignInInstead()

                seesSignIn()
                assertEquals(listOf(Destination.SignIn), backStack.toList())
            }
        }

    @Test
    fun tapping_a_face_opens_that_members_pin_over_the_picker() =
        runComposeUiTest {
            onAppNavHost {
                appNavHost(StubScreens(), backStack, session)

                tapsGoToSignIn()
                tapsPickEmma()

                seesThePinPadOf(StubScreens.EMMA)
                assertEquals(listOf(Destination.SignIn, Destination.Pin(StubScreens.EMMA)), backStack.toList())
            }
        }

    // ---- a destination's ViewModels ---------------------------------------

    @Test
    fun `GIVEN a conversation WHEN a screen is opened over it and closed THEN it still has the same ViewModel`() =
        runComposeUiTest {
            onAppNavHost {
                // GIVEN
                val screens = StubScreens()
                backStack[0] = Destination.Conversation()
                appNavHost(screens, backStack, session)

                // WHEN
                tapsReconnectCalendar()
                seesTheCalendarPicker()
                tapsBack()

                // THEN
                seesANewConversation()
                val kept = screens.viewModels.single()
                assertFalse(kept.cleared)
            }
        }

    @Test
    fun `GIVEN a conversation that opened the connect flow WHEN a calendar connects THEN the conversation hears it once`() =
        runComposeUiTest {
            onAppNavHost {
                // GIVEN
                val screens = StubScreens()
                backStack[0] = Destination.Conversation("s-1")
                appNavHost(screens, backStack, session)

                // WHEN
                tapsReconnectCalendar()
                tapsPickApple()
                tapsCalendarConnected()
                waitForIdle()

                // THEN
                seesTheConversation("s-1")
                assertEquals(1, screens.calendarsConnected)
            }
        }

    @Test
    fun `GIVEN a conversation that opened the connect flow WHEN it is left with Back THEN the conversation hears nothing`() =
        runComposeUiTest {
            onAppNavHost {
                // GIVEN
                val screens = StubScreens()
                backStack[0] = Destination.Conversation("s-1")
                appNavHost(screens, backStack, session)

                // WHEN
                tapsReconnectCalendar()
                tapsBack()
                waitForIdle()

                // THEN
                assertEquals(0, screens.calendarsConnected)
            }
        }

    @Test
    fun `GIVEN a PIN pad closed with Back WHEN it is opened again THEN it has a new ViewModel`() =
        runComposeUiTest {
            onAppNavHost {
                // GIVEN
                val screens = StubScreens()
                backStack[0] = Destination.SignIn
                appNavHost(screens, backStack, session)
                tapsPickEmma()
                tapsBack()
                waitForIdle()

                // WHEN
                tapsPickEmma()
                waitForIdle()

                // THEN
                assertEquals(2, screens.viewModels.size)
                assertTrue(screens.viewModels.first().cleared)
                assertFalse(screens.viewModels.last().cleared)
            }
        }

    @Test
    fun back_from_the_pin_returns_to_the_picker() =
        runComposeUiTest {
            onAppNavHost {
                appNavHost(StubScreens(), backStack, session)

                tapsGoToSignIn()
                tapsPickEmma()
                tapsBack()

                seesSignIn()
                assertEquals(listOf(Destination.SignIn), backStack.toList())
            }
        }

    @Test
    fun the_right_pin_goes_home_and_leaves_sign_in_behind() =
        runComposeUiTest {
            onAppNavHost {
                appNavHost(StubScreens(), backStack, session)

                tapsGoToSignIn()
                tapsPickEmma()
                tapsSignedIn()

                seesHome()
                assertEquals(listOf(Destination.Home), backStack.toList())
            }
        }

    @Test
    fun someone_with_an_invite_types_the_code_and_joins() =
        runComposeUiTest {
            onAppNavHost {
                appNavHost(StubScreens(), backStack, session)

                tapsGoToSignIn()
                tapsHaveAnInvite()
                tapsCodeAccepted()

                seesTheJoinFor(StubScreens.INVITE)
                tapsJoined()

                seesHome()
                assertEquals(listOf(Destination.Home), backStack.toList())
            }
        }

    @Test
    fun a_code_that_has_gone_sends_them_back_to_typing_one() =
        runComposeUiTest {
            onAppNavHost {
                appNavHost(StubScreens(), backStack, session)

                tapsGoToSignIn()
                tapsHaveAnInvite()
                tapsCodeAccepted()
                tapsCodeExpired()

                seesTheInviteCode()
                assertEquals(listOf(Destination.InviteCode), backStack.toList())
            }
        }

    @Test
    fun a_forgotten_pin_goes_through_a_reset_code_to_a_new_one() =
        runComposeUiTest {
            onAppNavHost {
                appNavHost(StubScreens(), backStack, session)

                tapsGoToSignIn()
                tapsPickEmma()
                tapsForgotten()
                seesTheForgottenPinOf(StubScreens.EMMA)

                tapsHaveAResetCode()
                tapsResetCodeTyped()
                seesTheNewPinFor("P4XN7T")
                tapsSignedIn()

                seesHome()
                assertEquals(listOf(Destination.Home), backStack.toList())
            }
        }

    @Test
    fun the_profile_opens_from_home_and_leads_to_members() =
        runComposeUiTest {
            onAppNavHost {
                backStack[0] = Destination.Home
                appNavHost(StubScreens(), backStack, session)

                tapsGoToProfile()
                tapsGoToMembers()

                seesMembers()
                assertEquals(listOf(Destination.Home, Destination.Profile, Destination.Members), backStack.toList())
            }
        }

    @Test
    fun the_profile_leads_to_the_memory_page_and_back() =
        runComposeUiTest {
            onAppNavHost {
                backStack[0] = Destination.Profile
                appNavHost(StubScreens(), backStack, session)

                tapsGoToMemories()
                seesMemories()
                assertEquals(listOf(Destination.Profile, Destination.Memories), backStack.toList())

                tapsBack()

                seesProfile()
                assertEquals(listOf(Destination.Profile), backStack.toList())
            }
        }

    @Test
    fun the_memory_page_leads_to_changes_to_check_and_back() =
        runComposeUiTest {
            onAppNavHost {
                backStack[0] = Destination.Memories
                appNavHost(StubScreens(), backStack, session)

                tapsGoToChangesToCheck()
                seesChangesToCheck()
                assertEquals(listOf(Destination.Memories, Destination.ChangesToCheck), backStack.toList())

                tapsBack()

                seesMemories()
                assertEquals(listOf(Destination.Memories), backStack.toList())
            }
        }

    @Test
    fun the_profile_leads_to_when_agents_act_and_back() =
        runComposeUiTest {
            onAppNavHost {
                backStack[0] = Destination.Profile
                appNavHost(StubScreens(), backStack, session)

                tapsGoToWhenAgentsAct()
                seesWhenAgentsAct()
                assertEquals(listOf(Destination.Profile, Destination.WhenAgentsAct), backStack.toList())

                tapsBack()

                seesProfile()
                assertEquals(listOf(Destination.Profile), backStack.toList())
            }
        }

    @Test
    fun members_leads_to_inviting_approving_and_removing() =
        runComposeUiTest {
            onAppNavHost {
                backStack[0] = Destination.Members
                appNavHost(StubScreens(), backStack, session)

                tapsGoToInvite()
                seesTheInviteCreate()
                tapsBack()

                tapsResetLiamsPin()
                seesTheApprovalFor(StubScreens.LIAM)
                tapsBack()

                tapsRemoveLiam()
                seesTheRemovalOf(StubScreens.LIAM)
                tapsRemoved()

                seesMembers()
                assertEquals(listOf(Destination.Members), backStack.toList())
            }
        }

    @Test
    fun leaving_the_household_ends_at_who_is_here() =
        runComposeUiTest {
            onAppNavHost {
                backStack[0] = Destination.Profile
                appNavHost(StubScreens(), backStack, session)

                tapsGoToLeave()
                tapsLeft()

                seesSignIn()
                assertEquals(listOf(Destination.SignIn), backStack.toList())
            }
        }

    @Test
    fun signing_out_ends_at_who_is_here() =
        runComposeUiTest {
            onAppNavHost {
                backStack[0] = Destination.Profile
                appNavHost(StubScreens(), backStack, session)

                tapsSignedOut()

                seesSignIn()
                assertEquals(listOf(Destination.SignIn), backStack.toList())
            }
        }

    @Test
    fun a_changed_pin_returns_to_the_profile() =
        runComposeUiTest {
            onAppNavHost {
                backStack[0] = Destination.Profile
                appNavHost(StubScreens(), backStack, session)

                tapsGoToChangePin()
                tapsPinChanged()

                seesProfile()
                assertEquals(listOf(Destination.Profile), backStack.toList())
            }
        }

    @Test
    fun a_connected_calendar_returns_to_the_profile_past_the_picker() =
        runComposeUiTest {
            onAppNavHost {
                backStack[0] = Destination.Profile
                appNavHost(StubScreens(), backStack, session)

                tapsGoToCalendar()
                tapsPickApple()
                seesTheCalendarConnectFor(CalendarProvider.APPLE)
                tapsCalendarConnected()

                seesProfile()
                assertEquals(listOf(Destination.Profile), backStack.toList())
            }
        }

    @Test
    fun `GIVEN the picker WHEN Google is picked and signed in THEN the profile is back past the picker`() =
        runComposeUiTest {
            onAppNavHost {
                // GIVEN
                backStack[0] = Destination.Profile
                appNavHost(StubScreens(), backStack, session)
                tapsGoToCalendar()

                // WHEN
                tapsPickGoogle()
                seesTheGoogleSignIn()
                tapsCalendarConnected()

                // THEN
                seesProfile()
                assertEquals(listOf(Destination.Profile), backStack.toList())
            }
        }

    @Test
    fun `GIVEN a Google calendar that needs signing in again WHEN the profile asks for it THEN the sign-in opens straight away`() =
        runComposeUiTest {
            onAppNavHost {
                // GIVEN
                backStack[0] = Destination.Profile
                appNavHost(StubScreens(), backStack, session)

                // WHEN
                tapsSignInToGoogleAgain()

                // THEN
                seesTheGoogleSignIn()
                assertEquals(listOf(Destination.Profile, Destination.GoogleCalendarSignIn), backStack.toList())
            }
        }

    @Test
    fun `GIVEN a failed calendar step WHEN Reconnect calendar is tapped and a calendar connected THEN the conversation is back`() =
        runComposeUiTest {
            onAppNavHost {
                // GIVEN
                backStack[0] = Destination.Conversation("s-1")
                appNavHost(StubScreens(), backStack, session)

                // WHEN
                tapsReconnectCalendar()
                seesTheCalendarPicker()
                tapsPickApple()
                tapsCalendarConnected()

                // THEN
                seesTheConversation("s-1")
                assertEquals(listOf(Destination.Conversation("s-1")), backStack.toList())
            }
        }

    @Test
    fun back_from_a_calendar_s_details_returns_to_the_picker() =
        runComposeUiTest {
            onAppNavHost {
                backStack[0] = Destination.Profile
                appNavHost(StubScreens(), backStack, session)

                tapsGoToCalendar()
                tapsPickApple()
                tapsBack()

                seesTheCalendarPicker()
                assertEquals(listOf(Destination.Profile, Destination.CalendarPicker), backStack.toList())
            }
        }

    // ---- the four tabs and the raised + ------------------------------------

    @Test
    fun each_tab_reaches_its_own_screen() =
        runComposeUiTest {
            onAppNavHost {
                backStack[0] = Destination.Home
                appNavHost(StubScreens(), backStack, session)

                tapsTheChatsTab()
                seesChats()

                tapsTheScheduleTab()
                seesSchedule()

                tapsTheMySpaceTab()
                seesMySpace()

                tapsTheHouseholdTab()
                seesHome()
            }
        }

    @Test
    fun a_tab_replaces_the_stack_rather_than_piling_onto_it() =
        runComposeUiTest {
            onAppNavHost {
                backStack[0] = Destination.Home
                appNavHost(StubScreens(), backStack, session)

                tapsTheChatsTab()
                tapsTheScheduleTab()

                // Otherwise Back would walk the tabs you happened to visit instead of leaving.
                assertEquals(listOf(Destination.Schedule), backStack.toList())
            }
        }

    @Test
    fun the_raised_plus_opens_a_conversation_that_has_no_session_yet() =
        runComposeUiTest {
            onAppNavHost {
                backStack[0] = Destination.Home
                appNavHost(StubScreens(), backStack, session)

                tapsTheRaisedPlus()

                seesANewConversation()
                assertEquals(listOf(Destination.Home, Destination.Conversation(null)), backStack.toList())
            }
        }

    @Test
    fun opening_a_chat_stacks_it_on_the_list_so_back_returns_there() =
        runComposeUiTest {
            onAppNavHost {
                backStack[0] = Destination.Chats
                appNavHost(StubScreens(), backStack, session)

                tapsOpenAChat()
                seesTheConversation("s-1")

                tapsBack()

                seesChats()
                assertEquals(listOf(Destination.Chats), backStack.toList())
            }
        }
}
