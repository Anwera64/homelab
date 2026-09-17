package com.homelab.household.app.screens.profile

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.profile_delete_blocked
import com.homelab.household.app.resources.profile_members
import com.homelab.household.app.resources.profile_sign_out
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.TestApp
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.data.local.InMemoryTokenStorage
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Your own account, with the real stack under it; only the hub is faked. */
@OptIn(ExperimentalTestApi::class)
class ProfileScreenTest {

    private val wait = 5_000L

    /** A phone with somebody signed in on it: these screens are all behind a token. */
    private fun signedIn() = InMemoryTokenStorage().apply { saveTokens("signed-in-token") }

    @Test
    fun the_only_admin_is_told_they_cannot_leave() {
        val hub = FakeMembersHub()

        runScreenTest {
            setContent {
                TestApp(hub.engine, tokenStorage = signedIn()) {
                    ProfileScreen(onBack = {}, onMembers = {}, onChangePin = {}, onLeave = {}, onSignedOut = {})
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
                TestApp(hub.engine, tokenStorage = signedIn()) {
                    ProfileScreen(onBack = {}, onMembers = { members++ }, onChangePin = {}, onLeave = {}, onSignedOut = {})
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
        val tokens = InMemoryTokenStorage().apply { saveTokens("signed-in-token") }
        var signedOut = 0

        runScreenTest {
            setContent {
                TestApp(hub.engine, tokenStorage = tokens) {
                    ProfileScreen(onBack = {}, onMembers = {}, onChangePin = {}, onLeave = {}, onSignedOut = { signedOut++ })
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
        assertEquals(4, states.size)

        states.forEach { state ->
            runComposeUiTest {
                setContent {
                    HearthTheme(darkTheme = false) {
                        ProfileContent(
                            state = state,
                            onBack = {},
                            onMembers = {},
                            onChangePin = {},
                            onLeave = {},
                            onSignOut = {}
                        )
                    }
                }

                onNodeWithText(getString(Res.string.profile_sign_out)).assertIsDisplayed()
            }
        }
    }
}
