package com.homelab.household.app.screens.changes

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.changes_done_replaced
import com.homelab.household.app.resources.changes_failed
import com.homelab.household.app.resources.changes_keep_both
import com.homelab.household.app.resources.changes_keep_stored
import com.homelab.household.app.resources.changes_none_title
import com.homelab.household.app.resources.changes_read
import com.homelab.household.app.resources.changes_saving
import com.homelab.household.app.resources.changes_source_owner
import com.homelab.household.app.resources.changes_source_title
import com.homelab.household.app.resources.changes_stored
import com.homelab.household.app.resources.changes_title
import com.homelab.household.app.resources.changes_use_new
import com.homelab.household.app.resources.changes_waiting_for
import com.homelab.household.app.resources.changes_waiting_line
import com.homelab.household.app.resources.memories_offline_title
import com.homelab.household.app.resources.memories_try_again
import com.homelab.household.app.resources.memories_undo
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.testing.TestApp
import com.homelab.household.data.datasource.local.InMemorySessionStorage
import com.homelab.household.presentation.memories.ChangesToCheckUiState
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import kotlin.test.assertEquals

/** Everything Changes to check says and does, named as the resources it reads. */
@OptIn(ExperimentalTestApi::class)
class ChangesToCheckRobot(
    private val test: ComposeUiTest,
) {
    suspend fun seesTheTitle(count: Int) = seesText(getPluralString(Res.plurals.changes_title, count, count))

    suspend fun seesNothingLeft() = seesText(getString(Res.string.changes_none_title))

    /** The stored sentence and the one read in the chat, each in its labelled box. */
    suspend fun seesTheChange(
        stored: String,
        read: String,
    ) {
        seesInList(stored)
        seesInList(read)
        seesInList(getString(Res.string.changes_stored).uppercase(), atLeastOne = true)
        seesInList(getString(Res.string.changes_read).uppercase(), atLeastOne = true)
    }

    suspend fun seesItWasReadIn(title: String) =
        seesSubstring(getString(Res.string.changes_source_title, title))

    suspend fun seesItWasReadInTheChatOf(owner: String) =
        seesSubstring(getString(Res.string.changes_source_owner, owner))

    suspend fun seesTheThreeAnswers() {
        seesInList(getString(Res.string.changes_keep_stored))
        seesInList(getString(Res.string.changes_keep_both))
        seesInList(getString(Res.string.changes_use_new))
    }

    suspend fun seesNoAnswers() {
        assertEquals(0, test.onAllNodes(hasText(getString(Res.string.changes_use_new))).fetchSemanticsNodes().size)
    }

    suspend fun usesNew() = tapsText(getString(Res.string.changes_use_new))

    suspend fun keepsStored() = tapsText(getString(Res.string.changes_keep_stored))

    suspend fun seesItSaving() = seesText(getString(Res.string.changes_saving))

    suspend fun seesItReplaced(stored: String) = seesInList(getString(Res.string.changes_done_replaced, stored))

    suspend fun undoes() = tapsText(getString(Res.string.memories_undo))

    suspend fun seesTheAnswerFailed() = seesInList(getString(Res.string.changes_failed))

    suspend fun seesItWaitsFor(name: String) {
        seesInList(getString(Res.string.changes_waiting_for, name).uppercase())
        seesInList(getString(Res.string.changes_waiting_line, name))
    }

    suspend fun seesTheHubCannotBeReached() {
        seesText(getString(Res.string.memories_offline_title))
        seesText(getString(Res.string.memories_try_again))
    }

    fun doesNotSee(text: String) = test.waitUntilDoesNotExist(hasText(text), timeoutMillis = WAIT_MILLIS)

    private fun seesText(text: String) {
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(text).assertIsDisplayed()
    }

    private fun seesInList(
        text: String,
        atLeastOne: Boolean = false,
    ) {
        if (atLeastOne) {
            test.waitUntil(timeoutMillis = WAIT_MILLIS) {
                test.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
            }
            return
        }
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }

    private fun seesSubstring(text: String) {
        test.waitUntil(timeoutMillis = WAIT_MILLIS) {
            test.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun tapsText(text: String) {
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(text).performScrollTo().performClick()
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}

@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.onChangesToCheck(block: suspend ChangesToCheckRobot.() -> Unit) {
    ChangesToCheckRobot(this).block()
}

/** Changes to check for a signed-in member, over a hub that answers however the test says. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.changesToCheckScreen(hub: FakeMembersHub) {
    setContent {
        TestApp(hub.engine, sessionStorage = InMemorySessionStorage().apply { saveTokens("signed-in-token") }) {
            ChangesToCheckScreen(onBack = {})
        }
    }
}

/** Changes to check drawn from [state] alone. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.changesToCheckContent(state: ChangesToCheckUiState) {
    setContent {
        StillTheme {
            ChangesToCheckContent(state = state, onBack = {}, onAnswer = { _, _ -> }, onUndo = {}, onRetry = {})
        }
    }
}
