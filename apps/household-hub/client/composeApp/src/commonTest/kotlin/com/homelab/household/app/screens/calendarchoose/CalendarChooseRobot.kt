package com.homelab.household.app.screens.calendarchoose

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.calendar_choose_count
import com.homelab.household.app.resources.calendar_choose_done
import com.homelab.household.app.resources.calendar_choose_editable
import com.homelab.household.app.resources.calendar_choose_failed
import com.homelab.household.app.resources.calendar_choose_read_only_note
import com.homelab.household.app.resources.calendar_choose_save_failed
import com.homelab.household.app.resources.calendar_choose_try_again
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.TestApp
import com.homelab.household.data.datasource.local.InMemorySessionStorage
import org.jetbrains.compose.resources.getString

/** Everything the list of calendars says and does, named as the member would. */
@OptIn(ExperimentalTestApi::class)
class CalendarChooseRobot(
    private val test: ComposeUiTest,
) {
    private fun switchFor(calendar: String): SemanticsMatcher = hasContentDescription(calendar) and isToggleable()

    private fun waitFor(matcher: SemanticsMatcher) =
        test.waitUntilExactlyOneExists(matcher, timeoutMillis = WAIT_MILLIS)

    /** Group titles are drawn in capitals, so they are matched whatever their case. */
    suspend fun seesTheCalendarsTheyCanEdit() {
        waitFor(hasText(getString(Res.string.calendar_choose_editable), ignoreCase = true))
    }

    /** The read-only group, by the note under it that says agents never change these. */
    suspend fun seesTheReadOnlyCalendars() {
        val note = getString(Res.string.calendar_choose_read_only_note)
        waitFor(hasText(note))
        test.onNodeWithText(note).performScrollTo().assertIsDisplayed()
    }

    fun seesItIsRead(calendar: String) {
        waitFor(switchFor(calendar))
        test.onNode(switchFor(calendar)).assertIsOn()
    }

    fun seesItIsNotRead(calendar: String) {
        waitFor(switchFor(calendar))
        test.onNode(switchFor(calendar)).assertIsOff()
    }

    fun turnsOver(calendar: String) {
        waitFor(switchFor(calendar))
        test.onNode(switchFor(calendar)).performScrollTo().performClick()
    }

    suspend fun seesHowManyAreOn(
        on: Int,
        of: Int,
    ) {
        waitFor(hasText(getString(Res.string.calendar_choose_count, on, of)))
    }

    suspend fun isDone() {
        test.onNodeWithText(getString(Res.string.calendar_choose_done)).performClick()
    }

    suspend fun seesTheAccountDidNotAnswer() {
        waitFor(hasText(getString(Res.string.calendar_choose_failed)))
    }

    suspend fun triesAgain() {
        test.onNodeWithText(getString(Res.string.calendar_choose_try_again)).performClick()
    }

    suspend fun seesItCouldNotSave() {
        waitFor(hasText(getString(Res.string.calendar_choose_save_failed)))
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}

@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.onCalendarChoose(block: suspend CalendarChooseRobot.() -> Unit) {
    CalendarChooseRobot(this).block()
}

/** The list of the account's calendars, against [hub]; Done calls [onDone]. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.calendarChooseScreen(
    hub: FakeMembersHub,
    onDone: () -> Unit = {},
) {
    setContent {
        TestApp(hub.engine, sessionStorage = InMemorySessionStorage().apply { saveTokens("signed-in-token") }) {
            CalendarChooseScreen(onBack = {}, onDone = onDone)
        }
    }
}
