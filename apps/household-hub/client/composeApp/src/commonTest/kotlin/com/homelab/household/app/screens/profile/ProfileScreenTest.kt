package com.homelab.household.app.screens.profile

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.data.datasource.local.InMemorySessionStorage
import com.homelab.household.presentation.profile.ProfileStatus
import com.homelab.household.presentation.profile.ProfileUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Your own account, with the real stack under it; only the hub is faked. */
@OptIn(ExperimentalTestApi::class)
class ProfileScreenTest {
    private val wait = 5_000L
    private val calendarAccount = "emma@icloud.com"

    @Test
    fun `GIVEN a profile that is still loading WHEN it is shown THEN blocks stand where the name goes and the static rows show without any claim about leaving`() {
        runComposeUiTest {
            profileContent(ProfileUiState(member = null, status = ProfileStatus.Loading))

            onProfile {
                seesItLoading()
                // The settings rows and Sign out are static copy: they need no hub, so they are there.
                seesMembers()
                seesSignOut()
                // Whether this phone is the only admin is not known yet, so the row makes no claim.
                doesNotSeeTheOnlyAdminCannotLeave()
            }
        }
    }

    @Test
    fun `GIVEN a member who is the only admin WHEN the profile is shown THEN it says the only admin cannot leave and shows their name`() {
        val hub = FakeMembersHub()

        runScreenTest {
            profileScreen(hub)

            onProfile {
                seesTheOnlyAdminCannotLeave()
                seesMember("Emma Larsson")
            }
        }
    }

    @Test
    fun `GIVEN the profile WHEN members is tapped THEN the screen opens members`() {
        val hub = FakeMembersHub()
        var members = 0

        runScreenTest {
            profileScreen(hub, onMembers = { members++ })

            onProfile { tapsMembers() }
            waitUntil(timeoutMillis = wait) { members == 1 }
        }
    }

    @Test
    fun `GIVEN a signed in member WHEN sign out is tapped THEN the token is forgotten and the screen reports the sign out`() {
        val hub = FakeMembersHub()
        val tokens = InMemorySessionStorage().apply { saveTokens("signed-in-token") }
        var signedOut = 0

        runScreenTest {
            profileScreen(hub, sessionStorage = tokens, onSignedOut = { signedOut++ })

            onProfile { tapsSignOut() }
            waitUntil(timeoutMillis = wait) { signedOut == 1 }
        }

        assertNull(tokens.getAccessToken())
    }

    @Test
    fun `GIVEN every previewed state WHEN it is drawn THEN sign out is there`() {
        val states = ProfileUiStateProvider().values.toList()
        assertEquals(6, states.size)

        states.forEach { state ->
            runComposeUiTest {
                profileContent(state)

                onProfile { seesSignOut() }
            }
        }
    }

    @Test
    fun `GIVEN a connected calendar WHEN its options are opened THEN it can be changed or disconnected`() {
        val hub = FakeMembersHub().apply { hasACalendar() }

        runScreenTest {
            profileScreen(hub)

            onProfile {
                seesCalendar(calendarAccount)

                opensTheCalendarOptions()

                seesItCanBeChangedOrDisconnected()
            }
        }
    }

    @Test
    fun `GIVEN the calendar options WHEN Choose calendars is tapped THEN the list of calendars opens`() {
        val hub = FakeMembersHub().apply { hasACalendar() }
        var choose = 0

        runScreenTest {
            profileScreen(hub, onChooseCalendars = { choose++ })

            onProfile {
                opensTheOptionsOfCalendar(calendarAccount)

                tapsChooseCalendars()
            }

            waitUntil(timeoutMillis = wait) { choose == 1 }
        }
    }

    @Test
    fun `GIVEN the calendar options WHEN Change calendar is chosen THEN the picker opens`() {
        val hub = FakeMembersHub().apply { hasACalendar() }
        var picker = 0

        runScreenTest {
            profileScreen(hub, onCalendar = { picker++ })

            onProfile {
                opensTheOptionsOfCalendar(calendarAccount)

                tapsChangeCalendar()
            }

            waitUntil(timeoutMillis = wait) { picker == 1 }
        }
    }

    @Test
    fun `GIVEN the calendar options WHEN Disconnect calendar is chosen THEN it asks first`() {
        val hub = FakeMembersHub().apply { hasACalendar() }

        runScreenTest {
            profileScreen(hub)

            onProfile {
                opensTheOptionsOfCalendar(calendarAccount)

                tapsDisconnectCalendar()

                seesItAsksToDisconnectAppleCalendar()
            }
            assertEquals(0, hub.calendarsRemoved)
        }
    }

