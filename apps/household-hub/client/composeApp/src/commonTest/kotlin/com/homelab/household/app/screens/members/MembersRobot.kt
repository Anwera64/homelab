package com.homelab.household.app.screens.members

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.components.HEARTH_PROGRESS_BAR_TAG
import com.homelab.household.app.components.SKELETON_GROUP_TAG
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.members_invite
import com.homelab.household.app.resources.members_remove
import com.homelab.household.app.resources.members_reset_pin
import com.homelab.household.app.resources.members_title
import com.homelab.household.app.resources.members_unreachable
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.testing.TestApp
import com.homelab.household.data.datasource.local.InMemorySessionStorage
import com.homelab.household.domain.model.Member
import com.homelab.household.presentation.members.MembersUiState
import org.jetbrains.compose.resources.getString

/** Everything the members list says and does, named as the resources it reads. */
@OptIn(ExperimentalTestApi::class)
class MembersRobot(
    private val test: ComposeUiTest,
) {
    suspend fun seesTheTitle() = seesText(getString(Res.string.members_title))

    /** A housemate, by the name the hub gave them. */
    fun seesMember(name: String) = seesText(name)

    suspend fun seesInvite() = seesText(getString(Res.string.members_invite))

    suspend fun doesNotSeeInvite() {
        test.onNodeWithText(getString(Res.string.members_invite)).assertDoesNotExist()
    }

    suspend fun seesResetPin() = seesText(getString(Res.string.members_reset_pin))

    suspend fun tapsResetPin() {
        test.onNodeWithText(getString(Res.string.members_reset_pin)).performClick()
    }

    suspend fun tapsRemove() {
        test.onNodeWithText(getString(Res.string.members_remove)).performClick()
    }

    suspend fun doesNotSeeRemove() {
        test.onNodeWithText(getString(Res.string.members_remove)).assertDoesNotExist()
    }

    suspend fun seesTheHubIsUnreachable() = seesText(getString(Res.string.members_unreachable))

    fun seesBlocksWhereTheMembersWillBe() {
        test.onNodeWithTag(SKELETON_GROUP_TAG).assertIsDisplayed()
    }

    fun doesNotSeeBlocks() {
        test.onNodeWithTag(SKELETON_GROUP_TAG).assertDoesNotExist()
    }

    fun seesTheBarUnderTheHeader() {
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
suspend fun ComposeUiTest.onMembers(block: suspend MembersRobot.() -> Unit) {
    MembersRobot(this).block()
}

/** The members list, signed in, over a hub that answers however the test says. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.membersScreen(
    hub: FakeMembersHub,
    onResetPin: (Member) -> Unit = {},
    onRemove: (Member) -> Unit = {},
) {
    setContent {
        TestApp(hub.engine, sessionStorage = InMemorySessionStorage().apply { saveTokens("signed-in-token") }) {
            MembersScreen(onBack = {}, onInvite = {}, onResetPin = onResetPin, onRemove = onRemove)
        }
    }
}

/** The members list's content drawn for [state]. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.membersContent(state: MembersUiState) {
    setContent {
        StillTheme {
            MembersContent(
                state = state,
                onInvite = {},
                onResetPin = {},
                onRemove = {},
                onRetry = {},
                onBack = {},
            )
        }
    }
}
