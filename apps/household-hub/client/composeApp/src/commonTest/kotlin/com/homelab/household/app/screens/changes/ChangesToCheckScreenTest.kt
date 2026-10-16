package com.homelab.household.app.screens.changes

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.presentation.memories.ChangeStatus
import com.homelab.household.presentation.memories.ChangesToCheckUiState
import kotlin.test.Test
import kotlin.test.assertNull

/** Changes to check, with the real stack under it; only the hub is faked. */
@OptIn(ExperimentalTestApi::class)
class ChangesToCheckScreenTest {
    private val wait = 5_000L

    private val stored = "Is vegetarian, but cooks fish for Liam."
    private val read = "Eats fish again since September."

    private fun hubWithAChange() = FakeMembersHub().apply { readsAChange("c1", stored = stored, read = read) }

    @Test
    fun `GIVEN a change WHEN the page opens THEN it shows Stored beside what was read, the chat, and the three answers`() {
        runScreenTest {
            changesToCheckScreen(hubWithAChange())

            onChangesToCheck {
                seesTheTitle(count = 1)
                seesTheChange(stored = stored, read = read)
                seesItWasReadIn("Dinner ideas")
                seesTheThreeAnswers()
            }
        }
    }

    @Test
    fun `GIVEN a change WHEN Use new is tapped THEN it turns into Replaced with Undo, and Undo opens it again`() {
        val hub = hubWithAChange()

        runScreenTest {
            changesToCheckScreen(hub)

            onChangesToCheck {
                usesNew()
                seesItReplaced(stored)
                seesNothingLeft()
            }
            waitUntil(timeoutMillis = wait) { hub.answerTo("c1") == "use_new" }

            onChangesToCheck {
                undoes()
                seesTheThreeAnswers()
                seesTheTitle(count = 1)
            }
            waitUntil(timeoutMillis = wait) { hub.answerTo("c1") == null }
        }
    }

    @Test
    fun `GIVEN the hub won't take answers WHEN Keep stored is tapped THEN the card stays open and says nothing changed`() {
        val hub = hubWithAChange().apply { refusesAnswers() }

        runScreenTest {
            changesToCheckScreen(hub)

            onChangesToCheck {
                keepsStored()
                seesTheAnswerFailed()
                seesTheThreeAnswers()
            }
            assertNull(hub.answerTo("c1"))
        }
    }

    @Test
    fun `GIVEN a change about Liam's memory from your chat WHEN the page opens THEN it waits for Liam, with no answers`() {
        val hub =
            FakeMembersHub().apply {
                readsAChange("c2", stored = "Works late on Thursdays.", read = "Now works late on Fridays.", mine = false)
            }

        runScreenTest {
            changesToCheckScreen(hub)

            onChangesToCheck {
                seesNothingLeft()
                seesItWaitsFor("Liam")
                seesTheChange(stored = "Works late on Thursdays.", read = "Now works late on Fridays.")
                seesNoAnswers()
            }
        }
    }

    @Test
    fun `GIVEN a change read in someone else's chat WHEN the page opens THEN it names whose chat, never its title`() {
        val hub = FakeMembersHub().apply { readsAChange("c3", stored = stored, read = read, title = null) }

        runScreenTest {
            changesToCheckScreen(hub)

            onChangesToCheck { seesItWasReadInTheChatOf("Liam") }
        }
    }

    @Test
    fun `GIVEN the hub is down WHEN the page opens THEN it says it can't reach the hub`() {
        runScreenTest {
            changesToCheckScreen(hubWithAChange().apply { isOffline() })

            onChangesToCheck { seesTheHubCannotBeReached() }
        }
    }

    @Test
    fun `GIVEN an answer saving WHEN the page draws THEN the tapped button says Saving`() {
        val state =
            ChangesToCheckUiStateProvider().values.first { state ->
                state.toCheck.any { it.status is ChangeStatus.Saving }
            }

        runComposeUiTest {
            changesToCheckContent(state)

            onChangesToCheck { seesItSaving() }
        }
    }

    @Test
    fun every_previewed_state_draws() {
        ChangesToCheckUiStateProvider().values.forEach { state ->
            runComposeUiTest {
                changesToCheckContent(state)
                waitForIdle()
            }
        }
    }
}
