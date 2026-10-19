package com.homelab.household.app.screens.join

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.testing.FakeJoinHub
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.domain.model.InvitePreview
import com.homelab.household.presentation.firstrun.AvatarPalette
import com.homelab.household.presentation.join.JoinStatus
import com.homelab.household.presentation.join.JoinUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Joining the household, with the real stack under it; only the hub is faked. */
@OptIn(ExperimentalTestApi::class)
class JoinScreenTest {
    private val invite = InvitePreview(invitedName = "Liam", inviterName = "Emma", inviterAvatarColor = "#3C6E4E")

    @Test
    fun `GIVEN an invitation from Emma to Liam WHEN a PIN is typed and join is tapped THEN the invited name and the PIN are sent and the screen hands back`() {
        val hub = FakeJoinHub()
        hub.letsThemJoinAs(memberId = "liam", fullName = "Liam")
        var joined = 0

        runScreenTest {
            joinScreen(hub, invite, onJoin = { joined++ })

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
    fun `GIVEN a PIN of three digits WHEN join is tapped THEN the screen says the PIN is not six digits and nothing is sent`() {
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
    fun `GIVEN a hub that says the name is taken WHEN a PIN is typed and join is tapped THEN the screen says the name is taken`() {
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
    fun `GIVEN a hub that says the code has gone WHEN a PIN is typed and join is tapped THEN the screen says the code expired`() {
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

    /**
     * Joining sends one request and says so on the button that sent it.
     * Nothing is dimmed: the button keeps its colour, stays enabled, and a screen reader hears what
     * is happening rather than "Working" (design notes §2, §6.21).
     */
    @Test
    fun `GIVEN a join in progress WHEN the screen is shown THEN the button says it is joining with a progress bar and join is gone`() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    JoinContent(
                        state =
                            JoinUiState(
                                preview =
                                    InvitePreview(
                                        invitedName = "Liam",
                                        inviterName = "Emma",
                                        inviterAvatarColor = "#3C6E4E",
                                    ),
                                colour = AvatarPalette.swatches.first(),
                                pin = "975310",
                                status = JoinStatus.Joining,
                            ),
                        onNameChange = {},
                        onPinChange = {},
                        onColourSelect = {},
                        onJoin = {},
                    )
                }
            }

            onJoin {
                seesTheButtonJoining()
                seesTheProgressBar()
                doesNotSeeJoin()
            }
        }

    @Test
    fun `GIVEN every previewed state WHEN it is drawn THEN the title is there`() {
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
                            onJoin = {},
                        )
                    }
                }

                onJoin { seesTheTitle() }
            }
        }
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
