package com.homelab.household.app.screens.profilepicker

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.testing.FakeSignInHub
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.domain.model.Member
import kotlin.test.Test
import kotlin.test.assertEquals

/** "Who's here?" with the real stack under it; only the hub is faked. */
@OptIn(ExperimentalTestApi::class)
class ProfilePickerScreenTest {
    private val emma = Member(id = "emma", name = "Emma", avatarColor = "#3C6E4E")
    private val liam = Member(id = "liam", name = "Liam", avatarColor = "#C05638")

    @Test
    fun `GIVEN a hub that lists Emma and Liam WHEN the picker is shown THEN both are there`() {
        val hub = FakeSignInHub()
        hub.lists(emma, liam)

        runScreenTest {
            profilePickerScreen(hub)

            onProfilePicker { seesTheHousehold("Emma", "Liam") }
        }
    }

    @Test
    fun `GIVEN a hub that lists Emma and Liam WHEN Liam's face is tapped THEN Liam is picked`() {
        val hub = FakeSignInHub()
        hub.lists(emma, liam)
        val picked = mutableListOf<Member>()

        runScreenTest {
            profilePickerScreen(hub, onSelectMember = { picked += it })

            onProfilePicker {
                seesTheHousehold("Emma", "Liam")
                tapsTheFaceOf("Liam")
            }
            waitUntil(timeoutMillis = WAIT_MILLIS) { picked.isNotEmpty() }
        }

        assertEquals(listOf(liam), picked)
    }

    @Test
    fun `GIVEN an unreachable hub that then lists Emma WHEN try again is tapped THEN the household shows`() {
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
    fun `GIVEN the picker WHEN invite code is tapped THEN the screen opens the invite code entry`() {
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
    fun `GIVEN every previewed state WHEN it is drawn THEN the title is there`() {
        val states = ProfilePickerUiStateProvider().values.toList()
        assertEquals(4, states.size)

        states.forEach { state ->
            runComposeUiTest {
                setContent {
                    StillTheme {
                        ProfilePickerContent(state = state, onSelectMember = {}, onRetry = {}, onInviteCode = {})
                    }
                }

                onProfilePicker { seesTheTitle() }
            }
        }
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
