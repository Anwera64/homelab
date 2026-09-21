package com.homelab.household.app.screens.join

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.join_title
import com.homelab.household.app.testing.FakeJoinHub
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.domain.model.InvitePreview
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Joining the household, with the real stack under it; only the hub is faked. */
@OptIn(ExperimentalTestApi::class)
class JoinScreenTest {

    private val invite = InvitePreview(invitedName = "Liam", inviterName = "Emma", inviterAvatarColor = "#3C6E4E")

    @Test
    fun the_joiner_keeps_the_name_they_were_invited_as_and_picks_a_pin() {
        val hub = FakeJoinHub()
        hub.letsThemJoinAs(memberId = "liam", fullName = "Liam")
        var joined = 0

        runScreenTest {
            joinScreen(hub, invite, onJoined = { joined++ })

            onJoin {
                seesTheInvitationFrom("Emma")
                seesTheNameFromTheInvite("Liam")
                typesThePin("975310")
                tapsJoin()
            }
            waitUntil(timeoutMillis = WAIT_MILLIS) { joined == 1 }
        }

        val sent = hub.joins.single()
        assertTrue(sent.contains(""""full_name":"Liam""""), sent)
        assertTrue(sent.contains(""""pin":"975310""""), sent)
    }

    @Test
    fun a_pin_that_is_not_six_digits_says_so_under_the_field() {
        val hub = FakeJoinHub()

        runScreenTest {
            joinScreen(hub, invite)

            onJoin {
                typesThePin("975")
                tapsJoin()
                seesThatThePinIsNotSixDigits()
            }
        }

        assertEquals(0, hub.joins.size)
    }

    @Test
    fun a_name_someone_took_meanwhile_says_so_under_the_name() {
        val hub = FakeJoinHub()
        hub.saysThatNameIsTaken()

        runScreenTest {
            joinScreen(hub, invite)

            onJoin {
                typesThePin("975310")
                tapsJoin()
                seesThatTheNameIsTaken()
            }
        }
    }

    @Test
    fun a_code_that_expired_while_they_typed_says_so() {
        val hub = FakeJoinHub()
        hub.saysTheCodeHasGone()

        runScreenTest {
            joinScreen(hub, invite)

            onJoin {
                typesThePin("975310")
                tapsJoin()
                seesThatTheCodeExpired()
            }
        }
    }

    @Test
    fun every_previewed_state_draws() {
        val states = JoinUiStateProvider().values.toList()

        states.forEach { state ->
            runComposeUiTest {
                setContent {
                    StillTheme {
                        JoinContent(
                            state = state,
                            onNameChange = {},
                            onPinChange = {},
                            onColourSelect = {},
                            onJoin = {}
                        )
                    }
                }

                onNodeWithText(getString(Res.string.join_title)).assertIsDisplayed()
            }
        }
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
