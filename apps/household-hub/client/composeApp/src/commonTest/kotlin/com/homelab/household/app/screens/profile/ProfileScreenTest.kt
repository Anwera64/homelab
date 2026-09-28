package com.homelab.household.app.screens.profile

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.components.SKELETON_GROUP_TAG
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.calendar_apple
import com.homelab.household.app.resources.calendar_disconnect_keep
import com.homelab.household.app.resources.calendar_disconnect_submit
import com.homelab.household.app.resources.calendar_disconnect_title
import com.homelab.household.app.resources.calendar_disconnect_unreachable
import com.homelab.household.app.resources.calendar_sheet_change
import com.homelab.household.app.resources.calendar_sheet_disconnect
import com.homelab.household.app.resources.profile_calendar_connect
import com.homelab.household.app.resources.profile_calendar_options
import com.homelab.household.app.resources.profile_calendar_sign_in_again
import com.homelab.household.app.resources.profile_calendar_sign_in_needed
import com.homelab.household.app.resources.profile_delete_blocked
import com.homelab.household.app.resources.profile_members
import com.homelab.household.app.resources.profile_sign_out
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.testing.TestApp
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.data.datasource.local.InMemorySessionStorage
import com.homelab.household.presentation.profile.ProfileStatus
import com.homelab.household.presentation.profile.ProfileUiState
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Your own account, with the real stack under it; only the hub is faked. */
@OptIn(ExperimentalTestApi::class)
class ProfileScreenTest {
    private val wait = 5_000L

    /** A phone with somebody signed in on it: these screens are all behind a token. */
    private fun signedIn() = InMemorySessionStorage().apply { saveTokens("signed-in-token") }

    @Test
    fun arriving_stands_blocks_where_the_name_goes_and_draws_the_rest_at_once() {
        runComposeUiTest {
            setContent {
                StillTheme {
                    ProfileContent(
                        state = ProfileUiState(member = null, status = ProfileStatus.Loading),
                        onBack = {},
                        onMembers = {},
                        onChangePin = {},
                        onLeave = {},
                        onSignOut = {},
                        onCalendar = {},
                        onCalendarSignInAgain = {},
                    )
                }
            }

            onNodeWithTag(SKELETON_GROUP_TAG).assertIsDisplayed()
            // The settings rows and Sign out are static copy: they need no hub, so they are there.
            onNodeWithText(getString(Res.string.profile_members)).assertIsDisplayed()
            onNodeWithText(getString(Res.string.profile_sign_out)).assertIsDisplayed()
            // Whether this phone is the only admin is not known yet, so the row makes no claim.
            onNodeWithText(getString(Res.string.profile_delete_blocked)).assertDoesNotExist()
        }
    }

    @Test
    fun the_only_admin_is_told_they_cannot_leave() {
        val hub = FakeMembersHub()

        runScreenTest {
            setContent {
                TestApp(hub.engine, sessionStorage = signedIn()) {
                    ProfileScreen(
                        onBack = {},
                        onMembers = {},
                        onChangePin = {},
                        onLeave = {},
                        onSignedOut = {},
                        onCalendar = {},
                        onCalendarSignInAgain = {},
                    )
                }
            }

            waitUntilExactlyOneExists(hasText(getString(Res.string.profile_delete_blocked)), timeoutMillis = wait)
            onNodeWithText("Emma Larsson").assertIsDisplayed()
        }
    }

    @Test
    fun members_is_a_tap_away() {
        val hub = FakeMembersHub()
        var members = 0

        runScreenTest {
            setContent {
                TestApp(hub.engine, sessionStorage = signedIn()) {
                    ProfileScreen(
                        onBack = {},
                        onMembers = { members++ },
                        onChangePin = {},
                        onLeave = {},
                        onSignedOut = {},
                        onCalendar = {},
                        onCalendarSignInAgain = {},
                    )
                }
            }

            waitUntilExactlyOneExists(hasText(getString(Res.string.profile_members)), timeoutMillis = wait)
            onNodeWithText(getString(Res.string.profile_members)).performClick()
            waitUntil(timeoutMillis = wait) { members == 1 }
        }
    }

