package com.homelab.household.app.screens.profile

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.components.SKELETON_GROUP_TAG
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.calendar_apple
import com.homelab.household.app.resources.calendar_disconnect_keep
import com.homelab.household.app.resources.calendar_disconnect_submit
import com.homelab.household.app.resources.calendar_disconnect_title
import com.homelab.household.app.resources.calendar_disconnect_unreachable
import com.homelab.household.app.resources.calendar_sheet_change
import com.homelab.household.app.resources.calendar_sheet_disconnect
import com.homelab.household.app.resources.profile_agents
import com.homelab.household.app.resources.profile_calendar_connect
import com.homelab.household.app.resources.profile_calendar_options
import com.homelab.household.app.resources.profile_calendar_sign_in_again
import com.homelab.household.app.resources.profile_calendar_sign_in_needed
import com.homelab.household.app.resources.profile_delete_blocked
import com.homelab.household.app.resources.profile_members
import com.homelab.household.app.resources.profile_sign_out
import com.homelab.household.app.resources.profile_when_agents_act
import com.homelab.household.app.resources.profile_when_agents_act_automatic
import com.homelab.household.app.resources.profile_when_agents_act_none
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.testing.TestApp
import com.homelab.household.data.datasource.local.InMemorySessionStorage
import com.homelab.household.presentation.profile.ProfileUiState
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/** Everything Profile says and does, named as the resources it reads. */
@OptIn(ExperimentalTestApi::class)
class ProfileRobot(
    private val test: ComposeUiTest,
) {
    fun seesItLoading() {
        test.onNodeWithTag(SKELETON_GROUP_TAG).assertIsDisplayed()
    }

    suspend fun seesMembers() = seesText(getString(Res.string.profile_members))

    suspend fun seesSignOut() = seesText(getString(Res.string.profile_sign_out))

    /** The hub has answered who lives here, so the account's own name is drawn. */
    suspend fun seesMember(name: String) = seesText(name)

    /** The only admin cannot leave, and the row says why. */
    suspend fun seesTheOnlyAdminCannotLeave() = seesText(getString(Res.string.profile_delete_blocked))

    suspend fun doesNotSeeTheOnlyAdminCannotLeave() {
        test.onNodeWithText(getString(Res.string.profile_delete_blocked)).assertDoesNotExist()
    }

    suspend fun tapsMembers() = tapsText(getString(Res.string.profile_members))

    suspend fun tapsSignOut() = tapsText(getString(Res.string.profile_sign_out))

    suspend fun seesToConnectACalendar() = seesText(getString(Res.string.profile_calendar_connect))

    suspend fun tapsConnectACalendar() = tapsText(getString(Res.string.profile_calendar_connect))

    /** The connected calendar is named by the account the hub holds for it. */
    suspend fun seesCalendar(account: String) = seesText(account)

    suspend fun opensTheCalendarOptions() {
        test.onNodeWithContentDescription(getString(Res.string.profile_calendar_options)).performClick()
    }

    /** Opens the options of the calendar connected as [account], once the sheet has drawn. */
    suspend fun opensTheOptionsOfCalendar(account: String) {
        seesCalendar(account)
        opensTheCalendarOptions()
        seesText(getString(Res.string.calendar_sheet_disconnect))
    }

    suspend fun seesItCanBeChangedOrDisconnected() {
        seesText(getString(Res.string.calendar_sheet_change))
        seesText(getString(Res.string.calendar_sheet_disconnect))
    }

    suspend fun tapsChangeCalendar() = tapsText(getString(Res.string.calendar_sheet_change))

    suspend fun tapsDisconnectCalendar() = tapsText(getString(Res.string.calendar_sheet_disconnect))

    suspend fun seesItAsksToDisconnectAppleCalendar() {
        seesText(getString(Res.string.calendar_disconnect_title, getString(Res.string.calendar_apple)))
    }

    suspend fun tapsConfirmTheDisconnect() = tapsText(getString(Res.string.calendar_disconnect_submit))

    suspend fun tapsKeepItConnected() = tapsText(getString(Res.string.calendar_disconnect_keep))

    suspend fun seesTheDisconnectQuestionGone() {
        test.waitUntilDoesNotExist(hasText(getString(Res.string.calendar_disconnect_keep)), timeoutMillis = WAIT_MILLIS)
    }

    suspend fun seesTheHubCouldNotBeReached() = seesText(getString(Res.string.calendar_disconnect_unreachable))

    suspend fun seesTheCalendarNeedsSigningInAgain() {
        seesText(getString(Res.string.profile_calendar_sign_in_needed))
    }

    suspend fun tapsSignInAgain() = tapsText(getString(Res.string.profile_calendar_sign_in_again))

    /** One kind of change runs without asking: the agents row counts it. */
    suspend fun seesAgentsAddingWithoutAsking(count: Int) {
        seesText(getPluralString(Res.plurals.profile_when_agents_act_automatic, count, count))
    }

    suspend fun seesTheAgentsHeading() = seesText(getString(Res.string.profile_agents).uppercase())

    suspend fun seesEveryChangeAsks() = seesText(getString(Res.string.profile_when_agents_act_none))

    suspend fun tapsWhenAgentsAct() {
        test.onNodeWithText(getString(Res.string.profile_when_agents_act)).performScrollTo().performClick()
    }

    private fun seesText(text: String) {
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(text).assertIsDisplayed()
    }

    private fun tapsText(text: String) {
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(text).performClick()
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}

@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.onProfile(block: suspend ProfileRobot.() -> Unit) {
    ProfileRobot(this).block()
}

/** Profile drawn from [state] alone, with no hub under it. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.profileContent(state: ProfileUiState) {
    setContent {
        StillTheme {
            ProfileContent(
                state = state,
                onBack = {},
                onMembers = {},
                onChangePin = {},
                onLeave = {},
                onSignOut = {},
                onCalendar = {},
                onCalendarSignInAgain = {},
                onWhenAgentsAct = {},
            )
        }
    }
}

/** Profile for a member signed in on this phone, over a hub that answers however the test says. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.profileScreen(
    hub: FakeMembersHub,
    sessionStorage: InMemorySessionStorage = InMemorySessionStorage().apply { saveTokens("signed-in-token") },
    onMembers: () -> Unit = {},
    onSignedOut: () -> Unit = {},
    onCalendar: () -> Unit = {},
    onCalendarSignInAgain: () -> Unit = {},
    onWhenAgentsAct: () -> Unit = {},
) {
    setContent {
        TestApp(hub.engine, sessionStorage = sessionStorage) {
            ProfileScreen(
                onBack = {},
                onMembers = onMembers,
                onChangePin = {},
                onLeave = {},
                onSignedOut = onSignedOut,
                onCalendar = onCalendar,
                onCalendarSignInAgain = onCalendarSignInAgain,
                onWhenAgentsAct = onWhenAgentsAct,
            )
        }
    }
}
