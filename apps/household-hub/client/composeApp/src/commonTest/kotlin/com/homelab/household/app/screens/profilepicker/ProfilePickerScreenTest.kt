package com.homelab.household.app.screens.profilepicker

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.picker_title
import com.homelab.household.app.testing.FakeSignInHub
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.domain.model.Member
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals

/** "Who's here?" with the real stack under it; only the hub is faked. */
@OptIn(ExperimentalTestApi::class)
class ProfilePickerScreenTest {

    private val emma = Member(id = "emma", name = "Emma", avatarColor = "#3C6E4E")
    private val liam = Member(id = "liam", name = "Liam", avatarColor = "#C05638")

    @Test
    fun it_shows_everyone_who_lives_on_the_hub() {
        val hub = FakeSignInHub()
        hub.lists(emma, liam)

        runScreenTest {
            profilePickerScreen(hub)

            onProfilePicker { seesTheHousehold("Emma", "Liam") }
        }
    }

    @Test
    fun tapping_a_face_opens_that_members_pin() {
        val hub = FakeSignInHub()
        hub.lists(emma, liam)
        val picked = mutableListOf<Member>()

        runScreenTest {
            profilePickerScreen(hub, onMemberSelected = { picked += it })

            onProfilePicker {
                seesTheHousehold("Emma", "Liam")
                tapsTheFaceOf("Liam")
            }
            waitUntil(timeoutMillis = WAIT_MILLIS) { picked.isNotEmpty() }
        }

        assertEquals(listOf(liam), picked)
    }

    @Test
    fun an_unreachable_hub_says_so_and_asks_again_on_try_again() {
        val hub = FakeSignInHub()
        hub.isOffline()

        runScreenTest {
            profilePickerScreen(hub)

            onProfilePicker { seesTheHubDidNotAnswer() }

            hub.lists(emma)
            onProfilePicker {
                tapsTryAgain()
                seesTheHousehold("Emma")
            }
        }
    }

    @Test
    fun someone_who_is_not_on_the_picker_can_say_they_have_an_invite() {
        val hub = FakeSignInHub()
        hub.lists(emma)
        var invite = 0

        runScreenTest {
            profilePickerScreen(hub, onInviteCode = { invite++ })

            onProfilePicker { tapsInviteCode() }
            waitUntil(timeoutMillis = 5_000L) { invite == 1 }
        }
    }

    @Test
    fun every_previewed_state_draws() {
        val states = ProfilePickerUiStateProvider().values.toList()
        assertEquals(4, states.size)

        states.forEach { state ->
            runComposeUiTest {
                setContent {
                    HearthTheme(darkTheme = false) {
                        ProfilePickerContent(state = state, onMemberSelected = {}, onRetry = {}, onInviteCode = {})
                    }
                }

                onNodeWithText(getString(Res.string.picker_title)).assertIsDisplayed()
            }
        }
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