    @Test
    fun `GIVEN it asks to disconnect WHEN the disconnect is confirmed THEN the hub forgets it and Profile offers to connect one`() {
        val hub = FakeMembersHub().apply { hasACalendar() }

        runScreenTest {
            profileScreen(hub)

            onProfile {
                opensTheOptionsOfCalendar(calendarAccount)
                tapsDisconnectCalendar()

                tapsConfirmTheDisconnect()

                seesToConnectACalendar()
            }
            assertEquals(1, hub.calendarsRemoved)
        }
    }

    @Test
    fun `GIVEN it asks to disconnect WHEN Keep it connected is chosen THEN nothing is disconnected`() {
        val hub = FakeMembersHub().apply { hasACalendar() }

        runScreenTest {
            profileScreen(hub)

            onProfile {
                opensTheOptionsOfCalendar(calendarAccount)
                tapsDisconnectCalendar()

                tapsKeepItConnected()

                seesTheDisconnectQuestionGone()
                seesCalendar(calendarAccount)
            }
            assertEquals(0, hub.calendarsRemoved)
        }
    }

    @Test
    fun `GIVEN the hub stops answering WHEN the disconnect is confirmed THEN it says so and the calendar stays`() {
        val hub = FakeMembersHub().apply { hasACalendar() }

        runScreenTest {
            profileScreen(hub)

            onProfile {
                opensTheOptionsOfCalendar(calendarAccount)
                tapsDisconnectCalendar()
                // The confirmation is up before the hub goes quiet.
                seesItAsksToDisconnectAppleCalendar()
                hub.isOffline()

                tapsConfirmTheDisconnect()

                seesTheHubCouldNotBeReached()
            }
            assertEquals(0, hub.calendarsRemoved)
        }
    }

    @Test
    fun `GIVEN Google stopped accepting the sign-in WHEN the profile is shown THEN the calendar asks to sign in again`() {
        val hub = FakeMembersHub().apply { hasAGoogleCalendarNeedingSignInAgain() }
        var signInAgain = 0

        runScreenTest {
            profileScreen(hub, onCalendarSignInAgain = { signInAgain++ })

            onProfile {
                seesTheCalendarNeedsSigningInAgain()

                tapsSignInAgain()
            }

            waitUntil(timeoutMillis = wait) { signInAgain == 1 }
            onProfile { seesCalendar("emma@gmail.com") }
        }
    }

    @Test
    fun `GIVEN memories on the hub WHEN the profile is shown THEN the Memories row counts them and opens the memory page`() {
        val hub =
            FakeMembersHub().apply {
                remembers("p1", "Likes tea")
                remembers("p2", "Studies architecture")
                remembers("h1", "Bins go out on Wednesday", household = true, mine = false)
            }
        var opened = 0

        runScreenTest {
            profileScreen(hub, onMemories = { opened++ })

            onProfile {
                seesTheMemoryHeading()
                seesMemoryCounts(yours = 2, household = 1)

                tapsMemories()
            }

            waitUntil(timeoutMillis = wait) { opened == 1 }
        }
    }

    @Test
    fun `GIVEN a change to check WHEN the profile is shown THEN the Memories row says 1 to check`() {
        val hub =
            FakeMembersHub().apply {
                remembers("p1", "Likes tea")
                readsAChange("c1", stored = "Likes tea", read = "Prefers coffee now")
            }

        runScreenTest {
            profileScreen(hub)

            onProfile { seesToCheck(count = 1) }
        }
    }

    @Test
    fun `GIVEN agents add events without asking WHEN the profile is shown THEN the agents row counts it and opens When agents act`() {
        val hub = FakeMembersHub().apply { addsEventsAutomatically() }
        var opened = 0

        runScreenTest {
            profileScreen(hub, onWhenAgentsAct = { opened++ })

            onProfile {
                seesAgentsAddingWithoutAsking(count = 1)
                seesTheAgentsHeading()

                tapsWhenAgentsAct()
            }

            waitUntil(timeoutMillis = wait) { opened == 1 }
        }
    }

    @Test
    fun `GIVEN nothing runs without asking WHEN the profile is shown THEN the agents row says every change asks`() {
        val hub = FakeMembersHub()

        runScreenTest {
            profileScreen(hub)

            onProfile { seesEveryChangeAsks() }
        }
    }

    @Test
    fun `GIVEN a member with no calendar WHEN connect a calendar is tapped THEN the screen opens the calendar picker`() {
        val hub = FakeMembersHub()
        var calendar = 0

        runScreenTest {
            profileScreen(hub, onCalendar = { calendar++ })

            onProfile { tapsConnectACalendar() }
            waitUntil(timeoutMillis = wait) { calendar == 1 }
        }
    }
}
