package com.homelab.household.app.navigation

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.navigation3.runtime.NavKey
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.new_chat
import com.homelab.household.app.resources.tab_chats
import com.homelab.household.app.resources.tab_household
import com.homelab.household.app.resources.tab_my_space
import com.homelab.household.app.resources.tab_schedule
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.domain.model.CalendarProvider
import com.homelab.household.domain.model.InvitePreview
import com.homelab.household.domain.model.Member
import org.jetbrains.compose.resources.getString

/**
 * The navigation host as the person sees it: the stub screens' text (named by [StubScreens]) and the
 * real bottom bar's labels (named as the resources they read).
 */
@OptIn(ExperimentalTestApi::class)
class AppNavHostRobot(
    private val test: ComposeUiTest,
) {
    fun seesLaunch() = seesText(StubScreens.LAUNCH)

    fun seesSignIn() = seesText(StubScreens.SIGN_IN)

    fun seesFirstRun() = seesText(StubScreens.FIRST_RUN)

    fun seesHome() = seesText(StubScreens.HOME)

    fun seesSchedule() = seesText(StubScreens.SCHEDULE)

    fun seesMySpace() = seesText(StubScreens.MY_SPACE)

    fun seesChats() = seesText(StubScreens.CHATS)

    fun seesANewConversation() = seesText(StubScreens.NEW_CONVERSATION)

    fun seesTheCalendarPicker() = seesText(StubScreens.CALENDAR_PICKER)

    fun seesTheInviteCode() = seesText(StubScreens.INVITE_CODE)

    fun seesMembers() = seesText(StubScreens.MEMBERS)

    fun seesWhenAgentsAct() = seesText(StubScreens.WHEN_AGENTS_ACT)

    fun seesMemories() = seesText(StubScreens.MEMORIES)

    fun seesProfile() = seesText(StubScreens.PROFILE)

    fun seesTheInviteCreate() = seesText(StubScreens.INVITE_CREATE)

    fun seesTheGoogleSignIn() = seesText(StubScreens.GOOGLE_SIGN_IN)

    fun seesTheConversation(sessionId: String) = seesText(StubScreens.conversationOf(sessionId))

    fun seesTheJoinFor(preview: InvitePreview) = seesText(StubScreens.joinOf(preview))

    fun seesThePinPadOf(member: Member) = seesText(StubScreens.pinOf(member))

    fun seesTheForgottenPinOf(member: Member) = seesText(StubScreens.forgotOf(member))

    fun seesTheNewPinFor(code: String) = seesText(StubScreens.newPinFor(code))

    fun seesTheApprovalFor(member: Member) = seesText(StubScreens.approveOf(member))

    fun seesTheRemovalOf(member: Member) = seesText(StubScreens.removeOf(member))

    fun seesTheCalendarConnectFor(provider: CalendarProvider) = seesText(StubScreens.calendarConnectOf(provider))

    fun tapsGoToSignIn() = tapsText(StubScreens.GO_TO_SIGN_IN)

    fun tapsGoToFirstRun() = tapsText(StubScreens.GO_TO_FIRST_RUN)

    fun tapsCreatedHousehold() = tapsText(StubScreens.CREATED)

    fun tapsSignInInstead() = tapsText(StubScreens.SIGN_IN_INSTEAD)

    fun tapsPickEmma() = tapsText(StubScreens.PICK_EMMA)

    fun tapsBack() = tapsText(StubScreens.BACK)

    fun tapsSignedIn() = tapsText(StubScreens.SIGNED_IN)

    fun tapsHaveAnInvite() = tapsText(StubScreens.HAVE_AN_INVITE)

    fun tapsCodeAccepted() = tapsText(StubScreens.CODE_ACCEPTED)

    fun tapsCodeExpired() = tapsText(StubScreens.CODE_EXPIRED)

    fun tapsJoined() = tapsText(StubScreens.JOINED)

    fun tapsForgotten() = tapsText(StubScreens.FORGOTTEN)

    fun tapsHaveAResetCode() = tapsText(StubScreens.HAVE_A_RESET_CODE)

    fun tapsResetCodeTyped() = tapsText(StubScreens.RESET_CODE_TYPED)

    fun tapsGoToProfile() = tapsText(StubScreens.GO_TO_PROFILE)

    fun tapsGoToMembers() = tapsText(StubScreens.GO_TO_MEMBERS)

    fun tapsGoToWhenAgentsAct() = tapsText(StubScreens.GO_TO_WHEN_AGENTS_ACT)

    fun tapsGoToMemories() = tapsText(StubScreens.GO_TO_MEMORIES)

    fun tapsGoToInvite() = tapsText(StubScreens.GO_TO_INVITE)

    fun tapsResetLiamsPin() = tapsText(StubScreens.RESET_LIAMS_PIN)

    fun tapsRemoveLiam() = tapsText(StubScreens.REMOVE_LIAM)

    fun tapsRemoved() = tapsText(StubScreens.REMOVED)

    fun tapsGoToLeave() = tapsText(StubScreens.GO_TO_LEAVE)

    fun tapsLeft() = tapsText(StubScreens.LEFT)

    fun tapsSignedOut() = tapsText(StubScreens.SIGNED_OUT)

    fun tapsGoToChangePin() = tapsText(StubScreens.GO_TO_CHANGE_PIN)

    fun tapsPinChanged() = tapsText(StubScreens.PIN_CHANGED)

    fun tapsGoToCalendar() = tapsText(StubScreens.GO_TO_CALENDAR)

    fun tapsPickApple() = tapsText(StubScreens.PICK_APPLE)

    fun tapsPickGoogle() = tapsText(StubScreens.PICK_GOOGLE)

    fun tapsCalendarConnected() = tapsText(StubScreens.CALENDAR_CONNECTED)

    fun tapsSignInToGoogleAgain() = tapsText(StubScreens.SIGN_IN_TO_GOOGLE_AGAIN)

    fun tapsReconnectCalendar() = tapsText(StubScreens.RECONNECT_CALENDAR)

    fun tapsOpenAChat() = tapsText(StubScreens.OPEN_A_CHAT)

    suspend fun tapsTheHouseholdTab() = tapsText(getString(Res.string.tab_household))

    suspend fun tapsTheScheduleTab() = tapsText(getString(Res.string.tab_schedule))

    suspend fun tapsTheChatsTab() = tapsText(getString(Res.string.tab_chats))

    suspend fun tapsTheMySpaceTab() = tapsText(getString(Res.string.tab_my_space))

    suspend fun tapsTheRaisedPlus() {
        test.onNodeWithContentDescription(getString(Res.string.new_chat)).performClick()
    }

    private fun seesText(text: String) {
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(text).assertIsDisplayed()
    }

    private fun tapsText(text: String) {
        test.onNodeWithText(text).performClick()
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}

@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.onAppNavHost(block: suspend AppNavHostRobot.() -> Unit) {
    AppNavHostRobot(this).block()
}

/** The navigation host over stub [screens], on the test's [backStack] and [session]. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.appNavHost(
    screens: StubScreens,
    backStack: SnapshotStateList<NavKey>,
    session: StubSession,
) {
    setContent { StillTheme { AppNavHost(screens = screens, backStack = backStack, session = session) } }
}
