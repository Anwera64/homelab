package com.homelab.household.app.screens.pinforgot

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.domain.model.Member
import com.homelab.household.presentation.pinforgot.PinForgotStatus
import com.homelab.household.presentation.pinforgot.PinForgotUiState
import kotlin.test.Test

/** A forgotten PIN, with the real stack under it; only the hub is faked. */
@OptIn(ExperimentalTestApi::class)
class PinForgotScreenTest {
    private val emma = Member(id = "emma", name = "Emma Larsson", avatarColor = "#3C6E4E")
    private val wait = 5_000L

    @Test
    fun `GIVEN a household with another member WHEN the forgotten PIN screen is shown THEN it names the housemate to ask and shows the fallback`() {
        val hub = FakeMembersHub()

        runScreenTest {
            pinForgotScreen(hub, emma)

            onPinForgot {
                seesToAsk("Liam")
                seesTheFallback()
            }
        }
    }

    @Test
    fun `GIVEN the forgotten PIN screen WHEN have a code is tapped THEN the screen goes straight on`() {
        val hub = FakeMembersHub()
        var code = 0

        runScreenTest {
            pinForgotScreen(hub, emma, onHaveCode = { code++ })

            onPinForgot { tapsHaveACode() }
            waitUntil(timeoutMillis = wait) { code == 1 }
        }
    }

    @Test
    fun `GIVEN a forgotten PIN screen that is still loading WHEN it is shown THEN it shows loading blocks and keeps the fallback`() {
        runComposeUiTest {
            setContent {
                StillTheme {
                    PinForgotContent(
                        state = PinForgotUiState(member = emma, others = emptyList(), status = PinForgotStatus.Loading),
                        onHaveCode = {},
                        onBack = {},
                    )
                }
            }

            onPinForgot {
                seesItLoading()
                seesTheFallback()
            }
        }
    }

    @Test
    fun `GIVEN every previewed state WHEN it is drawn THEN the title is there`() {
        val states = PinForgotUiStateProvider().values.toList()

        states.forEach { state ->
            runComposeUiTest {
                setContent {
                    StillTheme {
                        PinForgotContent(state = state, onHaveCode = {}, onBack = {})
                    }
                }

                onPinForgot { seesTheTitle() }
            }
        }
    }
}
