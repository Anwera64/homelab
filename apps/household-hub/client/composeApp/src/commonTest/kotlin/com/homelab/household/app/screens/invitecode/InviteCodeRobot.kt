package com.homelab.household.app.screens.invitecode

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.width
import com.homelab.household.app.components.HEARTH_PROGRESS_BAR_TAG
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.a11y_invite_code_checking
import com.homelab.household.app.resources.invite_code_back
import com.homelab.household.app.resources.invite_code_checking
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

    suspend fun seesTheTitle() = seesText(getString(Res.string.invite_code_title))

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

    /** The tapped button says what it is doing, stays enabled, and tells a screen reader what is being checked. */
    suspend fun seesTheButtonCheckingTheCode() {
        val checking = getString(Res.string.invite_code_checking)
        seesText(checking)
        test
            .onNodeWithText(checking)
            .assertIsEnabled()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    getString(Res.string.a11y_invite_code_checking),
                ),
            )
    }

    fun seesTheProgressBar() {
        test.waitUntilExactlyOneExists(hasTestTag(HEARTH_PROGRESS_BAR_TAG), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithTag(HEARTH_PROGRESS_BAR_TAG).assertIsDisplayed()
    }

    suspend fun doesNotSeeContinue() {
        test.onNodeWithText(getString(Res.string.invite_code_continue)).assertDoesNotExist()
    }

    fun screenWidth(): Dp = test.onRoot().getUnclippedBoundsInRoot().width

    suspend fun continueButtonWidth(): Dp {
        val label = getString(Res.string.invite_code_continue)
        seesText(label)
        return test.onNodeWithText(label).getUnclippedBoundsInRoot().width
    }

    private fun seesText(text: String) {
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(text).assertIsDisplayed()
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}

@OptIn(ExperimentalTestApi::class)
suspend fun <T> ComposeUiTest.onInviteCode(block: suspend InviteCodeRobot.() -> T): T = InviteCodeRobot(this).block()

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
