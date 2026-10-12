package com.homelab.household.app.screens.removemember

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
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.a11y_remove_removing
import com.homelab.household.app.resources.remove_confirm_label
import com.homelab.household.app.resources.remove_confirm_mismatch
import com.homelab.household.app.resources.remove_removing
import com.homelab.household.app.resources.remove_submit
import com.homelab.household.app.resources.remove_title
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.testing.TestApp
import com.homelab.household.data.datasource.local.InMemorySessionStorage
import com.homelab.household.domain.model.Member
import com.homelab.household.presentation.removemember.RemoveMemberUiState
import org.jetbrains.compose.resources.getString

/** Everything the remove-a-member screen says and does, named as the resources it reads. */
@OptIn(ExperimentalTestApi::class)
class RemoveMemberRobot(
    private val test: ComposeUiTest,
) {
    /** The title names the member being removed. */
    suspend fun seesTheTitleFor(name: String) = seesText(getString(Res.string.remove_title, name))

    suspend fun typesTheName(
        member: String,
        typed: String,
    ) {
        test.onNodeWithContentDescription(getString(Res.string.remove_confirm_label, member)).performTextInput(typed)
    }

    suspend fun tapsRemove(member: String) {
        test.onNodeWithText(getString(Res.string.remove_submit, member)).performClick()
    }

    suspend fun doesNotSeeRemove(member: String) {
        test.onNodeWithText(getString(Res.string.remove_submit, member)).assertDoesNotExist()
    }

    suspend fun seesTheNameDoesNotMatch() = seesText(getString(Res.string.remove_confirm_mismatch))

    /** The button says the removal is under way, stays enabled, and announces who is being removed. */
    suspend fun seesTheButtonRemoving(member: String) {
        test.onNodeWithText(getString(Res.string.remove_removing))
            .assertIsEnabled()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    getString(Res.string.a11y_remove_removing, member),
                ),
            )
        test.onNodeWithTag(HEARTH_PROGRESS_BAR_TAG).assertIsDisplayed()
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
suspend fun ComposeUiTest.onRemoveMember(block: suspend RemoveMemberRobot.() -> Unit) {
    RemoveMemberRobot(this).block()
}

/** The remove screen for [member], signed in, over a hub that answers however the test says. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.removeMemberScreen(
    hub: FakeMembersHub,
    member: Member,
    onRemove: () -> Unit = {},
) {
    setContent {
        TestApp(hub.engine, sessionStorage = InMemorySessionStorage().apply { saveTokens("signed-in-token") }) {
            RemoveMemberScreen(member = member, onBack = {}, onRemove = onRemove)
        }
    }
}

/** The remove screen's content drawn for [state]. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.removeMemberContent(state: RemoveMemberUiState) {
    setContent {
        StillTheme {
            RemoveMemberContent(state = state, onNameChange = {}, onRemove = {}, onBack = {})
        }
    }
}
