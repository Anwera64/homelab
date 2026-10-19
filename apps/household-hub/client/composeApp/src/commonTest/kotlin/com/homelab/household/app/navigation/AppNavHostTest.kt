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
    fun `GIVEN a phone on home WHEN the hub signs it out THEN it starts over at who is here`() =
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
    fun `GIVEN a phone on home WHEN the app opens THEN the session is renewed once`() =
        runComposeUiTest {
            onAppNavHost {
                backStack[0] = Destination.Home
                appNavHost(StubScreens(), backStack, session)

                waitForIdle()
                assertEquals(1, session.renewals)
            }
        }

    @Test
    fun `GIVEN a fresh back stack WHEN the app opens THEN it shows launch`() =
        runComposeUiTest {
            onAppNavHost {
                appNavHost(StubScreens(), backStack, session)

                seesLaunch()
            }
        }

    @Test
    fun `GIVEN the launch screen WHEN go to sign in is tapped THEN sign in replaces launch on the stack`() =
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
    fun `GIVEN the launch screen WHEN go to first run is tapped THEN first run replaces launch on the stack`() =
        runComposeUiTest {
            onAppNavHost {
                appNavHost(StubScreens(), backStack, session)

                tapsGoToFirstRun()

                seesFirstRun()
                assertEquals(listOf(Destination.FirstRun), backStack.toList())
            }
        }

    @Test
    fun `GIVEN the first run screen WHEN the household is created THEN home replaces first run on the stack`() =
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
    fun `GIVEN the first run screen on a hub already set up WHEN sign in instead is tapped THEN sign in replaces first run on the stack`() =
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
    fun `GIVEN the profile picker WHEN a member's face is tapped THEN that member's PIN opens over the picker`() =
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
                val screens = StubScreens()
                backStack[0] = Destination.Conversation()
                appNavHost(screens, backStack, session)

                tapsReconnectCalendar()
                seesTheCalendarPicker()
                tapsBack()

                seesANewConversation()
                val kept = screens.viewModels.single()
                assertFalse(kept.cleared)
            }
        }

    @Test
    fun `GIVEN a conversation that opened the connect flow WHEN a calendar connects THEN the conversation hears it once`() =
        runComposeUiTest {
            onAppNavHost {
                val screens = StubScreens()
                backStack[0] = Destination.Conversation("s-1")
                appNavHost(screens, backStack, session)

                tapsReconnectCalendar()
                tapsPickApple()
                tapsCalendarConnected()
                waitForIdle()

                seesTheConversation("s-1")
                assertEquals(1, screens.calendarsConnected)
            }
        }

    @Test
    fun `GIVEN a conversation that opened the connect flow WHEN it is left with Back THEN the conversation hears nothing`() =
        runComposeUiTest {
            onAppNavHost {
                val screens = StubScreens()
                backStack[0] = Destination.Conversation("s-1")
                appNavHost(screens, backStack, session)

                tapsReconnectCalendar()
                tapsBack()
                waitForIdle()

                assertEquals(0, screens.calendarsConnected)
            }
        }

    @Test
    fun `GIVEN a PIN pad closed with Back WHEN it is opened again THEN it has a new ViewModel`() =
        runComposeUiTest {
            onAppNavHost {
                val screens = StubScreens()
                backStack[0] = Destination.SignIn
                appNavHost(screens, backStack, session)
                tapsPickEmma()
                tapsBack()
                waitForIdle()

                tapsPickEmma()
                waitForIdle()

                assertEquals(2, screens.viewModels.size)
                assertTrue(screens.viewModels.first().cleared)
                assertFalse(screens.viewModels.last().cleared)
            }
        }

    @Test
    fun `GIVEN a member's PIN over the picker WHEN back is tapped THEN the picker is shown again`() =
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
    fun `GIVEN a member's PIN over the picker WHEN the right PIN is entered THEN home is the only screen left on the stack`() =
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
    fun `GIVEN the profile picker WHEN an invite code is accepted and the join is done THEN home is the only screen left on the stack`() =
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
    fun `GIVEN an accepted invite code WHEN the code turns out to have expired THEN the invite code screen is shown again`() =
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
    fun `GIVEN a member's PIN over the picker WHEN the PIN is forgotten and a reset code is typed and a new PIN is set THEN home is the only screen left on the stack`() =
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
    fun `GIVEN home WHEN the profile and then members are opened THEN members is shown over the profile over home`() =
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
    fun `GIVEN the profile WHEN the memory page is opened and back is tapped THEN the profile is shown again`() =
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
    fun `GIVEN a conversation WHEN all memories are opened and back is tapped THEN the conversation is the only screen left on the stack`() =
        runComposeUiTest {
            onAppNavHost {
                backStack[0] = Destination.Conversation("s-1")
                appNavHost(StubScreens(), backStack, session)

                tapsGoToAllMemories()
                seesMemories()
                assertEquals(listOf(Destination.Conversation("s-1"), Destination.Memories), backStack.toList())

                tapsBack()

                assertEquals(listOf(Destination.Conversation("s-1")), backStack.toList())
            }
        }

    @Test
    fun `GIVEN the memory page WHEN changes to check are opened and back is tapped THEN the memory page is shown again`() =
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
    fun `GIVEN the profile WHEN when agents act is opened and back is tapped THEN the profile is shown again`() =
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
    fun `GIVEN the members screen WHEN inviting then approving a reset then removing a member are opened and done THEN members is the only screen left on the stack`() =
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
    fun `GIVEN the profile WHEN leaving the household is done THEN sign in is the only screen left on the stack`() =
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
    fun `GIVEN the profile WHEN the member signs out THEN sign in is the only screen left on the stack`() =
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
    fun `GIVEN the profile WHEN the PIN is changed THEN the profile is the only screen left on the stack`() =
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
    fun `GIVEN the profile WHEN an Apple calendar is picked and connected THEN the profile is the only screen left on the stack`() =
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
                backStack[0] = Destination.Profile
                appNavHost(StubScreens(), backStack, session)
                tapsGoToCalendar()

                tapsPickGoogle()
                seesTheGoogleSignIn()
                tapsCalendarConnected()

                seesProfile()
                assertEquals(listOf(Destination.Profile), backStack.toList())
            }
        }

    @Test
    fun `GIVEN a Google calendar that needs signing in again WHEN the profile asks for it THEN the sign-in opens straight away`() =
        runComposeUiTest {
            onAppNavHost {
                backStack[0] = Destination.Profile
                appNavHost(StubScreens(), backStack, session)

                tapsSignInToGoogleAgain()

                seesTheGoogleSignIn()
                assertEquals(listOf(Destination.Profile, Destination.GoogleCalendarSignIn), backStack.toList())
            }
        }

    @Test
    fun `GIVEN a failed calendar step WHEN Reconnect calendar is tapped and a calendar connected THEN the conversation is back`() =
        runComposeUiTest {
            onAppNavHost {
                backStack[0] = Destination.Conversation("s-1")
                appNavHost(StubScreens(), backStack, session)

                tapsReconnectCalendar()
                seesTheCalendarPicker()
                tapsPickApple()
                tapsCalendarConnected()

                seesTheConversation("s-1")
                assertEquals(listOf(Destination.Conversation("s-1")), backStack.toList())
            }
        }

    @Test
    fun `GIVEN an Apple calendar's details opened from the picker WHEN back is tapped THEN the picker is shown again`() =
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
    fun `GIVEN home WHEN each tab is tapped in turn THEN each shows its own screen`() =
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
    fun `GIVEN home WHEN two tabs are tapped in turn THEN only the last tab's screen is on the stack`() =
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
    fun `GIVEN home WHEN the raised plus is tapped THEN a new conversation opens over home`() =
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
    fun `GIVEN the chats list WHEN a chat is opened and back is tapped THEN the chats list is the only screen left on the stack`() =
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
