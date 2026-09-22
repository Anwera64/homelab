package com.homelab.household.app.screens.invitecode

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.invite_code_back
import com.homelab.household.app.resources.invite_code_continue
import com.homelab.household.app.resources.invite_code_detail
import com.homelab.household.app.resources.invite_code_field
import com.homelab.household.app.resources.invite_code_incomplete
import com.homelab.household.app.resources.invite_code_invalid
import com.homelab.household.app.resources.invite_code_locked
import com.homelab.household.app.resources.invite_code_note
import com.homelab.household.app.resources.invite_code_title
import com.homelab.household.app.resources.invite_code_unreachable
import com.homelab.household.app.testing.FakeJoinHub
import com.homelab.household.app.testing.TestApp
import com.homelab.household.domain.model.InvitePreview
import org.jetbrains.compose.resources.getString

/** Everything the invite-code screen says and does, named as the resources it reads. */
@OptIn(ExperimentalTestApi::class)
class InviteCodeRobot(
    private val test: ComposeUiTest,
) {
    suspend fun seesTheCodeScreen() {
        seesText(getString(Res.string.invite_code_title))
        seesText(getString(Res.string.invite_code_detail))
        seesText(getString(Res.string.invite_code_note))
    }

    suspend fun types(code: String) {
        test.onNodeWithContentDescription(getString(Res.string.invite_code_field)).performTextInput(code)
    }

    suspend fun tapsContinue() {
        test.onNodeWithText(getString(Res.string.invite_code_continue)).performClick()
    }

    suspend fun tapsBack() {
        test.onNodeWithContentDescription(getString(Res.string.invite_code_back)).performClick()
    }

    suspend fun seesThatTheCodeIsShort() = seesText(getString(Res.string.invite_code_incomplete))

    suspend fun seesThatTheCodeIsNotValid() = seesText(getString(Res.string.invite_code_invalid))

    suspend fun seesTheWaitOf(seconds: Int) = seesText(getString(Res.string.invite_code_locked, seconds))

    suspend fun seesTheHubDidNotAnswer() = seesText(getString(Res.string.invite_code_unreachable))

    private fun seesText(text: String) {
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(text).assertIsDisplayed()
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}

@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.onInviteCode(block: suspend InviteCodeRobot.() -> Unit) {
    InviteCodeRobot(this).block()
}

/** The invite-code screen over a hub that answers however the test says. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.inviteCodeScreen(
    hub: FakeJoinHub,
    onBack: () -> Unit = {},
    onInvite: (InvitePreview, String) -> Unit = { _, _ -> },
) {
    setContent {
        TestApp(hub.engine) {
            InviteCodeScreen(onBack = onBack, onInvite = onInvite)
        }
    }
}
