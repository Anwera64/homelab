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
    fun arriving_stands_blocks_where_the_name_goes_and_draws_the_rest_at_once() {
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
    fun the_only_admin_is_told_they_cannot_leave() {
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
    fun members_is_a_tap_away() {
        val hub = FakeMembersHub()
        var members = 0

        runScreenTest {
            profileScreen(hub, onMembers = { members++ })

            onProfile { tapsMembers() }
            waitUntil(timeoutMillis = wait) { members == 1 }
        }
    }

    @Test
    fun signing_out_forgets_the_token_and_leaves() {
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
    fun every_previewed_state_draws() {
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
        // GIVEN
        val hub = FakeMembersHub().apply { hasACalendar() }

        runScreenTest {
            profileScreen(hub)

            onProfile {
                seesCalendar(calendarAccount)

                // WHEN
                opensTheCalendarOptions()

                // THEN
                seesItCanBeChangedOrDisconnected()
            }
        }
    }

    @Test
    fun `GIVEN the calendar options WHEN Change calendar is chosen THEN the picker opens`() {
        // GIVEN
        val hub = FakeMembersHub().apply { hasACalendar() }
        var picker = 0

        runScreenTest {
            profileScreen(hub, onCalendar = { picker++ })

            onProfile {
                opensTheOptionsOfCalendar(calendarAccount)

                // WHEN
                tapsChangeCalendar()
            }

            // THEN
            waitUntil(timeoutMillis = wait) { picker == 1 }
        }
    }

    @Test
    fun `GIVEN the calendar options WHEN Disconnect calendar is chosen THEN it asks first`() {
        // GIVEN
        val hub = FakeMembersHub().apply { hasACalendar() }

        runScreenTest {
            profileScreen(hub)

            onProfile {
                opensTheOptionsOfCalendar(calendarAccount)

                // WHEN
                tapsDisconnectCalendar()

                // THEN
                seesItAsksToDisconnectAppleCalendar()
            }
            assertEquals(0, hub.calendarsRemoved)
        }
    }

    @Test
    fun `GIVEN it asks to disconnect WHEN the disconnect is confirmed THEN the hub forgets it and Profile offers to connect one`() {
        // GIVEN
        val hub = FakeMembersHub().apply { hasACalendar() }

        runScreenTest {
            profileScreen(hub)

            onProfile {
                opensTheOptionsOfCalendar(calendarAccount)
                tapsDisconnectCalendar()

                // WHEN
                tapsConfirmTheDisconnect()

                // THEN
                seesToConnectACalendar()
            }
            assertEquals(1, hub.calendarsRemoved)
        }
    }

    @Test
    fun `GIVEN it asks to disconnect WHEN Keep it connected is chosen THEN nothing is disconnected`() {
        // GIVEN
        val hub = FakeMembersHub().apply { hasACalendar() }

        runScreenTest {
            profileScreen(hub)

            onProfile {
                opensTheOptionsOfCalendar(calendarAccount)
                tapsDisconnectCalendar()

                // WHEN
                tapsKeepItConnected()

                // THEN
                seesTheDisconnectQuestionGone()
                seesCalendar(calendarAccount)
            }
            assertEquals(0, hub.calendarsRemoved)
        }
    }

    @Test
    fun `GIVEN the hub stops answering WHEN the disconnect is confirmed THEN it says so and the calendar stays`() {
        // GIVEN
        val hub = FakeMembersHub().apply { hasACalendar() }

        runScreenTest {
            profileScreen(hub)

            onProfile {
                opensTheOptionsOfCalendar(calendarAccount)
                tapsDisconnectCalendar()
                // The confirmation is up before the hub goes quiet.
                seesItAsksToDisconnectAppleCalendar()
                hub.isOffline()

                // WHEN
                tapsConfirmTheDisconnect()

                // THEN
                seesTheHubCouldNotBeReached()
            }
            assertEquals(0, hub.calendarsRemoved)
        }
    }

    @Test
    fun `GIVEN Google stopped accepting the sign-in WHEN the profile is shown THEN the calendar asks to sign in again`() {
        // GIVEN
        val hub = FakeMembersHub().apply { hasAGoogleCalendarNeedingSignInAgain() }
        var signInAgain = 0

        runScreenTest {
            profileScreen(hub, onCalendarSignInAgain = { signInAgain++ })

            onProfile {
                seesTheCalendarNeedsSigningInAgain()

                // WHEN
                tapsSignInAgain()
            }

            // THEN
            waitUntil(timeoutMillis = wait) { signInAgain == 1 }
            onProfile { seesCalendar("emma@gmail.com") }
        }
    }

    @Test
    fun `GIVEN memories on the hub WHEN the profile is shown THEN the Memories row counts them and opens the memory page`() {
        // GIVEN
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

                // WHEN
                tapsMemories()
            }

            // THEN
            waitUntil(timeoutMillis = wait) { opened == 1 }
        }
    }

    @Test
    fun `GIVEN agents add events without asking WHEN the profile is shown THEN the agents row counts it and opens When agents act`() {
        // GIVEN
        val hub = FakeMembersHub().apply { addsEventsAutomatically() }
        var opened = 0

        runScreenTest {
            profileScreen(hub, onWhenAgentsAct = { opened++ })

            onProfile {
                seesAgentsAddingWithoutAsking(count = 1)
                seesTheAgentsHeading()

                // WHEN
                tapsWhenAgentsAct()
            }

            // THEN
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
    fun no_calendar_offers_to_connect_one() {
        val hub = FakeMembersHub()
        var calendar = 0

        runScreenTest {
            profileScreen(hub, onCalendar = { calendar++ })

            onProfile { tapsConnectACalendar() }
            waitUntil(timeoutMillis = wait) { calendar == 1 }
        }
    }
}