    @Test
    fun signing_out_forgets_the_token_and_leaves() {
        val hub = FakeMembersHub()
        val tokens = InMemorySessionStorage().apply { saveTokens("signed-in-token") }
        var signedOut = 0

        runScreenTest {
            setContent {
                TestApp(hub.engine, sessionStorage = tokens) {
                    ProfileScreen(
                        onBack = {},
                        onMembers = {},
                        onChangePin = {},
                        onLeave = {},
                        onSignedOut = { signedOut++ },
                        onCalendar = {},
                        onCalendarSignInAgain = {},
                    )
                }
            }

            waitUntilExactlyOneExists(hasText(getString(Res.string.profile_sign_out)), timeoutMillis = wait)
            onNodeWithText(getString(Res.string.profile_sign_out)).performClick()
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
                setContent {
                    StillTheme {
                        ProfileContent(
                            state = state,
                            onBack = {},
                            onMembers = {},
                            onChangePin = {},
                            onLeave = {},
                            onSignOut = {},
                            onCalendar = {},
                            onCalendarSignInAgain = {},
                        )
                    }
                }

                onNodeWithText(getString(Res.string.profile_sign_out)).assertIsDisplayed()
            }
        }
    }

    @Test
    fun `GIVEN a connected calendar WHEN its options are opened THEN it can be changed or disconnected`() {
        // GIVEN
        val hub = FakeMembersHub().apply { hasACalendar() }

        runScreenTest {
            showProfile(hub)
            waitUntilExactlyOneExists(hasText("emma@icloud.com"), timeoutMillis = wait)

            // WHEN
            onNodeWithContentDescription(getString(Res.string.profile_calendar_options)).performClick()

            // THEN
            waitUntilExactlyOneExists(hasText(getString(Res.string.calendar_sheet_change)), timeoutMillis = wait)
            onNodeWithText(getString(Res.string.calendar_sheet_disconnect)).assertIsDisplayed()
        }
    }

    @Test
    fun `GIVEN the calendar options WHEN Change calendar is chosen THEN the picker opens`() {
        // GIVEN
        val hub = FakeMembersHub().apply { hasACalendar() }
        var picker = 0

        runScreenTest {
            showProfile(hub, onCalendar = { picker++ })
            openCalendarOptions()

            // WHEN
            onNodeWithText(getString(Res.string.calendar_sheet_change)).performClick()

            // THEN
            waitUntil(timeoutMillis = wait) { picker == 1 }
        }
    }

    @Test
    fun `GIVEN the calendar options WHEN Disconnect calendar is chosen THEN it asks first`() {
        // GIVEN
        val hub = FakeMembersHub().apply { hasACalendar() }

        runScreenTest {
            showProfile(hub)
            openCalendarOptions()

            // WHEN
            onNodeWithText(getString(Res.string.calendar_sheet_disconnect)).performClick()

            // THEN
            val title = getString(Res.string.calendar_disconnect_title, getString(Res.string.calendar_apple))
            waitUntilExactlyOneExists(hasText(title), timeoutMillis = wait)
            assertEquals(0, hub.calendarsRemoved)
        }
    }

    @Test
    fun `GIVEN it asks to disconnect WHEN the disconnect is confirmed THEN the hub forgets it and Profile offers to connect one`() {
        // GIVEN
        val hub = FakeMembersHub().apply { hasACalendar() }

        runScreenTest {
            showProfile(hub)
            openCalendarOptions()
            onNodeWithText(getString(Res.string.calendar_sheet_disconnect)).performClick()
            waitUntilExactlyOneExists(hasText(getString(Res.string.calendar_disconnect_submit)), timeoutMillis = wait)

            // WHEN
            onNodeWithText(getString(Res.string.calendar_disconnect_submit)).performClick()

            // THEN
            waitUntilExactlyOneExists(hasText(getString(Res.string.profile_calendar_connect)), timeoutMillis = wait)
            assertEquals(1, hub.calendarsRemoved)
        }
    }

    @Test
    fun `GIVEN it asks to disconnect WHEN Keep it connected is chosen THEN nothing is disconnected`() {
        // GIVEN
        val hub = FakeMembersHub().apply { hasACalendar() }

        runScreenTest {
            showProfile(hub)
            openCalendarOptions()
            onNodeWithText(getString(Res.string.calendar_sheet_disconnect)).performClick()
            waitUntilExactlyOneExists(hasText(getString(Res.string.calendar_disconnect_keep)), timeoutMillis = wait)

            // WHEN
            onNodeWithText(getString(Res.string.calendar_disconnect_keep)).performClick()

            // THEN
            waitUntilDoesNotExist(hasText(getString(Res.string.calendar_disconnect_keep)), timeoutMillis = wait)
            onNodeWithText("emma@icloud.com").assertIsDisplayed()
            assertEquals(0, hub.calendarsRemoved)
        }
    }

    @Test
    fun `GIVEN the hub stops answering WHEN the disconnect is confirmed THEN it says so and the calendar stays`() {
        // GIVEN
        val hub = FakeMembersHub().apply { hasACalendar() }

        runScreenTest {
            showProfile(hub)
            openCalendarOptions()
            onNodeWithText(getString(Res.string.calendar_sheet_disconnect)).performClick()
            waitUntilExactlyOneExists(hasText(getString(Res.string.calendar_disconnect_submit)), timeoutMillis = wait)
            hub.isOffline()

            // WHEN
            onNodeWithText(getString(Res.string.calendar_disconnect_submit)).performClick()

            // THEN
            waitUntilExactlyOneExists(
                hasText(getString(Res.string.calendar_disconnect_unreachable)),
                timeoutMillis = wait,
            )
            assertEquals(0, hub.calendarsRemoved)
        }
    }

    @Test
    fun `GIVEN Google stopped accepting the sign-in WHEN the profile is shown THEN the calendar asks to sign in again`() {
        // GIVEN
        val hub = FakeMembersHub().apply { hasAGoogleCalendarNeedingSignInAgain() }
        var signInAgain = 0

        runScreenTest {
            showProfile(hub, onCalendarSignInAgain = { signInAgain++ })
            waitUntilExactlyOneExists(
                hasText(getString(Res.string.profile_calendar_sign_in_needed)),
                timeoutMillis = wait,
            )

            // WHEN
            onNodeWithText(getString(Res.string.profile_calendar_sign_in_again)).performClick()

            // THEN
            waitUntil(timeoutMillis = wait) { signInAgain == 1 }
            onNodeWithText("emma@gmail.com").assertIsDisplayed()
        }
    }

    private fun ComposeUiTest.showProfile(
        hub: FakeMembersHub,
        onCalendar: () -> Unit = {},
        onCalendarSignInAgain: () -> Unit = {},
    ) {
        setContent {
            TestApp(hub.engine, sessionStorage = signedIn()) {
                ProfileScreen(
                    onBack = {},
                    onMembers = {},
                    onChangePin = {},
                    onLeave = {},
                    onSignedOut = {},
                    onCalendar = onCalendar,
                    onCalendarSignInAgain = onCalendarSignInAgain,
                )
            }
        }
    }

    private suspend fun ComposeUiTest.openCalendarOptions() {
        waitUntilExactlyOneExists(hasText("emma@icloud.com"), timeoutMillis = wait)
        onNodeWithContentDescription(getString(Res.string.profile_calendar_options)).performClick()
        waitUntilExactlyOneExists(hasText(getString(Res.string.calendar_sheet_disconnect)), timeoutMillis = wait)
    }

    @Test
    fun no_calendar_offers_to_connect_one() {
        val hub = FakeMembersHub()
        var calendar = 0

        runScreenTest {
            setContent {
                TestApp(hub.engine, sessionStorage = signedIn()) {
                    ProfileScreen(
                        onBack = {},
                        onMembers = {},
                        onChangePin = {},
                        onLeave = {},
                        onSignedOut = {},
                        onCalendar = { calendar++ },
                        onCalendarSignInAgain = {},
                    )
                }
            }

            waitUntilExactlyOneExists(hasText(getString(Res.string.profile_calendar_connect)), timeoutMillis = wait)
            onNodeWithText(getString(Res.string.profile_calendar_connect)).performClick()
            waitUntil(timeoutMillis = wait) { calendar == 1 }
        }
    }
}
