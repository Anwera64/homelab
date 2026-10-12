package com.homelab.household.app.screens.leavehousehold

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
import com.homelab.household.app.resources.a11y_leave_deleting
import com.homelab.household.app.resources.leave_deleting
import com.homelab.household.app.resources.leave_pin_label
import com.homelab.household.app.resources.leave_sole_admin
import com.homelab.household.app.resources.leave_submit
import com.homelab.household.app.resources.leave_title
import com.homelab.household.app.resources.leave_wrong_pin
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.testing.TestApp
import com.homelab.household.data.datasource.local.InMemorySessionStorage
import com.homelab.household.presentation.leavehousehold.LeaveHouseholdUiState
import org.jetbrains.compose.resources.getString

/** Everything the leave-the-household screen says and does, named as the resources it reads. */
@OptIn(ExperimentalTestApi::class)
class LeaveHouseholdRobot(
    private val test: ComposeUiTest,
) {
    suspend fun seesTheTitle() = seesText(getString(Res.string.leave_title))

    suspend fun typesThePin(pin: String) {
        test.onNodeWithContentDescription(getString(Res.string.leave_pin_label)).performTextInput(pin)
    }

    suspend fun tapsLeave() {
        test.onNodeWithText(getString(Res.string.leave_submit)).performClick()
    }

    suspend fun seesTriesLeft(attemptsLeft: Int) = seesText(getString(Res.string.leave_wrong_pin, attemptsLeft))

    suspend fun seesTheOnlyAdminCannotLeave() = seesText(getString(Res.string.leave_sole_admin))

    /** The button says the deletion is under way, stays enabled, and announces what is happening. */
    suspend fun seesTheButtonDeleting() {
        test.onNodeWithText(getString(Res.string.leave_deleting))
            .assertIsEnabled()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    getString(Res.string.a11y_leave_deleting),
                ),
            )
        test.onNodeWithTag(HEARTH_PROGRESS_BAR_TAG).assertIsDisplayed()
    }

    suspend fun doesNotSeeLeave() {
        test.onNodeWithText(getString(Res.string.leave_submit)).assertDoesNotExist()
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
suspend fun ComposeUiTest.onLeaveHousehold(block: suspend LeaveHouseholdRobot.() -> Unit) {
    LeaveHouseholdRobot(this).block()
}

/** The leave screen, signed in, over a hub that answers however the test says; [onLeft] hears of a departure. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.leaveHouseholdScreen(
    hub: FakeMembersHub,
    onLeft: () -> Unit = {},
) {
    setContent {
        TestApp(hub.engine, sessionStorage = InMemorySessionStorage().apply { saveTokens("signed-in-token") }) {
            LeaveHouseholdScreen(onBack = {}, onLeft = onLeft)
        }
    }
}

/** The leave screen's content drawn for [state]. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.leaveHouseholdContent(state: LeaveHouseholdUiState) {
    setContent {
        StillTheme {
            LeaveHouseholdContent(state = state, onPinChange = {}, onLeave = {}, onBack = {})
        }
    }
}
