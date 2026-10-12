package com.homelab.household.app.screens.invitecreate

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.components.HEARTH_PROGRESS_BAR_TAG
import com.homelab.household.app.components.SKELETON_GROUP_TAG
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.a11y_invite_create_making
import com.homelab.household.app.resources.a11y_invite_create_remaking
import com.homelab.household.app.resources.invite_create_making
import com.homelab.household.app.resources.invite_create_making_new
import com.homelab.household.app.resources.invite_create_name_label
import com.homelab.household.app.resources.invite_create_name_missing
import com.homelab.household.app.resources.invite_create_new_code
import com.homelab.household.app.resources.invite_create_title
import com.homelab.household.app.resources.join_name_taken
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.testing.TestApp
import com.homelab.household.data.datasource.local.InMemorySessionStorage
import com.homelab.household.presentation.invitecreate.InviteCreateUiState
import org.jetbrains.compose.resources.getString

/** Everything the invite-code screen says and does, named as the resources it reads. */
@OptIn(ExperimentalTestApi::class)
class InviteCreateRobot(
    private val test: ComposeUiTest,
) {
    suspend fun seesTheTitle() = seesText(getString(Res.string.invite_create_title))

    suspend fun typesTheName(name: String) {
        test.onNodeWithContentDescription(getString(Res.string.invite_create_name_label)).performTextInput(name)
    }

    suspend fun tapsNewCode() {
        test.onNodeWithText(getString(Res.string.invite_create_new_code)).performClick()
    }

    /** The code to read out, as the hub made it. */
    fun seesTheCode(code: String) = seesText(code)

    /** The old code must go, rather than invite a read-out of characters that are being replaced. */
    fun doesNotSeeTheCode(code: String) {
        test.onNodeWithText(code).assertDoesNotExist()
    }

    suspend fun seesTheNameIsMissing() = seesText(getString(Res.string.invite_create_name_missing))

    suspend fun seesTheNameIsTaken() = seesText(getString(Res.string.join_name_taken))

    /** The first code is being made: the button says so, stays enabled, and announces what is happening. */
    suspend fun seesTheButtonMakingTheFirstCode() {
        test.onNodeWithText(getString(Res.string.invite_create_making))
            .assertIsEnabled()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    getString(Res.string.a11y_invite_create_making),
                ),
            )
        test.onNodeWithTag(HEARTH_PROGRESS_BAR_TAG).assertIsDisplayed()
    }

    suspend fun doesNotSeeNewCode() {
        test.onNodeWithText(getString(Res.string.invite_create_new_code)).assertDoesNotExist()
    }

    /** A second code is being made: the button announces that it is a new one. */
    suspend fun seesTheButtonMakingANewCode() {
        test.onNodeWithText(getString(Res.string.invite_create_making)).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                getString(Res.string.a11y_invite_create_remaking),
            ),
        )
    }

    fun seesTheCodePlaceholder() {
        test.onNodeWithTag(SKELETON_GROUP_TAG).assertIsDisplayed()
    }

    suspend fun seesTheNewCodeIsComing() {
        test.onNodeWithText(getString(Res.string.invite_create_making_new)).assertIsDisplayed()
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
suspend fun ComposeUiTest.onInviteCreate(block: suspend InviteCreateRobot.() -> Unit) {
    InviteCreateRobot(this).block()
}

/** The invite screen, signed in, over a hub that answers however the test says. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.inviteCreateScreen(hub: FakeMembersHub) {
    setContent {
        TestApp(hub.engine, sessionStorage = InMemorySessionStorage().apply { saveTokens("signed-in-token") }) {
            InviteCreateScreen(onBack = {})
        }
    }
}

/** The invite screen's content drawn for [state]. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.inviteCreateContent(state: InviteCreateUiState) {
    setContent {
        StillTheme {
            InviteCreateContent(
                state = state,
                onNameChange = {},
                onAdminChange = {},
                onCreate = {},
                onNewCode = {},
                onCopy = {},
                onBack = {},
            )
        }
    }
}
