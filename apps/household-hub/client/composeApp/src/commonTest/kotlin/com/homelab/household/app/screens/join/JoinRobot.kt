package com.homelab.household.app.screens.join

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.join_colour_swatch
import com.homelab.household.app.resources.join_expired
import com.homelab.household.app.resources.join_invited_by
import com.homelab.household.app.resources.join_name_label
import com.homelab.household.app.resources.join_name_taken
import com.homelab.household.app.resources.join_pin_label
import com.homelab.household.app.resources.join_pin_not_six_digits
import com.homelab.household.app.resources.join_submit
import com.homelab.household.app.resources.join_title
import com.homelab.household.app.testing.FakeJoinHub
import com.homelab.household.app.testing.TestApp
import com.homelab.household.domain.model.InvitePreview
import org.jetbrains.compose.resources.getString

/** Everything the join screen says and does, named as the resources it reads. */
@OptIn(ExperimentalTestApi::class)
class JoinRobot(private val test: ComposeUiTest) {

    suspend fun seesTheInvitationFrom(inviterName: String) {
        seesText(getString(Res.string.join_invited_by, inviterName))
        seesText(getString(Res.string.join_title))
    }

    suspend fun seesTheNameFromTheInvite(name: String) {
        test.onNodeWithText(name).assertIsDisplayed()
    }

    suspend fun changesTheNameTo(name: String) {
        test.onNodeWithContentDescription(getString(Res.string.join_name_label)).performTextReplacement(name)
    }

    suspend fun typesThePin(pin: String) {
        test.onNodeWithContentDescription(getString(Res.string.join_pin_label)).performTextInput(pin)
    }

    suspend fun picksColour(number: Int, of: Int) {
        test.onNodeWithContentDescription(getString(Res.string.join_colour_swatch, number, of)).performClick()
    }

    suspend fun tapsJoin() {
        test.onNodeWithText(getString(Res.string.join_submit)).performClick()
    }

    suspend fun seesThatTheNameIsTaken() = seesText(getString(Res.string.join_name_taken))

    suspend fun seesThatThePinIsNotSixDigits() = seesText(getString(Res.string.join_pin_not_six_digits))

    suspend fun seesThatTheCodeExpired() = seesText(getString(Res.string.join_expired))

    private fun seesText(text: String) {
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(text).assertIsDisplayed()
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}

@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.onJoin(block: suspend JoinRobot.() -> Unit) {
    JoinRobot(this).block()
}

/** The join screen over a hub that answers however the test says. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.joinScreen(
    hub: FakeJoinHub,
    preview: InvitePreview,
    code: String = "K7M2QP",
    onJoined: () -> Unit = {},
    onExpired: () -> Unit = {}
) {
    setContent {
        TestApp(hub.engine) {
            JoinScreen(preview = preview, code = code, onJoined = onJoined, onExpired = onExpired)
        }
    }
}
