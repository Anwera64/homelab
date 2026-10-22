package com.homelab.household.app.screens.calendarchoose

import androidx.compose.ui.test.ExperimentalTestApi
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.FakeMembersHub.FakeCalendarSource
import com.homelab.household.app.testing.runScreenTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Canvas: CalPickA, CalPickFailed. Which of the account's calendars agents read (#252). */
@OptIn(ExperimentalTestApi::class)
class CalendarChooseScreenTest {
    private fun emmasAccount() =
        FakeMembersHub().apply {
            hasACalendar()
            hasCalendars(
                FakeCalendarSource("c1", "Home"),
                FakeCalendarSource("c2", "Work"),
                FakeCalendarSource("c3", "Holidays in Spain", writable = false, picked = false),
            )
        }

    @Test
    fun `GIVEN an account with calendars WHEN the list opens THEN the ones she can edit are on and the read only one is off`() =
        runScreenTest {
            calendarChooseScreen(emmasAccount())

            onCalendarChoose {
                seesTheCalendarsTheyCanEdit()
                seesItIsRead("Home")
                seesItIsRead("Work")
                seesTheReadOnlyCalendars()
                seesItIsNotRead("Holidays in Spain")
                seesHowManyAreOn(on = 2, of = 3)
            }
        }

    @Test
    fun `GIVEN work is read WHEN Emma turns it off and is done THEN the hub reads home only and the list closes`() =
        runScreenTest {
            val hub = emmasAccount()
            var done = false
            calendarChooseScreen(hub, onDone = { done = true })

            onCalendarChoose {
                turnsOver("Work")
                seesHowManyAreOn(on = 1, of = 3)
                isDone()
            }

            waitUntil(timeoutMillis = WAIT) { done }
            assertEquals(listOf(true, false, false), hub.calendars.map { it.picked })
        }

    @Test
    fun `GIVEN the account doesn't answer WHEN the list opens THEN it says so and trying again shows the calendars`() =
        runScreenTest {
            val hub = emmasAccount().apply { cannotListCalendars() }
            calendarChooseScreen(hub)

            onCalendarChoose { seesTheAccountDidNotAnswer() }
            hub.canListCalendarsAgain()
            onCalendarChoose {
                triesAgain()
                seesItIsRead("Home")
            }
        }

    @Test
    fun `GIVEN the hub can't save WHEN Emma is done THEN it says so and her switches stay`() =
        runScreenTest {
            val hub = emmasAccount().apply { cannotSaveCalendars() }
            var done = false
            calendarChooseScreen(hub, onDone = { done = true })

            onCalendarChoose {
                turnsOver("Holidays in Spain")
                isDone()
                seesItCouldNotSave()
                seesItIsRead("Holidays in Spain")
            }
            assertTrue(!done)
        }

    private companion object {
        const val WAIT = 5_000L
    }
}
