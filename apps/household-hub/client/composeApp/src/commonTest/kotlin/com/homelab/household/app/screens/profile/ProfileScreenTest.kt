package com.homelab.household.app.screens.profile

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.components.SKELETON_GROUP_TAG
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.calendar_apple
import com.homelab.household.app.resources.profile_calendar_change
import com.homelab.household.app.resources.profile_calendar_connect
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
                        )
                    }
                }

                onNodeWithText(getString(Res.string.profile_sign_out)).assertIsDisplayed()
            }
        }
    }

    @Test
    fun a_connected_calendar_shows_its_account_and_offers_to_change_it() {
        val hub = FakeMembersHub()
        hub.hasACalendar()
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
                    )
                }
            }

            waitUntilExactlyOneExists(hasText("emma@icloud.com"), timeoutMillis = wait)
            onNodeWithText(getString(Res.string.calendar_apple)).assertIsDisplayed()
            onNodeWithText(getString(Res.string.profile_calendar_change)).performClick()
            waitUntil(timeoutMillis = wait) { calendar == 1 }
        }
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
                    )
                }
            }

            waitUntilExactlyOneExists(hasText(getString(Res.string.profile_calendar_connect)), timeoutMillis = wait)
            onNodeWithText(getString(Res.string.profile_calendar_connect)).performClick()
            waitUntil(timeoutMillis = wait) { calendar == 1 }
        }
    }
}
